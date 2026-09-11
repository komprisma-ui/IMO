package com.imo.operator;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** LUNA core: conversation + observe -> reason -> act -> verify. */
public final class LunaAgent {
    public interface Callback { void status(String text); void speak(String text); boolean confirm(JSONObject plan); void finished(); }
    private final MainActivity activity; private final LunaAccessibilityService service; private final SecureKeyStore keyStore; private final Callback callback;
    private static final ConversationMemory MEMORY=new ConversationMemory(); private static final int MAX_STEPS=20;
    public LunaAgent(MainActivity activity,LunaAccessibilityService service,SecureKeyStore keyStore,Callback callback){this.activity=activity;this.service=service;this.keyStore=keyStore;this.callback=callback;}

    public void run(String command){
        try{
            if(command==null||command.trim().isEmpty()){callback.status("Ketik atau ucapkan sesuatu kepada LUNA.");return;}
            String clean=command.trim(); MEMORY.addUser(clean);
            if(service!=null && runLocalCommand(clean))return;
            String key=keyStore.load(); if(key==null||key.trim().isEmpty())throw new IllegalStateException("API key Gemini belum diatur. Tekan API KEY untuk memasukkan key Gemini.");
            GeminiClient ai=new GeminiClient(key); String last="",failure="",previousSnapshot="";
            for(int step=1;step<=MAX_STEPS;step++){
                if(shouldStop())return;
                callback.status(service==null?"🧠 LUNA sedang berpikir…":"👁 Mengamati layar • langkah "+step+"/"+MAX_STEPS);
                String image=service==null?null:capture();
                String snapshot=service==null?"":service.snapshot(); String pkg=service==null?"":service.currentPackage();
                String prompt="TUJUAN/PERTANYAAN PENGGUNA:\n"+clean+"\n\nLANGKAH "+step+"/"+MAX_STEPS+"\nPACKAGE AKTIF="+pkg+"\nTINDAKAN TERAKHIR="+last+"\nKEGAGALAN TERAKHIR="+failure+"\nLAYAR BERUBAH="+(!snapshot.equals(previousSnapshot))+"\n\nRIWAYAT DIKELOLA CLIENT.\nJika pengguna hanya bertanya/mengobrol, actions harus kosong dan jawab natural dalam bahasa Indonesia. Jika pengguna meminta tindakan Android, selesaikan tujuan satu tindakan per putaran. Jangan mengaku berhasil sebelum diverifikasi. Jangan mengarang target. Jika target belum terlihat, ubah strategi. Tindakan sensitif seperti kirim, hapus, beli, transfer, keamanan, atau perubahan permanen wajib confirm=true.";
                JSONObject plan=new JSONObject(ai.plan(prompt,snapshot,image,MEMORY.prompt())); JSONArray actions=plan.optJSONArray("actions");
                if(actions==null||actions.length()==0){String answer=plan.optString("speak","Baik.");callback.status("✓ "+answer);callback.speak(answer);MEMORY.addAssistant(answer);return;}
                if(service==null){String answer="Untuk melakukan tindakan pada Android, aktifkan Accessibility LUNA terlebih dahulu.";callback.status("⚠ "+answer);callback.speak(answer);MEMORY.addAssistant(answer);return;}
                JSONObject action=actions.getJSONObject(0);String sig=signature(action);
                if(sig.equals(last)&&snapshot.equals(previousSnapshot)&&!"WAIT".equals(action.optString("type"))){String answer="Saya berhenti karena layar tidak berubah dan tindakan yang sama tidak aman untuk diulang.";callback.status("↻ "+answer);callback.speak(answer);MEMORY.addAssistant(answer);return;}
                boolean needsConfirm=plan.optBoolean("confirm",false)||isSensitive(action);
                if(needsConfirm&&!callback.confirm(plan)){String answer="Baik, saya batalkan tindakan itu.";callback.status("Tindakan dibatalkan pengguna.");callback.speak(answer);MEMORY.addAssistant(answer);return;}
                last=sig;previousSnapshot=snapshot;failure="";String narration=plan.optString("speak","");callback.status("⚙ "+describe(action)+(narration.isEmpty()?"":"\n"+narration));
                boolean ok=execute(action);if(!ok){failure=sig;callback.status("↻ Tindakan belum berhasil. Saya membaca layar lagi dan mencoba strategi lain…");if(!pauseResponsive(500))return;continue;}
                callback.status("✓ Tindakan dikirim. Memverifikasi layar…");if(!pauseResponsive(850))return;
            }
            String answer="Saya belum bisa memastikan tugas selesai. Saya berhenti agar tidak melakukan tindakan yang salah.";callback.status("LUNA berhenti setelah batas aman.");callback.speak(answer);MEMORY.addAssistant(answer);
        }catch(Exception e){String msg=friendlyError(e);callback.status("LUNA: "+msg);callback.speak(msg);MEMORY.addAssistant(msg);}finally{callback.finished();}
    }

