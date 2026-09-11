package com.imo.operator;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Observe -> reason -> act -> verify -> recover loop for LUNA. */
public final class LunaAgent {
    public interface Callback {
        void status(String text);
        void speak(String text);
        boolean confirm(JSONObject plan);
        void finished();
    }
    private final MainActivity activity;
    private final LunaAccessibilityService service;
    private final SecureKeyStore keyStore;
    private final Callback callback;
    private static final int MAX_STEPS = 20;

    public LunaAgent(MainActivity activity, LunaAccessibilityService service, SecureKeyStore keyStore, Callback callback) {
        this.activity=activity; this.service=service; this.keyStore=keyStore; this.callback=callback;
    }

    public void run(String command) {
        try {
            String key=keyStore.load();
            if(key==null||key.trim().isEmpty()) throw new IllegalStateException("API key belum diatur. Tekan API KEY untuk memasukkannya.");
            OpenAIClient ai=new OpenAIClient(key,"gpt-5.6-sol");
            String last="", failure="", previousSnapshot="";
            for(int step=1; step<=MAX_STEPS; step++) {
                if(shouldStop()) return;
                callback.status("👁 Mengamati layar • langkah "+step+"/"+MAX_STEPS);
                String image=capture(), snapshot=service.snapshot(), pkg=service.currentPackage();
                String prompt="TUJUAN PENGGUNA:\n"+command+
                        "\n\nLANGKAH "+step+"/"+MAX_STEPS+
                        "\nPACKAGE AKTIF="+pkg+
                        "\nTINDAKAN TERAKHIR="+last+
                        "\nKEGAGALAN TERAKHIR="+failure+
                        "\nLAYAR BERUBAH SEJAK LANGKAH SEBELUMNYA="+(!snapshot.equals(previousSnapshot))+
                        "\n\nSelesaikan tujuan akhir, jangan sekadar menjawab. Periksa layar terkini setelah setiap tindakan. Gunakan ID bila tersedia, lalu text/description, lalu koordinat screenshot yang benar-benar terlihat. Jangan mengarang target. Jika target belum terlihat, scroll/swipe atau ubah strategi. Jangan mengulang tindakan yang sama jika layar tidak berubah. Jika tujuan sudah tercapai, actions kosong dan speak menjelaskan hasil dalam bahasa Indonesia. Tindakan sensitif seperti kirim, hapus, beli, transfer, keamanan, atau perubahan permanen wajib confirm=true.";
                JSONObject plan=new JSONObject(ai.plan(prompt,snapshot,image));
                JSONArray actions=plan.optJSONArray("actions");
                if(actions==null || actions.length()==0) {
                    String answer=plan.optString("speak","Tujuan selesai.");
                    callback.status("✓ "+answer); callback.speak(answer); return;
                }
                JSONObject action=actions.getJSONObject(0); String sig=signature(action);
                if(sig.equals(last) && snapshot.equals(previousSnapshot) && !"WAIT".equals(action.optString("type"))) {
                    callback.status("↻ Layar tidak berubah setelah tindakan yang sama. Saya hentikan agar tidak terjadi klik berulang.");
                    callback.speak("Saya berhenti karena layar tidak berubah dan tindakan yang sama tidak aman untuk diulang.");
                    return;
                }
                if(plan.optBoolean("confirm",false) && !callback.confirm(plan)) {
                    callback.status("Tindakan dibatalkan pengguna."); callback.speak("Baik, saya batalkan tindakan itu."); return;
                }
                last=sig; previousSnapshot=snapshot; failure="";
                String narration=plan.optString("speak","");
                callback.status("⚙ "+describe(action)+(narration.isEmpty()?"":"\n"+narration));
                boolean ok=execute(action);
                if(!ok) {
                    failure=sig; callback.status("↻ Tindakan belum berhasil. Saya akan membaca layar lagi dan mencoba strategi lain…");
                    if(!pauseResponsive(500)) return;
                    continue;
                }
                callback.status("✓ Tindakan dikirim. Memverifikasi layar…");
                if(!pauseResponsive(850)) return;
            }
            callback.status("LUNA berhenti setelah batas aman. Tujuan belum dapat diverifikasi.");
            callback.speak("Saya belum bisa memastikan tugas selesai. Saya berhenti agar tidak melakukan tindakan yang salah.");
        } catch(Exception e) {
            callback.status("LUNA error: "+friendlyError(e));
            callback.speak(friendlyError(e));
        } finally { callback.finished(); }
    }

