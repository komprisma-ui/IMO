package com.imo.operator;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Observe -> plan -> act -> verify -> recover loop for LUNA. */
public final class LunaAgent {
    public interface Callback { void status(String text); boolean confirm(JSONObject plan); }
    private final MainActivity activity; private final LunaAccessibilityService service; private final SecureKeyStore keyStore; private final Callback callback;
    private static final int MAX_STEPS=12;
    public LunaAgent(MainActivity activity,LunaAccessibilityService service,SecureKeyStore keyStore,Callback callback){this.activity=activity;this.service=service;this.keyStore=keyStore;this.callback=callback;}

    public void run(String command){
        try{
            OpenAIClient ai=new OpenAIClient(keyStore.load(),"gpt-5.6-luna"); Set<String> recent=new HashSet<>(); String last="";
            for(int step=1;step<=MAX_STEPS;step++){
                if(shouldStop())return;
                callback.status("👁 LUNA membaca aplikasi — langkah "+step+"/"+MAX_STEPS);
                String image=capture(), snapshot=service.snapshot();
                String prompt=command+"\n\nAGENT STEP "+step+"/"+MAX_STEPS+". Tujuan akhir harus tercapai. Amati kondisi TERKINI. Pilih tepat SATU action. Setelah action, langkah berikutnya akan memverifikasi. Tindakan terakhir="+last+". Jangan mengulang action yang sama jika tidak ada perubahan. Jika tujuan selesai, actions kosong. Sensitif (kirim/hapus/beli/transfer/keamanan/permanen) wajib confirm=true.";
                JSONObject plan=new JSONObject(ai.plan(prompt,snapshot,image)); JSONArray actions=plan.optJSONArray("actions");
                if(actions==null||actions.length()==0){callback.status("✓ "+plan.optString("speak","Tujuan selesai."));return;}
                JSONObject action=actions.getJSONObject(0); String sig=signature(action);
                if(recent.contains(sig)&&!"WAIT".equals(action.optString("type"))){callback.status("↻ LUNA menghindari pengulangan; membaca ulang layar…"); return;}
                if(plan.optBoolean("confirm",false)&&!callback.confirm(plan)){callback.status("Tindakan dibatalkan pengguna.");return;}
                recent.add(sig); last=sig; callback.status("⚙ "+describe(action));
                boolean ok=execute(action);
                if(!ok){callback.status("↻ Tindakan gagal; LUNA mencari cara lain…"); if(!pauseResponsive(350))return; continue;}
                if(!pauseResponsive(650))return;
            }
            callback.status("LUNA berhenti setelah batas aman. Tujuan belum dapat diverifikasi.");
        }catch(Exception e){callback.status("LUNA error: "+e.getMessage());}
    }

    private String describe(JSONObject a){String t=a.optString("type"); if("CLICK_TEXT".equals(t)||"CLICK_DESC".equals(t)||"CLICK_ID".equals(t)||"LONG_CLICK_TEXT".equals(t))return t+" → "+a.optString("value"); if("TYPE".equals(t))return "TYPE → "+a.optString("value"); if("OPEN_APP".equals(t))return "Membuka "+a.optString("label",a.optString("package")); if("SWIPE".equals(t)||"SCROLL".equals(t))return t+" "+a.optString("direction"); return t;}
    private boolean shouldStop(){if(service==null||service.isStopped()){callback.status("■ STOP — LUNA dihentikan.");return true;}return false;}
    private boolean pauseResponsive(long ms){long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){if(shouldStop())return false;try{Thread.sleep(Math.min(100,end-System.currentTimeMillis()));}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}}return true;}
    private String signature(JSONObject x){return x.optString("type")+"|"+x.optString("value")+"|"+x.optString("package")+"|"+x.optString("label")+"|"+x.optString("direction")+"|"+x.optString("x")+"|"+x.optString("y");}

    private boolean execute(JSONObject x){
        String type=x.optString("type"); DeviceExecutor device=new DeviceExecutor(activity);
        if("OPEN_APP".equals(type))return device.openApp(x.optString("package"),x.optString("label"));
        if("CLICK_TEXT".equals(type))return service.clickText(x.optString("value"));
        if("CLICK_DESC".equals(type))return service.clickDescription(x.optString("value"));
        if("CLICK_ID".equals(type))return service.clickId(x.optString("value"));
        if("CLICK_POINT".equals(type))return service.clickPoint((float)x.optDouble("x",0),(float)x.optDouble("y",0));
        if("LONG_CLICK_TEXT".equals(type))return service.longClickText(x.optString("value"));
        if("LONG_CLICK_POINT".equals(type))return service.longClickPoint((float)x.optDouble("x",0),(float)x.optDouble("y",0));
        if("TYPE".equals(type))return service.typeText(x.optString("value"));
        if("SCROLL".equals(type))return service.scroll(x.optString("direction"));
        if("SWIPE".equals(type))return service.swipe(x.optString("direction"),x.optInt("distance",700),x.optInt("durationMs",500));
        if("BACK".equals(type))return service.globalBack(); if("HOME".equals(type))return service.globalHome(); if("RECENTS".equals(type))return service.globalRecents();
        if("OPEN_URL".equals(type))return device.openUrl(x.optString("value"));
        if("WAIT".equals(type)){device.delay(Math.min(5000,Math.max(100,x.optLong("delayMs",500))));return !shouldStop();}
        return false;
    }
    private String capture() throws InterruptedException {AtomicReference<String> image=new AtomicReference<>(null);CountDownLatch latch=new CountDownLatch(1);service.captureScreen(Runnable::run,b64->{image.set(b64);latch.countDown();});latch.await(8,TimeUnit.SECONDS);return image.get();}
}