    private boolean isSensitive(JSONObject a){
        String t=a.optString("type","").toUpperCase(Locale.ROOT); String v=a.optString("value","").toLowerCase(Locale.ROOT);
        if(t.contains("DELETE")||t.contains("SEND")||t.contains("PURCHASE")||t.contains("TRANSFER")||t.contains("PAY"))return true;
        if(t.equals("TYPE") && (v.contains("otp")||v.contains("password")||v.contains("kata sandi")||v.contains("pin")||v.contains("kode verifikasi")))return true;
        if(t.equals("CLICK_TEXT")||t.equals("CLICK_DESC")){String s=v;return s.contains("kirim")||s.contains("hapus")||s.contains("beli")||s.contains("bayar")||s.contains("transfer")||s.contains("setuju")||s.contains("pesan sekarang");}
        return false;
    }

    private boolean runLocalCommand(String raw){
        String c=raw.trim().toLowerCase(Locale.ROOT);DeviceExecutor d=new DeviceExecutor(activity);
        try{
            if(c.equals("home")||c.equals("beranda")||c.contains("ke beranda")||c.contains("ke home")){if(service.globalHome()){localDone("Beranda dibuka.");return true;}}
            if(c.equals("back")||c.equals("kembali")||c.contains("kembali ke sebelumnya")){if(service.globalBack()){localDone("Kembali.");return true;}}
            if(c.contains("notifikasi")||c.contains("notification")){if(service.globalNotifications()){localDone("Panel notifikasi saya buka.");return true;}}
            if(c.contains("quick settings")||c.contains("pengaturan cepat")){if(service.globalQuickSettings()){localDone("Pengaturan cepat saya buka.");return true;}}
            if(c.equals("pengaturan")||c.equals("settings")||c.contains("buka pengaturan")){if(d.openSettings("")){localDone("Pengaturan saya buka.");return true;}}
            if(c.contains("mute")||c.contains("senyap")||c.contains("bisukan suara")){if(d.mute()){localDone("Suara saya matikan.");return true;}}
            if(c.matches(".*volume\\s+\\d+.*")){String digits=c.replaceAll("[^0-9]","");int level=Math.max(0,Math.min(100,Integer.parseInt(digits)));if(d.setVolume(level)){localDone("Volume diatur ke "+level+" persen.");return true;}}
            if(c.startsWith("buka ")||c.startsWith("bukakan ")){String target=c.replaceFirst("^buka(?:kan)?\\s+","").trim();if(target.startsWith("http://")||target.startsWith("https://")){if(d.openUrl(target)){localDone("Baik, saya buka alamat web itu.");return true;}}else if(d.openApp("",target)){localDone("Baik, saya buka "+target+".");return true;}}
        }catch(Exception ignored){}
        return false;
    }
    private void localDone(String answer){callback.status("✓ "+answer);callback.speak(answer);MEMORY.addAssistant(answer);}
    private String friendlyError(Exception e){String m=e.getMessage()==null?"Kendala tidak diketahui.":e.getMessage();if(m.contains("API key Gemini"))return m;if(m.contains("Permintaan Gemini"))return m;if(m.contains("Batas penggunaan Gemini"))return m;if(m.contains("Server Gemini"))return m;if(m.contains("Gemini HTTP 401")||m.contains("Gemini HTTP 403"))return "API key Gemini ditolak. Pastikan key aktif dan memiliki akses Gemini API.";if(m.contains("Gemini HTTP 429"))return "Batas penggunaan Gemini tercapai. Tunggu sebentar lalu coba lagi.";return "Terjadi kendala: "+m;}
    private String describe(JSONObject a){String t=a.optString("type");if(t.contains("CLICK")||t.contains("LONG_CLICK"))return t+" → "+a.optString("value");if("TYPE".equals(t))return "Mengetik → "+a.optString("value");if("OPEN_APP".equals(t))return "Membuka "+a.optString("label",a.optString("package"));if("SWIPE".equals(t)||"SCROLL".equals(t))return t+" "+a.optString("direction");if("SET_VOLUME".equals(t))return "Volume → "+a.optInt("level",50)+"%";return t;}
    private boolean shouldStop(){return service!=null&&service.isStopped()?stopNotice():false;}
    private boolean stopNotice(){callback.status("■ STOP — LUNA dihentikan.");return true;}
    private boolean pauseResponsive(long ms){long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){if(shouldStop())return false;try{Thread.sleep(Math.min(100,end-System.currentTimeMillis()));}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}}return true;}
    private String signature(JSONObject x){return x.optString("type")+"|"+x.optString("value")+"|"+x.optString("package")+"|"+x.optString("label")+"|"+x.optString("direction")+"|"+x.optString("x")+"|"+x.optString("y")+"|"+x.optString("level");}
    private boolean execute(JSONObject x){String type=x.optString("type");DeviceExecutor device=new DeviceExecutor(activity);if("OPEN_APP".equals(type))return device.openApp(x.optString("package"),x.optString("label"));if("CLICK_TEXT".equals(type))return service.clickText(x.optString("value"));if("CLICK_DESC".equals(type))return service.clickDescription(x.optString("value"));if("CLICK_ID".equals(type))return service.clickId(x.optString("value"));if("CLICK_POINT".equals(type))return service.clickPoint((float)x.optDouble("x",0),(float)x.optDouble("y",0));if("LONG_CLICK_TEXT".equals(type))return service.longClickText(x.optString("value"));if("LONG_CLICK_DESC".equals(type))return service.longClickDescription(x.optString("value"));if("LONG_CLICK_ID".equals(type))return service.longClickId(x.optString("value"));if("LONG_CLICK_POINT".equals(type))return service.longClickPoint((float)x.optDouble("x",0),(float)x.optDouble("y",0));if("TYPE".equals(type))return service.typeText(x.optString("value"));if("SCROLL".equals(type))return service.scroll(x.optString("direction"));if("SWIPE".equals(type))return service.swipe(x.optString("direction"),x.optInt("distance",700),x.optInt("durationMs",500));if("BACK".equals(type))return service.globalBack();if("HOME".equals(type))return service.globalHome();if("RECENTS".equals(type))return service.globalRecents();if("NOTIFICATIONS".equals(type))return service.globalNotifications();if("QUICK_SETTINGS".equals(type))return service.globalQuickSettings();if("OPEN_URL".equals(type))return device.openUrl(x.optString("value"));if("OPEN_SETTINGS".equals(type))return device.openSettings(x.optString("value"));if("SET_VOLUME".equals(type))return device.setVolume(x.optInt("level",50));if("MUTE".equals(type))return device.mute();if("WAIT".equals(type)){device.delay(Math.min(5000,Math.max(100,x.optLong("delayMs",500))));return !shouldStop();}return false;}
    private String capture() throws InterruptedException{AtomicReference<String> image=new AtomicReference<>(null);CountDownLatch latch=new CountDownLatch(1);service.captureScreen(Runnable::run,b64->{image.set(b64);latch.countDown();});latch.await(8,TimeUnit.SECONDS);return image.get();}
}