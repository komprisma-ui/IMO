package com.imo.operator;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import java.util.List;

/** IMO execution loop with perception, verification, recovery and explicit confirmation for risky actions. */
public final class IMOEngine {
    public interface Callback { void onProgress(String message); void onFinished(String message, boolean success); void onConfirmationRequired(String message); }
    private final Handler main=new Handler(Looper.getMainLooper()); private final IMOAccessibilityService service; private final IMOConfirmation confirmation; private final Context context; private static final int MAX_RECOVERY=3;
    public IMOEngine(IMOAccessibilityService service){this(service,new IMOConfirmation(),null);} public IMOEngine(Context context,IMOAccessibilityService service){this(service,new IMOConfirmation(),context);} public IMOEngine(IMOAccessibilityService service,IMOConfirmation confirmation){this(service,confirmation,null);} public IMOEngine(IMOAccessibilityService service,IMOConfirmation confirmation,Context context){this.service=service;this.confirmation=confirmation;this.context=context;}
    public boolean hasPendingConfirmation(){return confirmation.pending()!=null;}
    public void execute(String input,Callback callback){IMOAction confirmed=confirmation.consumeIfConfirmed(input);if(confirmed!=null){if(confirmed.type==IMOAction.Type.NONE){callback.onFinished("Baik, saya batalkan tindakan tersebut.",false);return;}run(java.util.Collections.singletonList(confirmed),0,0,callback,true);return;}List<IMOAction> actions=IMOPlanner.plan(input);if(actions.isEmpty()){callback.onFinished("Saya belum menemukan langkah yang aman untuk tujuan itu.",false);return;}run(actions,0,0,callback,false);}
    private boolean needsAccessibility(IMOAction.Type t){switch(t){case OPEN_APP:case CLICK:case TYPE:case READ:case BACK:case HOME:case RECENTS:case NOTIFICATIONS:case QUICK_SETTINGS:case POWER_DIALOG:case SPLIT_SCREEN:case SCROLL_DOWN:case SCROLL_UP:case SWIPE:case LONG_CLICK:return true;default:return false;}}
    private void run(List<IMOAction> actions,int index,int recovery,Callback callback,boolean confirmed){
        if(index>=actions.size()){callback.onFinished("Selesai. Semua langkah yang direncanakan telah dijalankan dan pemeriksaan dasar selesai.",true);return;}
        IMOAction a=actions.get(index);callback.onProgress("Menjalankan: "+a);
        if(a.type==IMOAction.Type.WAIT){main.postDelayed(()->run(actions,index+1,0,callback,confirmed),Math.max(100,a.waitMs));return;}
        if(a.sensitive&&!confirmed){confirmation.request(a);callback.onConfirmationRequired("Tindakan ini membutuhkan konfirmasi: "+a.value+". Ucapkan YA untuk melanjutkan atau BATAL untuk membatalkan.");return;}
        if(needsAccessibility(a.type)&&service==null){callback.onFinished("Kendali Accessibility IMO belum aktif. Aktifkan layanan IMO terlebih dahulu.",false);return;}
        if(context==null&&!needsAccessibility(a.type)){callback.onFinished("Konteks perangkat belum tersedia untuk fungsi sistem IMO.",false);return;}
        boolean ok=false;String result=null;
        switch(a.type){case OPEN_APP:ok=service.openApp(a.value);break;case CLICK:ok=service.clickText(a.value);break;case TYPE:ok=service.typeText(a.value);break;case READ:result=service.readScreen();ok=result!=null&&!result.contains("Tidak ada elemen UI");break;case BACK:ok=service.goBack();break;case HOME:ok=service.goHome();break;case RECENTS:ok=service.openRecents();break;case NOTIFICATIONS:ok=service.openNotifications();break;case QUICK_SETTINGS:ok=service.openQuickSettings();break;case POWER_DIALOG:ok=service.powerDialog();break;case SPLIT_SCREEN:ok=service.splitScreen();break;case SCROLL_DOWN:ok=service.scrollDown();break;case SCROLL_UP:ok=service.scrollUp();break;case SWIPE:ok=service.swipe(a.value);break;case LONG_CLICK:ok=service.longClickText(a.value);break;case OPEN_URL:ok=service.openUrl(a.value);break;case OPEN_SETTINGS_PAGE:ok=service.openSettingsPage(a.value);break;case LOCK_SCREEN:ok=IMODeviceController.lockScreen(context);break;case WAKE_SCREEN:ok=IMODeviceController.wakeScreen(context);break;case TORCH_ON:ok=IMODeviceController.setTorch(context,true);break;case TORCH_OFF:ok=IMODeviceController.setTorch(context,false);break;case OPEN_WIFI_SETTINGS:ok=IMODeviceController.openWifiSettings(context);break;case OPEN_BLUETOOTH_SETTINGS:ok=IMODeviceController.openBluetoothSettings(context);break;case OPEN_SYSTEM_SETTINGS:ok=IMODeviceController.openSystemSettings(context);break;case VOLUME_UP:ok=IMODeviceController.setVolume(context,AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE);break;case VOLUME_DOWN:ok=IMODeviceController.setVolume(context,AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER);break;case MUTE:ok=IMODeviceController.setVolume(context,AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE);break;case CALL:ok=IMODeviceController.call(context,a.value);break;default:break;}
        if(!ok){if(recovery<MAX_RECOVERY&&(a.type==IMOAction.Type.CLICK||a.type==IMOAction.Type.OPEN_APP)){callback.onProgress("Recovery cepat…");main.postDelayed(()->run(actions,index,recovery+1,callback,confirmed),200+recovery*150);return;}callback.onFinished(result!=null?result:"Langkah gagal dan saya berhenti agar tidak mengambil tindakan yang keliru.",false);return;}
        if(result!=null)callback.onProgress(result);
        long delay=(a.type==IMOAction.Type.OPEN_APP||a.type==IMOAction.Type.OPEN_URL)?500:200;
        main.postDelayed(()->verifyAndContinue(actions,index,recovery,callback,confirmed),delay);
    }
    private void verifyAndContinue(List<IMOAction> actions,int index,int recovery,Callback callback,boolean confirmed){
        if(service!=null){String observation=service.readScreen();if(observation==null||observation.trim().isEmpty()){callback.onFinished("Aksi dijalankan tetapi keadaan layar tidak dapat diverifikasi. Saya tidak akan mengklaim berhasil.",false);return;}if(actions.get(index).type==IMOAction.Type.OPEN_APP&&!service.isPackageActive(actions.get(index).value)){if(recovery<MAX_RECOVERY){callback.onProgress("Aplikasi belum terdeteksi; recovery cepat.");main.postDelayed(()->run(actions,index,recovery+1,callback,confirmed),200+recovery*150);return;}callback.onFinished("Aplikasi belum dapat diverifikasi sebagai layar aktif setelah beberapa percobaan.",false);return;}}
        run(actions,index+1,0,callback,confirmed);
    }
}
