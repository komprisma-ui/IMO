package com.imo.operator;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Foreground voice session with JARVIS-style autonomous agent and optional visual context. */
public final class IMOConversationService extends Service implements TextToSpeech.OnInitListener {
    public static final String ACTION_START="com.imo.operator.START_DIALOG";
    public static final String ACTION_STOP="com.imo.operator.STOP_DIALOG";
    private static final int NOTIFICATION_ID=4201; private static final String CHANNEL="imo_voice_operator";
    private volatile boolean running; private Thread worker; private TextToSpeech tts; private volatile boolean ttsReady;
    private IMOVoiceIdentity identity; private IMOConversationBrain brain; private IMOConfirmation confirmation; private IMOEngine engine; private IMOAutonomousAgent agent; private IMOVisionCamera vision;

    @Override public void onCreate(){super.onCreate();createChannel();identity=new IMOVoiceIdentity(this);brain=new IMOConversationBrain(this);confirmation=new IMOConfirmation();tts=new TextToSpeech(this,this);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopSession();return START_NOT_STICKY;}startSession();return START_NOT_STICKY;}

    private void startSession(){
        if(running)return;
        try{Notification n=buildNotification();if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE|ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);else startForeground(NOTIFICATION_ID,n);}catch(Exception e){stopSelf();return;}
        running=true;
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){vision=new IMOVisionCamera(this);vision.start();}
        speakAndWait("Mode dialog HP aktif. Saya siap mendengarkan.",6000);if(!running)return;
        worker=new Thread(this::loop,"IMO-Voice-Operator");worker.start();
    }

    private void loop(){
        while(running){
            try{
                waitSpeech(10000);if(!running)break;
                CountDownLatch done=new CountDownLatch(1);final String[] transcript={""};final String[] error={""};
                IMOVoiceCommandPipeline p=new IMOVoiceCommandPipeline(this,identity,.72f);
                boolean started=p.start(5000,new IMOVoiceCommandPipeline.Callback(){
                    public void onState(String m){}
                    public void onAccepted(short[]pcm,int rate){try{IMOLocalAsr asr=new IMOLocalAsr(IMOConversationService.this);try{transcript[0]=asr.transcribe(pcm,rate).trim();}finally{asr.release();}}catch(Exception e){error[0]=e.getMessage();}finally{done.countDown();}}
                    public void onRejected(String m){error[0]=m;done.countDown();}
                    public void onError(String m){error[0]=m;done.countDown();}
                });
                if(!started){Thread.sleep(700);continue;}
                if(!done.await(45,TimeUnit.SECONDS)){if(running)speakNatural("Pemrosesan suara terlalu lama. Saya menghentikan sesi agar mikrofon tidak macet.");stopSession();break;}
                if(!running)break;
                String text=transcript[0];if(text==null||text.trim().isEmpty()){if(error[0]!=null&&!error[0].trim().isEmpty())speakNatural(error[0]);continue;}
                handle(text);
            }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
             catch(Exception e){if(running)speakNatural("Terjadi kendala audio. Saya tetap siap mendengarkan lagi.");try{Thread.sleep(700);}catch(InterruptedException x){Thread.currentThread().interrupt();break;}}
        }
    }

    private void handle(String text){
        try{
            if(engine==null)engine=new IMOEngine(IMOAccessibilityService.instance,confirmation,this);
            String lower=text==null?"":text.trim().toLowerCase(Locale.ROOT);
            if(agent!=null&&agent.isRunning()&&(lower.equals("berhenti")||lower.equals("stop")||lower.equals("batal")||lower.equals("batalkan")||lower.contains("hentikan tugas"))){
                agent.stop();speakNatural("Baik, tugas saya hentikan.");return;
            }
            if(engine.hasPendingConfirmation()){
                CountDownLatch latch=new CountDownLatch(1);engine.execute(text,new CallbackTts(latch));latch.await(20,TimeUnit.SECONDS);return;
            }

            // Fast path: simple device commands never wait for the cloud AI.
            IMOAction fast=IMOFastIntent.parse(text);
            if(fast!=null){
                CountDownLatch latch=new CountDownLatch(1);
                engine.execute(fastToCommand(fast),new CallbackTts(latch));
                latch.await(20,TimeUnit.SECONDS);
                return;
            }

            if(brain.aiConfigured()){
                String screen=IMOAccessibilityService.instance==null?"":IMOAccessibilityService.instance.readScreen();
                if(vision!=null&&vision.hasFrame()&&isPureConversation(text)){
                    IMOVisionReasoner.Reply vr=IMOVisionReasoner.think(brain.ai(),text,screen,vision.latestFrame());
                    if(vr.execute)startAgent(text);else speakNatural(vr.text);
                }else{
                    startAgent(text);
                }
            }else{
                CountDownLatch latch=new CountDownLatch(1);engine.execute(text,new CallbackTts(latch));latch.await(25,TimeUnit.SECONDS);
            }
        }catch(Exception e){speakNatural("Saya mengalami kendala, tetapi tidak menjalankan tindakan yang tidak pasti.");}
    }

    /** Converts a fast action back into the planner's natural command vocabulary so the central executor remains authoritative. */
    private String fastToCommand(IMOAction a){
        switch(a.type){
            case BACK:return "kembali"; case HOME:return "beranda"; case RECENTS:return "aplikasi terbaru";
            case NOTIFICATIONS:return "buka notifikasi"; case QUICK_SETTINGS:return "pengaturan cepat";
            case SCROLL_UP:return "scroll atas"; case SCROLL_DOWN:return "scroll bawah";
            case VOLUME_UP:return "naikkan volume"; case VOLUME_DOWN:return "turunkan volume"; case MUTE:return "mute";
            case TORCH_ON:return "nyalakan senter"; case TORCH_OFF:return "matikan senter";
            case OPEN_APP:return "buka "+a.value;
            default:return null;
        }
    }

    /** Starts the bounded autonomous loop. Each cycle re-reads the UI and may use camera vision. */
    private void startAgent(String goal){
        if(agent==null||!agent.isRunning()){
            agent=new IMOAutonomousAgent(brain,engine,IMOAccessibilityService.instance,vision,new IMOAutonomousAgent.Callback(){
                public void onProgress(String message){}
                public void onSpeak(String message){speakNatural(message);}
                public void onFinished(boolean success,String message){if(brain!=null)brain.rememberExecution(message);speakNatural(message);}
            });
        }
        agent.start(goal);
    }

    /** Vision remains the fast path for purely visual conversation; device actions use the autonomous executor. */
    private boolean isPureConversation(String text){
        String n=text==null?"":text.toLowerCase(Locale.ROOT);
        return n.contains("apa yang kamu lihat")||n.contains("lihat kamera")||n.contains("jelaskan gambar")||n.contains("apa yang ada di depan");
    }

    private final class CallbackTts implements IMOEngine.Callback{
        private final CountDownLatch latch;CallbackTts(CountDownLatch l){latch=l;}
        public void onProgress(String m){}
        public void onConfirmationRequired(String m){speakNatural(m);latch.countDown();}
        public void onFinished(String m,boolean success){if(brain!=null)brain.rememberExecution(m);speakNatural(m);latch.countDown();}
    }
    private void speakNatural(String text){if(text==null||text.trim().isEmpty())return;speakAndWait(text,10000);}
    private void speakAndWait(String text,long max){speak(text);waitSpeech(max);}
    private void speak(String text){if(tts!=null&&ttsReady&&text!=null&&!text.trim().isEmpty())tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"imo_service");}
    private void waitSpeech(long max){long end=System.currentTimeMillis()+max;while(ttsReady&&tts!=null&&tts.isSpeaking()&&System.currentTimeMillis()<end){try{Thread.sleep(80);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}}}
    private void stopSession(){running=false;if(agent!=null)agent.stop();if(worker!=null&&worker!=Thread.currentThread())worker.interrupt();if(vision!=null){vision.close();vision=null;}if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);stopSelf();}
    @Override public void onDestroy(){running=false;if(agent!=null)agent.stop();if(worker!=null&&worker!=Thread.currentThread())worker.interrupt();if(vision!=null){vision.close();vision=null;}if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onInit(int result){if(result!=TextToSpeech.SUCCESS)return;ttsReady=true;Locale id=new Locale("id","ID");tts.setLanguage(id);VoiceHelper.applyBest(tts,id);tts.setSpeechRate(.95f);tts.setPitch(.90f);}
    private Notification buildNotification(){Intent stop=new Intent(this,IMOConversationService.class).setAction(ACTION_STOP);PendingIntent pi=PendingIntent.getService(this,42,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,CHANNEL).setSmallIcon(com.imo.operator.R.drawable.imo_icon).setContentTitle("IMO — JARVIS Mode Aktif").setContentText("IMO mendengarkan dan dapat menjalankan tugas bertahap secara mandiri").setOngoing(true).addAction(new Notification.Action.Builder(null,"HENTIKAN",pi).build()).build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"IMO Voice Operator",NotificationManager.IMPORTANCE_LOW));}}
}