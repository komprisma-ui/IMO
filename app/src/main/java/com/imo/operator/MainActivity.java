package com.imo.operator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** IMO operator UI: unrestricted voice commands plus optional front-camera visual context. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_AUDIO=10,REQ_DEVICE=11,REQ_NOTIFY=12,REQ_VISION=13; private TextView status,chat,voiceStatus,aiStatus; private IMOHologramView hologram; private Button mic,enrollButton,conversationButton; private TextToSpeech tts; private IMOEngine engine; private IMOConfirmation confirmation; private IMOMemory memory; private IMOVoiceIdentity voiceIdentity; private IMOConversationBrain brain; private volatile boolean voiceBusy,enrollmentBusy,conversationMode; private boolean pendingEnrollment; private String pendingCommand; private boolean pendingCameraPermission,pendingCallPermission;
    @Override public void onCreate(Bundle state){super.onCreate(state);buildUi();memory=new IMOMemory(this);confirmation=new IMOConfirmation();voiceIdentity=new IMOVoiceIdentity(this);brain=new IMOConversationBrain(this);tts=new TextToSpeech(this,this);refreshStatuses();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);}
    @Override protected void onResume(){super.onResume();if(confirmation==null)confirmation=new IMOConfirmation();engine=new IMOEngine(IMOAccessibilityService.instance,confirmation,this);refreshStatuses();}
    @Override public void onRequestPermissionsResult(int code,String[]p,int[]g){super.onRequestPermissionsResult(code,p,g);if(code==REQ_AUDIO){boolean ok=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;if(ok&&pendingEnrollment){pendingEnrollment=false;enrollVoiceInternal();}else if(!ok&&pendingEnrollment){pendingEnrollment=false;showEnrollmentPermissionFailure();}return;}if(code==REQ_NOTIFY){if(conversationMode)startConversationService();return;}if(code==REQ_VISION){if(conversationMode)startConversationService();return;}if(code!=REQ_DEVICE||pendingCommand==null)return;boolean cam=!pendingCameraPermission||checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;boolean call=!pendingCallPermission||checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED;String cmd=pendingCommand;pendingCommand=null;pendingCameraPermission=false;pendingCallPermission=false;if(!cam||!call){status.setText("Izin perangkat belum diberikan.");speak("Izin perangkat belum diberikan. Saya tidak akan melewati keamanan Android.");return;}executePlannedCommand(cmd);}
    private void buildUi(){
        getWindow().setStatusBarColor(Color.rgb(1,4,12));
        getWindow().setNavigationBarColor(Color.rgb(1,4,12));

        android.widget.FrameLayout root=new android.widget.FrameLayout(this);
        root.setBackgroundColor(Color.rgb(1,5,16));

        // The hologram renderer contains the verified master image internally.
        // Do not depend on the previously corrupted external base64 asset.
        hologram=new IMOHologramView(this);
        hologram.setVisibility(android.view.View.VISIBLE);
        android.widget.FrameLayout.LayoutParams hp=new android.widget.FrameLayout.LayoutParams(-1,0);
        hp.gravity=android.view.Gravity.TOP;
        hp.height=(int)(getResources().getDisplayMetrics().heightPixels*0.69f);
        hp.topMargin=(int)(getResources().getDisplayMetrics().heightPixels*0.075f);
        root.addView(hologram,hp);

        LinearLayout overlay=new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.setPadding(16,10,16,8);
        root.addView(overlay,new android.widget.FrameLayout.LayoutParams(-1,-1));

        TextView title=new TextView(this);
        title.setText("IMO");
        title.setTextColor(Color.rgb(170,245,255));
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,72);
        tp.gravity=Gravity.CENTER_HORIZONTAL;
        overlay.addView(title,tp);

        Button menu=new Button(this);
        menu.setText("⋮");
        menu.setTextColor(Color.rgb(170,245,255));
        menu.setTextSize(27);
        menu.setPadding(0,0,0,5);
        menu.setBackground(roundBg(Color.TRANSPARENT,Color.rgb(25,150,220)));
        android.widget.FrameLayout.LayoutParams mp=new android.widget.FrameLayout.LayoutParams(64,64,Gravity.TOP|Gravity.RIGHT);
        mp.topMargin=8; mp.rightMargin=10;
        root.addView(menu,mp);
        menu.setOnClickListener(v->showMainMenu());

        Space spacer=new Space(this);
        overlay.addView(spacer,new LinearLayout.LayoutParams(1,0,1f));

        status=hiddenText(); chat=hiddenText(); voiceStatus=hiddenText(); aiStatus=hiddenText();
        mic=new Button(this); conversationButton=new Button(this); enrollButton=new Button(this);

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView online=new TextView(this);
        online.setText("●  IMO • ONLINE");
        online.setTextColor(Color.rgb(150,245,255));
        online.setTextSize(21);
        online.setGravity(Gravity.CENTER);
        online.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        online.setBackground(roundBg(Color.rgb(5,30,55),Color.rgb(30,185,235)));
        info.addView(online,new LinearLayout.LayoutParams(235,52));

        TextView sub=new TextView(this);
        sub.setText("N E U R A L   H O L O G R A P H I C   I N T E R F A C E");
        sub.setTextColor(Color.rgb(110,180,220));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,38);
        sp.topMargin=4;
        info.addView(sub,sp);

        TextView ready=new TextView(this);
        ready.setText("Siap. Saya menunggu perintah.");
        ready.setTextColor(Color.WHITE);
        ready.setTextSize(19);
        ready.setGravity(Gravity.CENTER);
        info.addView(ready,new LinearLayout.LayoutParams(-1,42));

        styleButton(mic,"🎙   BICARA DENGAN IMO");
        styleButton(conversationButton,"🗣   MODE DIALOG");
        Button quick=new Button(this);
        styleButton(quick,"⚡   PERINTAH CEPAT");
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,58);
        bp.setMargins(0,4,0,0);
        info.addView(mic,bp);
        info.addView(conversationButton,new LinearLayout.LayoutParams(-1,58));
        info.addView(quick,new LinearLayout.LayoutParams(-1,58));
        mic.setOnClickListener(v->listen());
        conversationButton.setOnClickListener(v->toggleConversation());
        quick.setOnClickListener(v->listen());

        overlay.addView(info,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }
    private void addHit(android.widget.FrameLayout root,android.view.View hit,float x,float y,float w,float h,android.view.View.OnClickListener click){
        hit.setBackgroundColor(Color.TRANSPARENT);
        hit.setOnClickListener(click);
        root.addView(hit,new android.widget.FrameLayout.LayoutParams(1,1));
        root.post(()->{
            int rw=root.getWidth(), rh=root.getHeight();
            android.widget.FrameLayout.LayoutParams lp=(android.widget.FrameLayout.LayoutParams)hit.getLayoutParams();
            lp.width=Math.max(1,(int)(rw*w));
            lp.height=Math.max(1,(int)(rh*h));
            lp.leftMargin=(int)(rw*x);
            lp.topMargin=(int)(rh*y);
            hit.setLayoutParams(lp);
        });
    }
    private TextView hiddenText(){TextView t=new TextView(this);t.setVisibility(android.view.View.GONE);return t;}
    private android.graphics.Bitmap loadExactMaster(){
        try{
            StringBuilder b=new StringBuilder();
            int[] ids={R.raw.imo_master_a,R.raw.imo_master_b};
            for(int id:ids){
                java.io.InputStream in=getResources().openRawResource(id);
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                byte[] buf=new byte[4096]; int n;
                while((n=in.read(buf))>0)out.write(buf,0,n);
                in.close();
                b.append(new String(out.toByteArray(),java.nio.charset.StandardCharsets.US_ASCII));
            }
            byte[] image=android.util.Base64.decode(b.toString(),android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(image,0,image.length);
            if(bitmap==null)throw new IllegalStateException("master bitmap decode failed");
            return bitmap;
        }catch(Exception e){
            return android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);
        }
    }
    private GradientDrawable roundBg(int fill,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(28);g.setStroke(2,strokeColor);return g;}
    private void styleButton(Button b,String label){b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setPadding(12,4,12,4);b.setBackground(roundBg(Color.rgb(12,25,48),Color.rgb(45,150,205)));}
    private void showMainMenu(){
        String[] items={"🧠  AI CERDAS","⚙  KENDALI HP / ACCESSIBILITY","🗣  MODE DIALOG","🔐  DAFTARKAN SUARA","🧠  HAPUS MEMORI PERCAKAPAN"};
        new AlertDialog.Builder(this).setTitle("IMO • CONTROL CENTER").setItems(items,(d,which)->{
            if(which==0)configureAi();else if(which==1)startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));else if(which==2)toggleConversation();else if(which==3)enrollVoice();else if(which==4){memory.clear();brain.clear();chat.setText("IMO: Memori percakapan dibersihkan.");speak("Memori percakapan sudah dibersihkan.");}
        }).setNegativeButton("TUTUP",null).show();
    }
    private void refreshStatuses(){refreshVoiceStatus();if(aiStatus!=null)aiStatus.setText(brain!=null&&brain.aiConfigured()?"🧠 AI reasoning: AKTIF — "+brain.ai().model():"🧠 AI reasoning: LOKAL / BELUM DIKONFIGURASI");}
    private void refreshVoiceStatus(){if(voiceStatus==null||voiceIdentity==null)return;voiceStatus.setText(voiceIdentity.isEnrolled()?"🔐 Voiceprint: TERDAFTAR — opsional":"🔓 Voiceprint: OPSIONAL — semua suara dapat berbicara");}
    private void toggleConversation(){if(enrollmentBusy)return;conversationMode=!conversationMode;conversationButton.setText(conversationMode?"🗣️ MODE DIALOG: AKTIF":"🗣️ MODE DIALOG: MATI");if(conversationMode){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFY);return;}if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){status.setText("Izin kamera diperlukan agar IMO dapat menggunakan mata visual.");requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_VISION);return;}startConversationService();}else{stopConversationService();speak("Mode dialog dimatikan.");}}
    private void startConversationService(){try{Intent i=new Intent(this,IMOConversationService.class).setAction(IMOConversationService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Mode dialog aktif — suara + mata kamera depan aktif.");}catch(Exception e){conversationMode=false;conversationButton.setText("🗣️ MODE DIALOG: MATI");speak("Mode dialog tidak dapat dimulai. "+safe(e.getMessage()));}}
    private void stopConversationService(){try{stopService(new Intent(this,IMOConversationService.class));}catch(Exception ignored){}}
    private void configureAi(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(30,10,30,0);EditText endpoint=new EditText(this);endpoint.setHint("Endpoint Responses API");endpoint.setText(brain.ai().endpoint());box.addView(endpoint);EditText model=new EditText(this);model.setHint("Model");model.setText(brain.ai().model());box.addView(model);EditText key=new EditText(this);key.setHint("API key");key.setInputType(0x00000081);box.addView(key);new AlertDialog.Builder(this).setTitle("AI Cerdas IMO").setMessage("Masukkan endpoint, model, dan API key. Kunci disimpan menggunakan Android Keystore.").setView(box).setPositiveButton("SIMPAN",(d,w)->{try{brain.ai().configure(endpoint.getText().toString(),model.getText().toString(),key.getText().toString());refreshStatuses();speak("AI reasoning aktif. Saya siap bekerja lebih cerdas.");}catch(Exception e){speak("Konfigurasi AI gagal. "+safe(e.getMessage()));}}).setNegativeButton("BATAL",null).setNeutralButton("HAPUS AI",(d,w)->{brain.ai().clear();refreshStatuses();speak("Konfigurasi AI dihapus. Saya kembali ke mode lokal.");}).show();}
    private void enrollVoice(){if(enrollmentBusy){status.setText("Pendaftaran suara sedang berjalan…");return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingEnrollment=true;status.setText("Meminta izin mikrofon…");requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}enrollVoiceInternal();}
    private void enrollVoiceInternal(){if(enrollmentBusy)return;enrollmentBusy=true;enrollButton.setEnabled(false);status.setText("Siap. Ikuti instruksi dengan suara alami.");speak("Baik. Mari kita daftarkan suara Anda. Setelah saya selesai berbicara, silakan ucapkan secara alami.");new Thread(()->{try{waitForTtsToFinish(8000);Thread.sleep(900);IMOSherpaSpeakerEncoder encoder=new IMOSherpaSpeakerEncoder(getApplicationContext());IMOVoiceEnrollment enrollment=new IMOVoiceEnrollment(encoder,voiceIdentity);enrollment.enroll(3,5000,new IMOVoiceEnrollment.Callback(){public void onProgress(String m){runOnUiThread(()->status.setText(m));}public void onFinished(boolean success,String m){encoder.release();runOnUiThread(()->{enrollmentBusy=false;enrollButton.setEnabled(true);status.setText(success?"Voiceprint siap ✓":"Pendaftaran suara perlu diulang");chat.setText("IMO: "+m);refreshVoiceStatus();speak(m);});}});}catch(Exception e){runOnUiThread(()->{enrollmentBusy=false;enrollButton.setEnabled(true);status.setText("Pendaftaran suara perlu diulang");chat.setText("IMO: Enrollment gagal: "+safe(e.getMessage()));speak("Pendaftaran suara perlu diulang. "+safe(e.getMessage()));});}},"IMO-Voice-Setup").start();}
    private void showEnrollmentPermissionFailure(){status.setText("Izin mikrofon belum diberikan");speak("Pendaftaran suara membutuhkan izin mikrofon.");}
    private void listen(){if(hologram!=null)hologram.setListening(true);if(conversationMode){speak("Mode dialog sedang aktif. Saya sudah mendengarkan melalui layanan latar depan.");return;}if(voiceBusy||enrollmentBusy)return;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}voiceBusy=true;mic.setText("🎙 MENDENGARKAN…");IMOVoiceCommandPipeline pipeline=new IMOVoiceCommandPipeline(this,voiceIdentity,.72f);boolean started;try{started=pipeline.start(5000,new IMOVoiceCommandPipeline.Callback(){public void onState(String m){runOnUiThread(()->status.setText(m));}public void onAccepted(short[]pcm,int rate){processSpeech(pcm,rate);}public void onRejected(String m){voiceBusy=false;runOnUiThread(()->{mic.setText("🎙 MULAI BICARA");status.setText("Akses suara ditolak.");chat.setText("IMO: "+m);speak(m);});}public void onError(String m){voiceBusy=false;runOnUiThread(()->{mic.setText("🎙 MULAI BICARA");status.setText("Pipeline suara berhenti aman.");chat.setText("IMO: "+m);});}});}catch(Exception e){started=false;}if(!started){voiceBusy=false;mic.setText("🎙 MULAI BICARA");status.setText("Tidak dapat memulai pipeline suara.");}}
    private void processSpeech(short[]pcm,int rate){if(hologram!=null){hologram.setListening(false);hologram.setThinking(true);}new Thread(()->{IMOLocalAsr asr=null;try{runOnUiThread(()->status.setText("Suara diterima ✓. Memahami…"));asr=new IMOLocalAsr(MainActivity.this);String text=asr.transcribe(pcm,rate).trim();if(text.isEmpty()){runOnUiThread(()->{status.setText("Ucapan belum terbaca.");chat.setText("IMO: Saya belum menangkapnya.");speak("Saya belum menangkapnya. Silakan ulangi.");});return;}runOnUiThread(()->handleRecognized(text));}catch(Exception e){runOnUiThread(()->{status.setText("ASR lokal gagal.");speak("Saya belum dapat memahami ucapan itu.");});}finally{if(asr!=null)asr.release();voiceBusy=false;runOnUiThread(()->mic.setText("🎙 MULAI BICARA"));}},"IMO-ASR").start();}
    private void handleRecognized(String text){if(hologram!=null)hologram.setThinking(true);chat.setText("Anda: "+text);status.setText("Menganalisis maksud…");if(engine!=null&&engine.hasPendingConfirmation()&&(text.toLowerCase(Locale.ROOT).contains("ya")||text.toLowerCase(Locale.ROOT).contains("batal")||text.toLowerCase(Locale.ROOT).contains("tidak"))){executePlannedCommand(text);return;}if(brain.aiConfigured()){new Thread(()->{try{String screen=IMOAccessibilityService.instance==null?"":IMOAccessibilityService.instance.readScreen();IMOConversationBrain.Reply r=brain.think(text,screen);runOnUiThread(()->{chat.setText("Anda: "+text+"\nIMO: "+r.text);if(r.execute){status.setText("Menjalankan rencana AI…");executeWithRequiredPermissions(r.text);}else{status.setText("IMO siap mendengarkan.");speak(r.text);}});}catch(Exception e){runOnUiThread(()->{status.setText("AI tidak tersedia — mode lokal aktif.");executeLocalCommand(text);});}},"IMO-AI").start();}else executeLocalCommand(text);}
    private void executeLocalCommand(String clean){IMOJarvisCore.Decision decision=IMOJarvisCore.understand(clean);String plannerInput=IMOJarvisCore.normalizeForPlanner(clean);if(decision.intent==IMOJarvisCore.Intent.NONE&&decision.confidence<.55f){chat.setText("Anda: "+clean+"\nIMO: Saya belum cukup yakin memahami maksudnya.");status.setText("Perlu klarifikasi");speak("Saya belum cukup yakin dengan maksud perintah itu. Tolong jelaskan sedikit lagi.");memory.remember(clean,"Klarifikasi diperlukan");return;}chat.setText("Anda: "+clean+"\nIMO: Saya pahami. Mulai bekerja…");status.setText("Menganalisis tujuan dan menyusun langkah…");executeWithRequiredPermissions(plannerInput);}
    private void executeWithRequiredPermissions(String plannerInput){String lower=plannerInput.toLowerCase(Locale.ROOT);boolean needsCamera=lower.contains("senter")||lower.contains("flashlight")||lower.contains("torch");boolean needsCall=lower.startsWith("panggil ")||lower.startsWith("telepon ")||lower.startsWith("call ");pendingCommand=plannerInput;pendingCameraPermission=needsCamera;pendingCallPermission=needsCall;if(needsCamera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){status.setText("Meminta izin kamera untuk lampu senter…");requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_DEVICE);return;}if(needsCall&&checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){status.setText("Meminta izin telepon…");requestPermissions(new String[]{Manifest.permission.CALL_PHONE},REQ_DEVICE);return;}pendingCommand=null;pendingCameraPermission=false;pendingCallPermission=false;executePlannedCommand(plannerInput);}
    private void executePlannedCommand(String plannerInput){engine=new IMOEngine(IMOAccessibilityService.instance,confirmation,this);engine.execute(plannerInput,new IMOEngine.Callback(){public void onProgress(String m){runOnUiThread(()->status.setText(m));}public void onConfirmationRequired(String m){memory.remember(plannerInput,m);runOnUiThread(()->{status.setText("Menunggu konfirmasi");chat.setText("IMO: "+m);speak(m);});}public void onFinished(String m,boolean success){memory.remember(plannerInput,m);if(brain!=null)brain.rememberExecution(m);runOnUiThread(()->{status.setText(success?"Selesai ✓":"Belum selesai");chat.setText("IMO: "+m);speak(m);});}});}
    private void speak(String text){if(hologram!=null){hologram.setThinking(false);hologram.setSpeaking(true);hologram.postDelayed(()->hologram.setSpeaking(false),Math.max(1200,Math.min(6500,(text==null?10:text.length())*65)));}if(tts!=null&&text!=null)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"imo");}
    private void waitForTtsToFinish(long maxMs)throws InterruptedException{long end=System.currentTimeMillis()+maxMs;while(tts!=null&&tts.isSpeaking()&&System.currentTimeMillis()<end)Thread.sleep(100);}
    @Override public void onInit(int result){if(result!=TextToSpeech.SUCCESS)return;Locale id=new Locale("id","ID");int language=tts.setLanguage(id);VoiceHelper.applyBest(tts,id);tts.setSpeechRate(.95f);tts.setPitch(.90f);if(language==TextToSpeech.LANG_MISSING_DATA||language==TextToSpeech.LANG_NOT_SUPPORTED)return;}
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
    private static String safe(String s){return s==null||s.trim().isEmpty()?"kesalahan tidak diketahui":s;}
}