    private String friendlyError(Exception e){
        String m=e.getMessage()==null?"Kendala tidak diketahui.":e.getMessage();
        if(m.contains("insufficient_quota")||m.contains("credit_balance_exhausted")||m.contains("no credits")) return "Saldo OpenAI API habis. Tambahkan kredit API lalu coba lagi.";
        if(m.contains("HTTP 401")) return "API key tidak valid atau sudah tidak aktif. Periksa API KEY LUNA.";
        if(m.contains("HTTP 403")) return "Akses API ditolak. Periksa organisasi dan izin API key.";
        if(m.contains("HTTP 429")) return "Permintaan ke OpenAI sedang dibatasi. Tunggu sebentar lalu coba lagi.";
        if(m.contains("HTTP 5")) return "Server OpenAI sedang bermasalah. Coba lagi beberapa saat.";
        return "Terjadi kendala: "+m;
    }

    private String describe(JSONObject a) {
        String t=a.optString("type");
        if(t.contains("CLICK") || t.contains("LONG_CLICK")) return t+" → "+a.optString("value");
        if("TYPE".equals(t)) return "Mengetik → "+a.optString("value");
        if("OPEN_APP".equals(t)) return "Membuka "+a.optString("label",a.optString("package"));
        if("SWIPE".equals(t)||"SCROLL".equals(t)) return t+" "+a.optString("direction");
        if("SET_VOLUME".equals(t)) return "Volume → "+a.optInt("level",50)+"%";
        return t;
    }
    private boolean shouldStop(){if(service==null||service.isStopped()){callback.status("■ STOP — LUNA dihentikan.");return true;}return false;}
    private boolean pauseResponsive(long ms){long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){if(shouldStop())return false;try{Thread.sleep(Math.min(100,end-System.currentTimeMillis()));}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}}return true;}
    private String signature(JSONObject x){return x.optString("type")+"|"+x.optString("value")+"|"+x.optString("package")+"|"+x.optString("label")+"|"+x.optString("direction")+"|"+x.optString("x")+"|"+x.optString("y")+"|"+x.optString("level");}

    private boolean execute(JSONObject x){
        String type=x.optString("type"); DeviceExecutor device=new DeviceExecutor(activity);
        if("OPEN_APP".equals(type))return device.openApp(x.optString("package"),x.optString("label"));
        if("CLICK_TEXT".equals(type))return service.clickText(x.optString("value"));
        if("CLICK_DESC".equals(type))return service.clickDescription(x.optString("value"));
        if("CLICK_ID".equals(type))return service.clickId(x.optString("value"));
        if("CLICK_POINT".equals(type))return service.clickPoint((float)x.optDouble("x",0),(float)x.optDouble("y",0));
        if("LONG_CLICK_TEXT".equals(type))return service.longClickText(x.optString("value"));
        if("LONG_CLICK_DESC".equals(type))return service.longClickDescription(x.optString("value"));
        if("LONG_CLICK_ID".equals(type))return service.longClickId(x.optString("value"));
        if("LONG_CLICK_POINT".equals(type))return service.longClickPoint((float)x.optDouble("x",0),(float)x.optDouble("y",0));
        if("TYPE".equals(type))return service.typeText(x.optString("value"));
        if("SCROLL".equals(type))return service.scroll(x.optString("direction"));
        if("SWIPE".equals(type))return service.swipe(x.optString("direction"),x.optInt("distance",700),x.optInt("durationMs",500));
        if("BACK".equals(type))return service.globalBack();
        if("HOME".equals(type))return service.globalHome();
        if("RECENTS".equals(type))return service.globalRecents();
        if("NOTIFICATIONS".equals(type))return service.globalNotifications();
        if("QUICK_SETTINGS".equals(type))return service.globalQuickSettings();
        if("OPEN_URL".equals(type))return device.openUrl(x.optString("value"));
        if("OPEN_SETTINGS".equals(type))return device.openSettings(x.optString("value"));
        if("SET_VOLUME".equals(type))return device.setVolume(x.optInt("level",50));
        if("MUTE".equals(type))return device.mute();
        if("WAIT".equals(type)){device.delay(Math.min(5000,Math.max(100,x.optLong("delayMs",500))));return !shouldStop();}
        return false;
    }
    private String capture() throws InterruptedException {AtomicReference<String> image=new AtomicReference<>(null);CountDownLatch latch=new CountDownLatch(1);service.captureScreen(Runnable::run,b64->{image.set(b64);latch.countDown();});latch.await(8,TimeUnit.SECONDS);return image.get();}
}
