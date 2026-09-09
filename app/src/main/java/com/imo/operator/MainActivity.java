package com.imo.operator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.Set;

/** IMO operator UI: voice identity, local device control, two-way AI reasoning and continuous dialogue. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_AUDIO=10, REQ_DEVICE=11;
    private TextView status,chat,voiceStatus,aiStatus; private Button mic,enrollButton,conversationButton;
    private TextToSpeech tts; private IMOEngine engine; private IMOConfirmation confirmation; private IMOMemory memory; private IMOVoiceIdentity voiceIdentity;
    private IMOConversationBrain brain; private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean voiceBusy,enrollmentBusy,conversationMode;
    private boolean pendingEnrollment; private String pendingCommand; private boolean pendingCameraPermission,pendingCallPermission;

    @Override public void onCreate(Bundle state){super.onCreate(state);buildUi();memory=new IMOMemory(this);confirmation=new IMOConfirmation();voiceIdentity=new IMOVoiceIdentity(this);brain=new IMOConversationBrain(this);tts=new TextToSpeech(this,this);refreshStatuses();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);}
    @Override protected void onResume(){super.onResume();if(confirmation==null)confirmation=new IMOConfirmation();engine=new IMOEngine(IMOAccessibilityService.instance,confirmation,this);refreshStatuses();}

    @Override public void onRequestPermissionsResult(int requestCode,String[]permissions,int[]grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_AUDIO){boolean granted=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;if(granted&&pendingEnrollment){pendingEnrollment=false;enrollVoiceInternal();}else if(!granted&&pendingEnrollment){pendingEnrollment=false;showEnrollmentPermissionFailure();}return;}if(requestCode!=REQ_DEVICE||pendingCommand==null)return;boolean cameraOk=!pendingCameraPermission||checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;boolean callOk=!pendingCallPermission||checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED;String command=pendingCommand;pendingCommand=null;pendingCameraPermission=false;pendingCallPermission=false;if(!cameraOk||!callOk){status.setText("Izin perangkat belum diberikan.");speak("Izin perangkat belum diberikan. Saya tidak akan melewati keamanan Android.");return;}executePlannedCommand(command);}

    private void buildUi(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,38,32,28);
        TextView title=new TextView(this);title.setText("IMO");title.setTextSize(34);title.setGravity(Gravity.CENTER);root.addView(title);
        TextView subtitle=new TextView(this);subtitle.setText("Intelligent Mobile Operator • JARVIS Core");subtitle.setGravity(Gravity.CENTER);root.addView(subtitle);
        status=new TextView(this);status.setText("Siap. Saya menunggu perintah.");status.setTextSize(17);status.setPadding(0,22,0,14);root.addView(status);
        chat=new TextView(this);chat.setText("IMO: Siap membantu.");chat.setTextSize(18);root.addView(chat);
        voiceStatus=new TextView(this);voiceStatus.setTextSize(15);voiceStatus.setPadding(0,12,0,5);root.addView(voiceStatus);
        aiStatus=new TextView(this);aiStatus.setTextSize(14);root.addView(aiStatus);
        mic=new Button(this);mic.setText("🎙 MULAI BICARA");mic.setOnClickListener(v->listen());root.addView(mic);
        conversationButton=new Button(this);conversationButton.setText("🗣️ MODE DIALOG: MATI");conversationButton.setOnClickListener(v->toggleConversation());root.addView(conversationButton);
        enrollButton=new Button(this);enrollButton.setText("🔐 DAFTARKAN SUARA SAYA");enrollButton.setOnClickListener(v->enrollVoice());root.addView(enrollButton);
        Button ai=new Button(this);ai.setText("🧠 KONFIGURASI AI CERDAS");ai.setOnClickListener(v->configureAi());root.addView(ai);
        Button accessibility=new Button(this);accessibility.setText("⚙ AKTIFKAN KENDALI HP");accessibility.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(accessibility);setContentView(root);}

    private void refreshStatuses(){refreshVoiceStatus();if(aiStatus!=null)aiStatus.setText(brain!=null&&brain.aiConfigured()?"🧠 AI reasoning: AKTIF — "+brain.ai().model():"🧠 AI reasoning: LOKAL / BELUM DIKONFIGURASI");}
    private void refreshVoiceStatus(){if(voiceStatus==null||voiceIdentity==null)return;voiceStatus.setText(voiceIdentity.isEnrolled()?"🔐 Voiceprint: TERDAFTAR — terenkripsi di perangkat":"🔒 Voiceprint: BELUM TERDAFTAR — voice lock belum aktif");}

    private void toggleConversation(){if(enrollmentBusy)return;conversationMode=!conversationMode;conversationButton.setText(conversationMode?"🗣️ MODE DIALOG: AKTIF":"🗣️ MODE DIALOG: MATI");if(conversationMode){speak("Mode dialog aktif. Silakan bicara, saya akan menjawab dan tetap mendengarkan.");main.postDelayed(this::listen,1500);}else speak("Mode dialog dimatikan.");}

    private void configureAi(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(30,10,30,0);
        EditText endpoint=new EditText(this);endpoint.setHint("Endpoint Responses API");endpoint.setText(brain.ai().endpoint());box.addView(endpoint);
        EditText model=new EditText(this);model.setHint("Model");model.setText(brain.ai().model());box.addView(model);
        EditText key=new EditText(this);key.setHint("API key");key.setInputType(0x00000081);box.addView(key);
        new AlertDialog.Builder(this).setTitle("AI Cerdas IMO").setMessage("Masukkan endpoint, model, dan API key. Kunci disimpan menggunakan Android Keystore.").setView(box).setPositiveButton("SIMPAN",(d,w)->{try{brain.ai().configure(endpoint.getText().toString(),model.getText().toString(),key.getText().toString());refreshStatuses();speak("AI reasoning aktif. Saya siap bekerja lebih cerdas.");}catch(Exception e){speak("Konfigurasi AI gagal. "+safe(e.getMessage()));}}).setNegativeButton("BATAL",null).setNeutralButton("HAPUS AI",(d,w)->{brain.ai().clear();refreshStatuses();speak("Konfigurasi AI dihapus. Saya kembali ke mode lokal.");}).show();}

    private void enrollVoice(){if(enrollmentBusy){status.setText("Pendaftaran suara sedang berjalan…");return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingEnrollment=true;status.setText("Meminta izin mikrofon…");requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}enrollVoiceInternal();}
    private void enrollVoiceInternal(){if(enrollmentBusy)return;enrollmentBusy=true;enrollButton.setEnabled(false);status.setText("Siap. Ikuti instruksi dengan suara alami.");speak("Baik. Mari kita daftarkan suara Anda. Setelah saya selesai berbicara, silakan ucapkan secara alami.");new Thread(()->{try{waitForTtsToFinish(8000);Thread.sleep(900);IMOSherpaSpeakerEncoder encoder=new IMOSherpaSpeakerEncoder(getApplicationContext());IMOVoiceEnrollment enrollment=new IMOVoiceEnrollment(encoder,voiceIdentity);enrollment.enroll(3,5000,new IMOVoiceEnrollment.Callback(){public void onProgress(String m){runOnUiThread(()->status.setText(m));}public void onFinished(boolean success,String m){encoder.release();runOnUiThread(()->{enrollmentBusy=false;enrollButton.setEnabled(true);status.setText(success?"Voiceprint siap ✓":"Pendaftaran suara perlu diulang");chat.setText("IMO: "+m);refreshVoiceStatus();speak(m);});}});}catch(Exception e){runOnUiThread(()->{enrollmentBusy=false;enrollButton.setEnabled(true);status.setText("Pendaftaran suara perlu diulang");chat.setText("IMO: Enrollment gagal: "+safe(e.getMessage()));speak("Pendaftaran suara perlu diulang. "+safe(e.getMessage()));});}},"IMO-Voice-Setup").start();}
    private void showEnrollmentPermissionFailure(){status.setText("Izin mikrofon belum diberikan");speak("Pendaftaran suara membutuhkan izin mikrofon.");}

    private void listen(){if(voiceBusy||enrollmentBusy)return;if(!voiceIdentity.isEnrolled()){status.setText("Voiceprint belum terdaftar.");speak("Silakan daftarkan suara Anda terlebih dahulu.");conversationMode=false;conversationButton.setText("🗣️ MODE DIALOG: MATI");return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}voiceBusy=true;mic.setText("🔐 MENDENGARKAN…");IMOVoiceCommandPipeline pipeline=new IMOVoiceCommandPipeline(this,voiceIdentity,.72f);boolean started;try{started=pipeline.start(5000,new IMOVoiceCommandPipeline.Callback(){public void onState(String m){runOnUiThread(()->status.setText(m));}public void onAccepted(short[]pcm,int rate){processSpeech(pcm,rate);}public void onRejected(String m){voiceBusy=false;runOnUiThread(()->{mic.setText("🎙 MULAI BICARA");status.setText("Akses suara ditolak.");chat.setText("IMO: "+m);speak(m);scheduleConversationRetry();});}public void onError(String m){voiceBusy=false;runOnUiThread(()->{mic.setText("🎙 MULAI BICARA");status.setText("Pipeline suara berhenti aman.");chat.setText("IMO: "+m);scheduleConversationRetry();});}});}catch(Exception e){started=false;}if(!started){voiceBusy=false;mic.setText("🎙 MULAI BICARA");status.setText("Tidak dapat memulai pipeline suara.");}}

    private void processSpeech(short[]pcm,int rate){new Thread(()->{IMOLocalAsr asr=null;try{runOnUiThread(()->status.setText("Suara cocok ✓. Memahami…"));asr=new IMOLocalAsr(MainActivity.this);String text=asr.transcribe(pcm,rate).trim();if(text.isEmpty()){runOnUiThread(()->{status.setText("Ucapan belum terbaca.");chat.setText("IMO: Saya belum menangkapnya.");speak("Saya belum menangkapnya. Silakan ulangi.");});return;}final String spoken=text;runOnUiThread(()->handleRecognized(spoken));}catch(Exception e){runOnUiThread(()->{status.setText("ASR lokal gagal.");speak("Saya belum dapat memahami ucapan itu.");});}finally{if(asr!=null)asr.release();voiceBusy=false;runOnUiThread(()->mic.setText("🎙 MULAI BICARA"));}},"IMO-ASR").start();}

    private void handleRecognized(String text){chat.setText("Anda: "+text);status.setText("Menganalisis maksud…");if(engine!=null&&engine.hasPendingConfirmation()&&(text.toLowerCase(Locale.ROOT).contains("ya")||text.toLowerCase(Locale.ROOT).contains("batal")||text.toLowerCase(Locale.ROOT).contains("tidak"))){executePlannedCommand(text);return;}if(brain.aiConfigured()){new Thread(()->{try{String screen=IMOAccessibilityService.instance==null?"":IMOAccessibilityService.instance.readScreen();IMOConversationBrain.Reply r=brain.think(text,screen);runOnUiThread(()->{chat.setText("Anda: "+text+"\nIMO: "+r.text);if(r.execute){status.setText("Menjalankan rencana AI…");executeWithRequiredPermissions(r.text);}else{status.setText("IMO siap mendengarkan.");speakNatural(r.text);scheduleConversationRetry();}});}catch(Exception e){runOnUiThread(()->{status.setText("AI tidak tersedia — mode lokal aktif.");executeLocalCommand(text);});}},"IMO-AI").start();}else executeLocalCommand(text);}

    private void executeLocalCommand(String clean){IMOJarvisCore.Decision decision=IMOJarvisCore.understand(clean);String plannerInput=IMOJarvisCore.normalizeForPlanner(clean);if(decision.intent==IMOJarvisCore.Intent.NONE&&decision.confidence<.55f){chat.setText("Anda: "+clean+"\nIMO: Saya belum cukup yakin memahami maksudnya.");status.setText("Perlu klarifikasi");speakNatural("Saya belum cukup yakin dengan maksud perintah itu. Tolong jelaskan sedikit lagi.");memory.remember(clean,"Klarifikasi diperlukan");scheduleConversationRetry();return;}chat.setText("Anda: "+clean+"\nIMO: Saya pahami. Mulai bekerja…");status.setText("Menganalisis tujuan dan menyusun langkah…");executeWithRequiredPermissions(plannerInput);}

    private void executeWithRequiredPermissions(String plannerInput){String lower=plannerInput.toLowerCase(Locale.ROOT);boolean needsCamera=lower.contains("senter")||lower.contains("flashlight")||lower.contains("torch");boolean needsCall=lower.startsWith("panggil ")||lower.startsWith("telepon ")||lower.startsWith("call ");pendingCommand=plannerInput;pendingCameraPermission=needsCamera;pendingCallPermission=needsCall;if(needsCamera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){status.setText("Meminta izin kamera untuk lampu senter…");requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_DEVICE);return;}if(needsCall&&checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){status.setText("Meminta izin telepon…");requestPermissions(new String[]{Manifest.permission.CALL_PHONE},REQ_DEVICE);return;}pendingCommand=null;pendingCameraPermission=false;pendingCallPermission=false;executePlannedCommand(plannerInput);}

    private void executePlannedCommand(String plannerInput){engine=new IMOEngine(IMOAccessibilityService.instance,confirmation,this);engine.execute(plannerInput,new IMOEngine.Callback(){public void onProgress(String m){runOnUiThread(()->status.setText(m));}public void onConfirmationRequired(String m){memory.remember(plannerInput,m);runOnUiThread(()->{status.setText("Menunggu konfirmasi");chat.setText("IMO: "+m);speakNatural(m);});}public void onFinished(String m,boolean success){memory.remember(plannerInput,m);if(brain!=null)brain.rememberExecution(m);runOnUiThread(()->{status.setText(success?"Selesai ✓":"Belum selesai");chat.setText("IMO: "+m);speakNatural(m);scheduleConversationRetry();});}});}

    private void scheduleConversationRetry(){if(!conversationMode)return;main.removeCallbacksAndMessages(null);main.postDelayed(this::listen,1800);}
    private void speakNatural(String text){if(text==null||text.trim().isEmpty())return;speak(text);}
    private void speak(String text){if(tts!=null&&text!=null)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"imo");}
    private void waitForTtsToFinish(long maxMs)throws InterruptedException{long end=System.currentTimeMillis()+maxMs;while(tts!=null&&tts.isSpeaking()&&System.currentTimeMillis()<end)Thread.sleep(100);}
    @Override public void onInit(int result){if(result!=TextToSpeech.SUCCESS)return;Locale id=new Locale("id","ID");int language=tts.setLanguage(id);Voice best=findBestIndonesianVoice(tts.getVoices(),id);if(best!=null)tts.setVoice(best);tts.setSpeechRate(.95f);tts.setPitch(.90f);if(language==TextToSpeech.LANG_MISSING_DATA||language==TextToSpeech.LANG_NOT_SUPPORTED)return;}
    private static Voice findBestIndonesianVoice(Set<Voice>voices,Locale locale){if(voices==null)return null;Voice best=null;int bestScore=Integer.MIN_VALUE;for(Voice v:voices){if(v==null||v.getLocale()==null||!v.getLocale().getLanguage().equals(locale.getLanguage()))continue;String n=v.getName()==null?"":v.getName().toLowerCase(Locale.US);int s=v.getQuality()*10;if(v.getQuality()>=Voice.QUALITY_VERY_HIGH)s+=100;if(v.getLatency()<=Voice.LATENCY_NORMAL)s+=20;if(n.contains("male")||n.contains("man")||n.contains("pria")||n.contains("laki")||n.contains("#m"))s+=250;if(n.contains("female")||n.contains("woman")||n.contains("girl")||n.contains("wanita")||n.contains("perempuan")||n.contains("#f"))s-=250;if(v.isNetworkConnectionRequired())s+=15;if(s>bestScore){bestScore=s;best=v;}}return best;}
    @Override protected void onDestroy(){conversationMode=false;main.removeCallbacksAndMessages(null);if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
    private static String safe(String s){return s==null||s.trim().isEmpty()?"kesalahan tidak diketahui":s;}
}
