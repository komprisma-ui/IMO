package com.imo.operator;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import java.util.List;

/** IMO execution loop with explicit confirmation, device controls and bounded recovery. */
public final class IMOEngine {
    public interface Callback { void onProgress(String message); void onFinished(String message, boolean success); void onConfirmationRequired(String message); }
    private final Handler main=new Handler(Looper.getMainLooper()); private final IMOAccessibilityService service; private final IMOConfirmation confirmation; private static final int MAX_RECOVERY=2;
    public IMOEngine(IMOAccessibilityService s){this(s,new IMOConfirmation());} public IMOEngine(IMOAccessibilityService s,IMOConfirmation c){service=s;confirmation=c;}
    public boolean hasPendingConfirmation(){return confirmation.pending()!=null;}
    public void execute(String input,Callback cb){IMOAction confirmed=confirmation.consumeIfConfirmed(input);if(confirmed!=null){if(confirmed.type==IMOAction.Type.NONE){cb.onFinished("Baik, saya batalkan tindakan tersebut.",false);return;}run(java.util.Collections.singletonList(confirmed),0,0,cb,true);return;}List<IMOAction>a=IMOPlanner.plan(input);if(a.isEmpty()){cb.onFinished("Saya belum memahami tujuan itu. Jelaskan apa yang ingin dicapai.",false);return;}run(a,0,0,cb,false);}
    private void run(List<IMOAction>a,int i,int recovery,Callback cb,boolean confirmed){if(i>=a.size()){cb.onFinished("Selesai. Semua langkah yang direncanakan telah dijalankan.",true);return;}IMOAction x=a.get(i);cb.onProgress("Menjalankan: "+x);if(x.type==IMOAction.Type.WAIT){main.postDelayed(()->run(a,i+1,0,cb,confirmed),Math.max(100,x.waitMs));return;}
        if(x.sensitive&&!confirmed){confirmation.request(x);cb.onConfirmationRequired("Tindakan ini membutuhkan konfirmasi: "+x.value+". Ucapkan YA untuk melanjutkan atau BATAL untuk membatalkan.");return;}
        boolean ok=false;String result=null;
        if(x.type==IMOAction.Type.LOCK_SCREEN) ok=IMODeviceController.lockScreen(service);
        else if(x.type==IMOAction.Type.WAKE_SCREEN) ok=IMODeviceController.wakeScreen(service);
        else if(x.type==IMOAction.Type.TORCH_ON) ok=IMODeviceController.setTorch(service,true);
        else if(x.type==IMOAction.Type.TORCH_OFF) ok=IMODeviceController.setTorch(service,false);
        else if(x.type==IMOAction.Type.OPEN_WIFI_SETTINGS) ok=IMODeviceController.openWifiSettings(service);
        else if(x.type==IMOAction.Type.OPEN_BLUETOOTH_SETTINGS) ok=IMODeviceController.openBluetoothSettings(service);
        else if(x.type==IMOAction.Type.OPEN_SYSTEM_SETTINGS) ok=IMODeviceController.openSystemSettings(service);
        else if(x.type==IMOAction.Type.VOLUME_UP) ok=IMODeviceController.setVolume(service,AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE);
        else if(x.type==IMOAction.Type.VOLUME_DOWN) ok=IMODeviceController.setVolume(service,AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER);
        else if(x.type==IMOAction.Type.MUTE) ok=IMODeviceController.setVolume(service,AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE);
        else if(x.type==IMOAction.Type.CALL) ok=IMODeviceController.call(service,x.value);
        else if(service!=null){switch(x.type){case OPEN_APP:ok=service.openApp(x.value);break;case CLICK:ok=service.clickText(x.value);break;case TYPE:ok=service.typeText(x.value);break;case READ:result=service.readScreen();ok=result!=null&&!result.contains("Tidak ada elemen UI");break;case BACK:ok=service.goBack();break;case HOME:ok=service.goHome();break;case SCROLL_DOWN:ok=service.scrollDown();break;case SCROLL_UP:ok=service.scrollUp();break;case LONG_CLICK:ok=service.longClickText(x.value);break;default:break;}}
        if(!ok){if(recovery<MAX_RECOVERY&&x.type==IMOAction.Type.CLICK){cb.onProgress("Aksi belum cocok. Saya mencoba pencarian yang lebih toleran…");main.postDelayed(()->run(a,i,recovery+1,cb,confirmed),300);return;}cb.onFinished(result!=null?result:"Langkah gagal. Saya berhenti agar tidak mengambil tindakan yang keliru.",false);return;}
        if(result!=null)cb.onProgress(result);main.postDelayed(()->verifyAndContinue(a,i,cb,confirmed),x.type==IMOAction.Type.OPEN_APP?1000:400);
    }
    private void verifyAndContinue(List<IMOAction>a,int i,Callback cb,boolean confirmed){if(service!=null){String o=service.readScreen();if(o!=null&&!o.isEmpty())cb.onProgress("Verifikasi layar selesai.");}run(a,i+1,0,cb,confirmed);}
}
