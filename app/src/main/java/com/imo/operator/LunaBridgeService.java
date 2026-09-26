package com.imo.operator;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import okhttp3.*;
import org.json.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Authenticated bidirectional bridge. The remote side sends one structured command;
 * IMO executes it and returns observation + result. No unauthenticated control is accepted.
 */
public final class LunaBridgeService extends Service {
    private static final String CHANNEL="imo_bridge";
    private OkHttpClient http; private WebSocket socket; private volatile boolean running=false;
    private LunaAccessibilityService access; private BridgeStore store;

    @Override public void onCreate(){
        super.onCreate(); store=new BridgeStore(this);
        createChannel();
        startForeground(8201,notification("IMO bridge siap"));
        http=new OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
        access=LunaAccessibilityService.get();
    }

    @Override public int onStartCommand(Intent intent,int flags,int id){
        connect(); return START_STICKY;
    }

    private void connect(){
        if(running||store.url().trim().isEmpty())return;
        String u=store.url().trim(); if(!u.startsWith("wss://")&&!u.startsWith("ws://"))return;
        Request r=new Request.Builder().url(u).header("Authorization","Bearer "+store.token()).build();
        running=true;
        socket=http.newWebSocket(r,new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){socket=ws;notifyStatus("Terhubung ke bridge");sendHello();}
            @Override public void onMessage(WebSocket ws,String text){handle(text);}
            @Override public void onClosing(WebSocket ws,int code,String reason){running=false;ws.close(1000,null);scheduleReconnect();}
            @Override public void onFailure(WebSocket ws,Throwable t,Response response){running=false;notifyStatus("Bridge terputus");scheduleReconnect();}
        });
    }

    private void sendHello(){
        JSONObject x=new JSONObject();try{x.put("type","hello");x.put("device","android");x.put("app","IMO");x.put("version","2.2.0");x.put("accessibility",LunaAccessibilityService.get()!=null);send(x);}catch(Exception ignored){}
    }

    private void handle(String raw){
        try{
            JSONObject cmd=new JSONObject(raw);String type=cmd.optString("type","").toUpperCase(Locale.ROOT);
            if(!"action".equalsIgnoreCase(type)&&!"observe".equalsIgnoreCase(type)&&!"ping".equalsIgnoreCase(type))return;
            if("ping".equalsIgnoreCase(type)){JSONObject p=new JSONObject();p.put("type","pong");send(p);return;}
            access=LunaAccessibilityService.get();
            if(access==null){reply(cmd.optString("id",""),false,"Accessibility belum aktif",null);return;}
            if("observe".equalsIgnoreCase(type)){reply(cmd.optString("id",""),true,"observation",observation(cmd.optBoolean("screenshot",false)));return;}
            JSONObject a=cmd.optJSONObject("action");if(a==null){reply(cmd.optString("id",""),false,"action kosong",null);return;}
            if(isSensitive(a)&&!cmd.optBoolean("confirmed",false)){reply(cmd.optString("id",""),false,"confirmation_required",observation());return;}
            boolean ok=execute(a);SystemClock.sleep(350);
            reply(cmd.optString("id",""),ok,ok?"executed":"execution_failed",observation(cmd.optBoolean("screenshot",false)));
        }catch(Exception e){JSONObject x=new JSONObject();try{x.put("type","error");x.put("error",e.getMessage()==null?"invalid_command":e.getMessage());send(x);}catch(Exception ignored){}}
    }

    private boolean execute(JSONObject a){
        String t=a.optString("type","").toUpperCase(Locale.ROOT);DeviceExecutor d=new DeviceExecutor(this);
        if(t.equals("OPEN_APP"))return d.openApp(a.optString("package"),a.optString("label"));
        if(t.equals("CLICK_TEXT"))return access.clickText(a.optString("value"));
        if(t.equals("CLICK_DESC"))return access.clickDescription(a.optString("value"));
        if(t.equals("CLICK_ID"))return access.clickId(a.optString("value"));
        if(t.equals("CLICK_POINT"))return access.clickPoint((float)a.optDouble("x"),(float)a.optDouble("y"));
        if(t.equals("LONG_CLICK_TEXT"))return access.longClickText(a.optString("value"));
        if(t.equals("LONG_CLICK_DESC"))return access.longClickDescription(a.optString("value"));
        if(t.equals("LONG_CLICK_ID"))return access.longClickId(a.optString("value"));
        if(t.equals("LONG_CLICK_POINT"))return access.longClickPoint((float)a.optDouble("x"),(float)a.optDouble("y"));
        if(t.equals("TYPE"))return access.typeText(a.optString("value"));
        if(t.equals("CLEAR_TEXT"))return access.clearFocusedText();
        if(t.equals("PRESS_ENTER"))return access.pressEnter();
        if(t.equals("SCROLL"))return access.scroll(a.optString("direction"));
        if(t.equals("SWIPE"))return access.swipe(a.optString("direction"),a.optInt("distance",700),a.optInt("durationMs",500));
        if(t.equals("BACK"))return access.globalBack();if(t.equals("HOME"))return access.globalHome();if(t.equals("RECENTS"))return access.globalRecents();
        if(t.equals("NOTIFICATIONS"))return access.globalNotifications();if(t.equals("QUICK_SETTINGS"))return access.globalQuickSettings();
        if(t.equals("POWER_DIALOG"))return access.globalPowerDialog();if(t.equals("LOCK_SCREEN"))return access.globalLockScreen();if(t.equals("SPLIT_SCREEN"))return access.globalSplitScreen();
        if(t.equals("OPEN_URL"))return d.openUrl(a.optString("value"));if(t.equals("OPEN_SETTINGS"))return d.openSettings(a.optString("value"));
        if(t.equals("SET_VOLUME"))return d.setVolume(a.optInt("level",50));if(t.equals("MUTE"))return d.mute();
        if(t.equals("WAIT")){d.delay(Math.min(5000,Math.max(100,a.optLong("delayMs",500))));return true;}
        return false;
    }

    private boolean isSensitive(JSONObject a){
        String t=a.optString("type","").toLowerCase(Locale.ROOT),v=(a.optString("value","")+" "+a.optString("label","")).toLowerCase(Locale.ROOT);
        return t.contains("send")||t.contains("delete")||t.contains("purchase")||t.contains("transfer")||t.contains("pay")||
          v.matches(".*\\b(kirim|hapus|beli|bayar|transfer|setuju|otp|pin|password|kode verifikasi)\\b.*");
    }

    private JSONObject observation(boolean includeScreenshot){
        JSONObject o=new JSONObject();try{
            o.put("package",access.currentPackage());o.put("tree",access.snapshot());o.put("nodes",access.snapshotNodes());o.put("accessibility",true);o.put("bridge_protocol",1);
            o.put("timestamp",System.currentTimeMillis());
            if(includeScreenshot&&Build.VERSION.SDK_INT>=30){
                final java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
                final String[] shot=new String[1];
                access.captureScreen(Runnable::run,b->{shot[0]=b;latch.countDown();});
                latch.await(8,java.util.concurrent.TimeUnit.SECONDS);
                if(shot[0]!=null)o.put("screenshot_jpeg_base64",shot[0]);
            }
        }catch(Exception ignored){}return o;
    }

    private void reply(String id,boolean ok,String message,JSONObject obs){
        JSONObject r=new JSONObject();try{r.put("type","result");r.put("id",id);r.put("ok",ok);r.put("message",message);if(obs!=null)r.put("observation",obs);send(r);}catch(Exception ignored){}
    }
    private void send(JSONObject x){if(socket!=null)socket.send(x.toString());}
    private void scheduleReconnect(){new Handler(Looper.getMainLooper()).postDelayed(this::connect,3000);}
    private void notifyStatus(String s){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(8201,notification(s));}
    private Notification notification(String s){return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("IMO • Luna Bridge").setContentText(s).setOngoing(true).build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"IMO Bridge",NotificationManager.IMPORTANCE_LOW));}
    @Override public void onDestroy(){running=false;if(socket!=null)socket.close(1000,"stop");if(http!=null)http.dispatcher().executorService().shutdown();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
