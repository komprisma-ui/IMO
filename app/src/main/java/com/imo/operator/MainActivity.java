package com.imo.operator;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_MIC=4101;
    private static final int BG=Color.rgb(3,10,23), CARD=Color.rgb(8,24,47), CARD2=Color.rgb(12,33,61), LINE=Color.rgb(35,82,130);
    private static final int TEXT=Color.rgb(244,247,255), MUTED=Color.rgb(155,176,204), PURPLE=Color.rgb(139,92,246), GREEN=Color.rgb(54,230,126), RED=Color.rgb(235,64,105);
    private EditText command; private TextView status,state; private Button mic,send,stop,key,access;
    private TextToSpeech tts; private SpeechRecognizer recognizer; private SecureKeyStore keyStore;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean running=false,voiceMode=false,recognitionBusy=false,speaking=false,pendingListen=false,ttsReady=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(Color.rgb(2,7,16));
        keyStore=new SecureKeyStore(this); buildUi(); initTts();
    }

    private void initTts(){
        tts=new TextToSpeech(this, result->{
            ttsReady=result==TextToSpeech.SUCCESS;
            if(ttsReady){
                int r=tts.setLanguage(new Locale("id","ID"));
                if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault());
                tts.setSpeechRate(.96f); tts.setPitch(1.0f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String id){speaking=true;}
                    @Override public void onDone(String id){speaking=false;if(pendingListen&&voiceMode){pendingListen=false;new Handler(Looper.getMainLooper()).postDelayed(()->listen(),450);}}
                    @Override public void onError(String id){speaking=false;if(pendingListen&&voiceMode){pendingListen=false;new Handler(Looper.getMainLooper()).postDelayed(()->listen(),450);}}
                });
            } else setStatus("⚠ Text-to-Speech Android gagal diinisialisasi.");
        });
    }

    private void buildUi(){
        ScrollView outer=new ScrollView(this); outer.setFillViewport(true); outer.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(16),dp(18),dp(22)); outer.addView(root);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView orb=text("L",25,Color.WHITE,true); orb.setGravity(Gravity.CENTER); orb.setBackground(round(PURPLE,100)); top.addView(orb,new LinearLayout.LayoutParams(dp(62),dp(62)));
        LinearLayout names=new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(dp(13),0,0,0); names.addView(text("LUNA",29,TEXT,true));
        TextView sub=text("AI ANDROID DEVICE OPERATOR",11,MUTED,true); sub.setLetterSpacing(.08f); names.addView(sub); top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        TextView online=text("● ONLINE\n   Operator Aktif",11,GREEN,true); online.setGravity(Gravity.CENTER); online.setPadding(dp(10),dp(7),dp(10),dp(7)); online.setBackground(round(Color.rgb(7,42,34),30)); top.addView(online); root.addView(top);
        TextView tagline=text("Bicara  •  Pahami  •  Amati  •  Operasikan  •  Selesaikan",10,MUTED,false); tagline.setPadding(dp(4),dp(6),0,dp(10)); root.addView(tagline);

        LinearLayout hello=card(); hello.setOrientation(LinearLayout.HORIZONTAL); TextView avatar=text("◉",40,Color.WHITE,true); avatar.setGravity(Gravity.CENTER); avatar.setBackground(round(Color.rgb(24,70,150),100)); hello.addView(avatar,new LinearLayout.LayoutParams(dp(78),dp(78)));
        LinearLayout ht=new LinearLayout(this); ht.setOrientation(LinearLayout.VERTICAL); ht.setPadding(dp(14),0,0,0); ht.addView(text("Halo, saya LUNA!",18,TEXT,true)); ht.addView(text("Saya siap membantu Anda mengoperasikan\nAndroid dengan suara, teks, dan perintah langsung.",12,MUTED,false)); hello.addView(ht,new LinearLayout.LayoutParams(0,-2,1)); root.addView(hello);

        root.addView(section("STATUS LUNA")); status=text("Siap. Masukkan Gemini API Key lalu coba bertanya.",12,TEXT,false); LinearLayout sc=card(); sc.addView(status,new LinearLayout.LayoutParams(-1,dp(70))); root.addView(sc);
        root.addView(section("MODE OPERATOR")); LinearLayout mode=card(); mode.setOrientation(LinearLayout.HORIZONTAL); TextView brain=text("◈",25,Color.WHITE,true); brain.setGravity(Gravity.CENTER); brain.setBackground(round(PURPLE,100)); mode.addView(brain,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout mt=new LinearLayout(this); mt.setOrientation(LinearLayout.VERTICAL); mt.setPadding(dp(12),0,0,0); mt.addView(text("Model AI",11,MUTED,false)); mt.addView(text("Gemini 2.5 Flash • Free Tier",14,TEXT,true)); mt.addView(text("Teks • Screenshot • Operator",10,MUTED,false)); mode.addView(mt,new LinearLayout.LayoutParams(0,-2,1)); TextView ready=text("SIAP",11,GREEN,true); ready.setGravity(Gravity.CENTER); ready.setPadding(dp(11),dp(6),dp(11),dp(6)); ready.setBackground(stroke(Color.TRANSPARENT,GREEN,20,dp(1))); mode.addView(ready); root.addView(mode);

        root.addView(section("PROSES KERJA")); LinearLayout process=card(); process.setGravity(Gravity.CENTER); String[] icons={"◉","♬","◈","⚙","✓"}; String[] labs={"Dengar","Pahami","Amati","Bertindak","Verifikasi"};
        for(int i=0;i<5;i++){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setGravity(Gravity.CENTER);TextView ic=text(icons[i],20,i==4?Color.rgb(80,220,240):PURPLE,true);ic.setGravity(Gravity.CENTER);ic.setBackground(round(Color.rgb(26,29,88),100));p.addView(ic,new LinearLayout.LayoutParams(dp(42),dp(42)));TextView lb=text(labs[i],9,MUTED,true);lb.setGravity(Gravity.CENTER);p.addView(lb,new LinearLayout.LayoutParams(dp(58),dp(28)));process.addView(p,new LinearLayout.LayoutParams(0,-2,1));if(i<4){TextView dash=text("—",14,LINE,true);dash.setGravity(Gravity.CENTER);process.addView(dash,new LinearLayout.LayoutParams(dp(10),dp(45)));}}
        root.addView(process);

        root.addView(section("PERINTAH CEPAT")); LinearLayout quick=card(); quick.setOrientation(LinearLayout.VERTICAL); LinearLayout q1=row(); q1.addView(quickButton("▦  Buka Facebook","buka Facebook"));q1.addView(quickButton("●  Buka WhatsApp","buka WhatsApp"));q1.addView(quickButton("☁  Cek cuaca","cek cuaca hari ini"));quick.addView(q1);LinearLayout q2=row();q2.addView(quickButton("▶  Buka YouTube","buka YouTube"));q2.addView(quickButton("🔊  Volume 50","volume 50"));q2.addView(quickButton("⚙  Pengaturan","buka pengaturan"));quick.addView(q2);root.addView(quick);

        LinearLayout voice=card();voice.setOrientation(LinearLayout.VERTICAL);LinearLayout vr=row();Button textBtn=actionButton("⌨  TEKS",Color.TRANSPARENT,TEXT);vr.addView(textBtn,new LinearLayout.LayoutParams(dp(90),dp(48)));TextView wave=text("  )))    ◉    (((  ",19,PURPLE,true);wave.setGravity(Gravity.CENTER);vr.addView(wave,new LinearLayout.LayoutParams(0,dp(48),1));mic=actionButton("MIC",Color.TRANSPARENT,TEXT);vr.addView(mic,new LinearLayout.LayoutParams(dp(105),dp(48)));voice.addView(vr);TextView vt=text("Ketuk MIC untuk bicara • LUNA akan menjawab dengan suara",11,TEXT,true);vt.setGravity(Gravity.CENTER);vt.setPadding(0,dp(5),0,0);voice.addView(vt);root.addView(voice);

        LinearLayout cmdRow=new LinearLayout(this);cmdRow.setGravity(Gravity.CENTER_VERTICAL);command=new EditText(this);command.setHint("Ketik pertanyaan atau perintah…");command.setHintTextColor(Color.rgb(105,128,159));command.setTextColor(TEXT);command.setTextSize(14);command.setSingleLine(true);command.setPadding(dp(16),0,dp(10),0);command.setBackground(round(CARD2,28));cmdRow.addView(command,new LinearLayout.LayoutParams(0,dp(54),1));send=actionButton("➤",PURPLE,Color.WHITE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(58),dp(54));sp.setMargins(dp(8),0,0,0);cmdRow.addView(send,sp);root.addView(cmdRow);
        LinearLayout mainRow=new LinearLayout(this);mainRow.setPadding(0,dp(12),0,0);mainRow.setGravity(Gravity.CENTER);Button run=actionButton("▶  JALANKAN",PURPLE,Color.WHITE);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(54),1);rp.setMargins(0,0,dp(7),0);mainRow.addView(run,rp);stop=actionButton("■  STOP",RED,Color.WHITE);mainRow.addView(stop,new LinearLayout.LayoutParams(0,dp(54),.45f));root.addView(mainRow);
        LinearLayout tools=new LinearLayout(this);tools.setPadding(0,dp(10),0,0);access=outlineButton("♿  ACCESSIBILITY");key=outlineButton("⚿  GEMINI API KEY");tools.addView(access,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout kp=new LinearLayout(this);kp.setPadding(dp(8),0,0,0);kp.addView(key,new LinearLayout.LayoutParams(-1,dp(50)));tools.addView(kp,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(tools);
        state=text("",10,MUTED,false);root.addView(state);TextView foot=text("LUNA  •  Observe → Reason → Act → Verify\nTekan STOP kapan saja untuk menghentikan operator.",10,MUTED,false);foot.setPadding(dp(4),dp(12),dp(4),0);root.addView(foot);setContentView(outer);

        send.setOnClickListener(v->runCommand(command.getText().toString()));run.setOnClickListener(v->runCommand(command.getText().toString()));stop.setOnClickListener(v->stopAll());access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));key.setOnClickListener(v->showKeyDialog());mic.setOnClickListener(v->toggleVoice());textBtn.setOnClickListener(v->command.requestFocus());refreshState();
    }

    private Button quickButton(String label,String cmd){Button b=actionButton(label,Color.rgb(9,31,57),TEXT);b.setTextSize(9);b.setOnClickListener(v->{command.setText(cmd);runCommand(cmd);});b.setLayoutParams(new LinearLayout.LayoutParams(0,dp(42),1));return b;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setPadding(dp(13),dp(12),dp(13),dp(12));x.setBackground(stroke(CARD,LINE,18,dp(1)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));x.setLayoutParams(p);return x;}
    private TextView section(String s){TextView v=text(s,10,MUTED,true);v.setLetterSpacing(.1f);v.setPadding(dp(4),dp(13),0,dp(3));return v;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(3),0,dp(3));return r;}
    private TextView text(String s,float z,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button actionButton(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(0,0,0,0);b.setBackground(round(bg,18));return b;}
    private Button outlineButton(String s){Button b=actionButton(s,Color.TRANSPARENT,TEXT);b.setBackground(stroke(Color.TRANSPARENT,Color.rgb(57,88,124),18,dp(1)));return b;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private GradientDrawable stroke(int c,int l,int r,int w){GradientDrawable g=round(c,r);g.setStroke(w,l);return g;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void refreshState(){if(state!=null)state.setText(LunaAccessibilityService.get()!=null?(voiceMode?"● Accessibility aktif • MODE SUARA":"● Accessibility aktif"):"● Accessibility belum aktif");}

    private void showKeyDialog(){
        EditText e=new EditText(this);e.setHint("AIza…");e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        try{String old=keyStore.load();if(old!=null&&!old.isEmpty())e.setText(old);}catch(Exception ignored){}
        new AlertDialog.Builder(this).setTitle("Gemini API Key").setMessage("Masukkan API key Gemini dari Google AI Studio. Key disimpan terenkripsi di perangkat dan tidak dikirim ke LUNA developer.").setView(e).setPositiveButton("Simpan",(x,w)->{try{String k=e.getText().toString().trim();if(k.isEmpty()){setStatus("⚠ API key kosong.");return;}keyStore.save(k);setStatus("✓ Gemini API key tersimpan. Coba: Halo LUNA, siapa kamu?");}catch(Exception ex){setStatus("Gagal menyimpan Gemini API key: "+ex.getMessage());}}).setNegativeButton("Hapus",(x,w)->{keyStore.clear();setStatus("Gemini API key dihapus.");}).setNeutralButton("Batal",null).show();
    }

    private void ensureRecognizer(){if(recognizer!=null)return;try{if(SpeechRecognizer.isRecognitionAvailable(this))recognizer=SpeechRecognizer.createSpeechRecognizer(this);}catch(Throwable ignored){recognizer=null;}}
    private void toggleVoice(){
        if(voiceMode){voiceMode=false;recognitionBusy=false;pendingListen=false;if(recognizer!=null)recognizer.cancel();if(tts!=null)tts.stop();mic.setText("MIC");setStatus("Mode suara dihentikan.");refreshState();return;}
        if(Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        voiceMode=true;mic.setText("STOP MIC");refreshState();setStatus("🎙 LUNA siap mendengarkan…");listen();
    }
    private void listen(){
        if(!voiceMode||recognitionBusy||speaking)return;
        if(Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        ensureRecognizer();
        if(recognizer==null){setStatus("⚠ SpeechRecognizer tidak tersedia. Pastikan Google Speech Services aktif.");return;}
        try{
            recognitionBusy=true;Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"id-ID");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"id-ID");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,1200);i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,500);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle p){setStatus("🎙 Mendengarkan…");} public void onBeginningOfSpeech(){setStatus("🎙 Saya dengar…");} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){setStatus("🧠 LUNA memahami…");}
                public void onError(int e){recognitionBusy=false;if(!voiceMode)return;String m=e==SpeechRecognizer.ERROR_NO_MATCH?"Saya belum menangkap ucapan Anda.":e==SpeechRecognizer.ERROR_NETWORK?"Jaringan pengenalan suara bermasalah.":e==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Izin mikrofon ditolak.":e==SpeechRecognizer.ERROR_RECOGNIZER_BUSY?"Voice sedang sibuk.":"Voice error "+e;setStatus("⚠ "+m);new Handler(Looper.getMainLooper()).postDelayed(()->listen(),1000);}
                public void onResults(Bundle r){recognitionBusy=false;ArrayList<String>a=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){String heard=a.get(0).trim();if(!heard.isEmpty()){command.setText(heard);runCommand(heard);return;}}if(voiceMode)new Handler(Looper.getMainLooper()).postDelayed(()->listen(),400);}
                public void onPartialResults(Bundle r){} public void onEvent(int t,Bundle p){}
            });
            recognizer.startListening(i);
        }catch(Throwable t){recognitionBusy=false;setStatus("⚠ Voice gagal dimulai: "+t.getClass().getSimpleName());new Handler(Looper.getMainLooper()).postDelayed(()->listen(),1200);}
    }
    @Override public void onRequestPermissionsResult(int c,String[] p,int[] r){super.onRequestPermissionsResult(c,p,r);if(c==REQ_MIC){if(r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED){voiceMode=true;mic.setText("STOP MIC");refreshState();listen();}else{voiceMode=false;mic.setText("MIC");setStatus("Izin mikrofon belum diberikan.");}}}

    private boolean looksLikeConversation(String txt){String s=txt.toLowerCase(Locale.ROOT).trim();return s.matches(".*\\b(siapa|apa|mengapa|kenapa|bagaimana|kapan|berapa|jelaskan|jelasin|ceritakan|menurut|apakah|bisakah|bisa tidak|tolong jelaskan|buatkan|hitung|terjemahkan|halo|hai|selamat)\\b.*")&&!s.matches(".*\\b(buka|klik|tekan|cari|ketik|scroll|geser|kirim|hapus|instal|pasang|atur|setel|volume|mute|matikan|nyalakan|notifikasi|pengaturan|home|kembali)\\b.*");}
    private void runCommand(String txt){
        if(txt==null||txt.trim().isEmpty()){setStatus("Tulis atau ucapkan pertanyaan/perintah terlebih dahulu.");return;}
        if(running){setStatus("LUNA masih bekerja. Tekan STOP untuk membatalkan.");return;}
        final String cmd=txt.trim();LunaAccessibilityService svc=LunaAccessibilityService.get();
        if(svc==null&&!looksLikeConversation(cmd)){setStatus("⚠ Aktifkan Accessibility Service LUNA untuk perintah Android. Pertanyaan biasa tetap bisa dijawab.");}
        running=true;pendingListen=voiceMode;send.setEnabled(false);if(svc!=null)svc.resumeNow();setStatus("🧠 LUNA memproses: "+cmd);
        worker.submit(()->{try{LunaAgent agent=new LunaAgent(this,svc,keyStore,new LunaAgent.Callback(){public void status(String t){setStatus(t);}public void speak(String t){speakLuna(t);}public boolean confirm(JSONObject p){return confirmAndWait(p);}public void finished(){running=false;runOnUiThread(()->send.setEnabled(true));if(voiceMode&&pendingListen&&!speaking&&ttsReady){pendingListen=false;new Handler(Looper.getMainLooper()).postDelayed(()->listen(),450);}}});agent.run(cmd);}catch(Throwable e){running=false;setStatus("LUNA error: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));if(voiceMode&&pendingListen){pendingListen=false;new Handler(Looper.getMainLooper()).postDelayed(()->listen(),1000);}runOnUiThread(()->send.setEnabled(true));}});
    }
    private boolean confirmAndWait(JSONObject p){final Object lock=new Object();final boolean[] ok={false},done={false};runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Konfirmasi tindakan").setMessage(p.optString("speak","LUNA meminta izin melakukan tindakan sensitif.")).setNegativeButton("Batal",(d,w)->{synchronized(lock){ok[0]=false;done[0]=true;lock.notifyAll();}}).setPositiveButton("Lanjutkan",(d,w)->{synchronized(lock){ok[0]=true;done[0]=true;lock.notifyAll();}}).setOnCancelListener(d->{synchronized(lock){done[0]=true;lock.notifyAll();}}).show());synchronized(lock){while(!done[0])try{lock.wait(30000);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}}return ok[0]&&running;}
    private void speakLuna(String t){if(t==null||t.trim().isEmpty())return;setStatus("🗣 LUNA: "+t);if(!ttsReady||tts==null){setStatus("⚠ LUNA sudah mendapat jawaban, tetapi Text-to-Speech belum siap. Periksa mesin Text-to-Speech Android.");return;}speaking=true;pendingListen=voiceMode;runOnUiThread(()->tts.speak(t,TextToSpeech.QUEUE_FLUSH,null,"LUNA_RESPONSE"));}
    private void stopAll(){running=false;voiceMode=false;recognitionBusy=false;pendingListen=false;if(recognizer!=null)recognizer.cancel();if(tts!=null)tts.stop();LunaAccessibilityService s=LunaAccessibilityService.get();if(s!=null)s.stopNow();setStatus("■ STOP — LUNA dihentikan.");runOnUiThread(()->{send.setEnabled(true);mic.setText("MIC");refreshState();});}
    private void setStatus(String s){runOnUiThread(()->{if(status!=null)status.setText(s);});}
    @Override protected void onResume(){super.onResume();refreshState();}
    @Override protected void onDestroy(){running=false;voiceMode=false;recognitionBusy=false;if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();worker.shutdownNow();super.onDestroy();}
}
