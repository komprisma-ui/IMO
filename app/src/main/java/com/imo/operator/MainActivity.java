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

/** LUNA control console. Voice is a continuous listen -> understand -> act -> speak loop. */
public class MainActivity extends Activity {
    private static final int REQ_MIC=4101, REQ_NOTIFICATIONS=4102;
    private int micRequestMode=0; // 1=enrollment, 2=voice mode
    private static final int BG=Color.rgb(3,10,23),CARD=Color.rgb(8,24,47),CARD2=Color.rgb(12,33,61),LINE=Color.rgb(35,82,130);
    private static final int TEXT=Color.rgb(244,247,255),MUTED=Color.rgb(155,176,204),PURPLE=Color.rgb(139,92,246),GREEN=Color.rgb(54,230,126),RED=Color.rgb(235,64,105);
    private EditText command; private TextView status,state;
    private Button mic,send,stop,key,access;
    private TextToSpeech tts; private SpeechRecognizer recognizer; private SecureKeyStore keyStore; private BridgeStore bridgeStore; private VoiceProfileManager voiceProfile; private volatile boolean voiceGateBusy=false;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean voiceMode=false,recognitionBusy=false,speaking=false,ttsReady=false,running=false;
    private volatile boolean ttsPending=false;
    private int recognitionRetries=0;

    @Override public void onCreate(Bundle b){super.onCreate(b);requestNotificationPermissionIfNeeded();getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(Color.rgb(2,7,16));keyStore=new SecureKeyStore(this);bridgeStore=new BridgeStore(this);voiceProfile=new VoiceProfileManager(this,keyStore);buildUi();initTts();}

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
    }

    private void initTts(){
        tts=new TextToSpeech(this,result->{
            ttsReady=result==TextToSpeech.SUCCESS;
            if(!ttsReady){setStatus("⚠ Mesin suara LUNA belum siap. Aktifkan Text-to-Speech Android.");return;}
            int r=tts.setLanguage(new Locale("id","ID"));
            if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)tts.setLanguage(Locale.getDefault());
            tts.setSpeechRate(.96f);tts.setPitch(1f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){speaking=true;}
                @Override public void onDone(String id){speaking=false;if(ttsPending&&voiceMode){ttsPending=false;postListen(550);}}
                @Override public void onError(String id){speaking=false;if(ttsPending&&voiceMode){ttsPending=false;postListen(750);}}
            });
        });
    }

    private void buildUi(){
        ScrollView outer=new ScrollView(this);outer.setFillViewport(true);outer.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(22));outer.addView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView orb=text("L",25,Color.WHITE,true);orb.setGravity(Gravity.CENTER);orb.setBackground(round(PURPLE,100));top.addView(orb,new LinearLayout.LayoutParams(dp(62),dp(62)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.setPadding(dp(13),0,0,0);names.addView(text("LUNA",29,TEXT,true));
        TextView sub=text("AI ANDROID DEVICE OPERATOR",11,MUTED,true);sub.setLetterSpacing(.08f);names.addView(sub);top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        TextView online=text("● ONLINE\nOperator Aktif",11,GREEN,true);online.setGravity(Gravity.CENTER);online.setPadding(dp(10),dp(7),dp(10),dp(7));online.setBackground(round(Color.rgb(7,42,34),30));top.addView(online);root.addView(top);
        TextView tag=text("Bicara  •  Pahami  •  Amati  •  Operasikan  •  Selesaikan",10,MUTED,false);tag.setPadding(dp(4),dp(6),0,dp(10));root.addView(tag);
        LinearLayout hello=card();hello.setOrientation(LinearLayout.HORIZONTAL);TextView av=text("◉",40,Color.WHITE,true);av.setGravity(Gravity.CENTER);av.setBackground(round(Color.rgb(24,70,150),100));hello.addView(av,new LinearLayout.LayoutParams(dp(78),dp(78)));
        LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);ht.setPadding(dp(14),0,0,0);ht.addView(text("Halo, saya LUNA!",18,TEXT,true));ht.addView(text("Saya siap membantu Anda mengoperasikan\nAndroid dengan suara, teks, dan perintah langsung.",12,MUTED,false));hello.addView(ht,new LinearLayout.LayoutParams(0,-2,1));root.addView(hello);
        root.addView(section("STATUS LUNA"));status=text("Siap. Tekan MIC lalu bicara.",12,TEXT,false);LinearLayout sc=card();sc.addView(status,new LinearLayout.LayoutParams(-1,dp(78)));root.addView(sc);
        root.addView(section("MODE OPERATOR"));LinearLayout mode=card();mode.setOrientation(LinearLayout.HORIZONTAL);TextView brain=text("◈",25,Color.WHITE,true);brain.setGravity(Gravity.CENTER);brain.setBackground(round(PURPLE,100));mode.addView(brain,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout mt=new LinearLayout(this);mt.setOrientation(LinearLayout.VERTICAL);mt.setPadding(dp(12),0,0,0);mt.addView(text("Model AI",11,MUTED,false));mt.addView(text("Gemini 2.5 Flash • Free Tier",14,TEXT,true));mt.addView(text("Teks • Screenshot • Operator",10,MUTED,false));mode.addView(mt,new LinearLayout.LayoutParams(0,-2,1));
        TextView rd=text("SIAP",11,GREEN,true);rd.setGravity(Gravity.CENTER);rd.setPadding(dp(11),dp(6),dp(11),dp(6));rd.setBackground(stroke(Color.TRANSPARENT,GREEN,20,dp(1)));mode.addView(rd);root.addView(mode);
        root.addView(section("PROSES KERJA"));LinearLayout process=card();process.setGravity(Gravity.CENTER);String[] ic={"◉","♬","◈","⚙","✓"};String[] lb={"Dengar","Pahami","Amati","Bertindak","Verifikasi"};
        for(int i=0;i<5;i++){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setGravity(Gravity.CENTER);TextView x=text(ic[i],20,i==4?Color.CYAN:PURPLE,true);x.setGravity(Gravity.CENTER);x.setBackground(round(Color.rgb(26,29,88),100));p.addView(x,new LinearLayout.LayoutParams(dp(42),dp(42)));TextView y=text(lb[i],9,MUTED,true);y.setGravity(Gravity.CENTER);p.addView(y,new LinearLayout.LayoutParams(dp(58),dp(28)));process.addView(p,new LinearLayout.LayoutParams(0,-2,1));if(i<4)process.addView(text("—",14,LINE,true),new LinearLayout.LayoutParams(dp(10),dp(45)));}root.addView(process);
        root.addView(section("PERINTAH CEPAT"));LinearLayout quick=card();quick.setOrientation(LinearLayout.VERTICAL);LinearLayout q1=row();q1.addView(quickButton("▦  Buka Facebook","buka Facebook"));q1.addView(quickButton("●  Buka WhatsApp","buka WhatsApp"));q1.addView(quickButton("☁  Cek cuaca","cek cuaca hari ini"));quick.addView(q1);LinearLayout q2=row();q2.addView(quickButton("▶  Buka YouTube","buka YouTube"));q2.addView(quickButton("🔊  Volume 50","volume 50"));q2.addView(quickButton("⚙  Pengaturan","buka pengaturan"));quick.addView(q2);root.addView(quick);
        LinearLayout voice=card();voice.setOrientation(LinearLayout.VERTICAL);LinearLayout vr=row();Button tb=actionButton("⌨  TEKS",Color.TRANSPARENT,TEXT);vr.addView(tb,new LinearLayout.LayoutParams(dp(90),dp(48)));TextView wave=text(")))    ◉    (((",19,PURPLE,true);wave.setGravity(Gravity.CENTER);vr.addView(wave,new LinearLayout.LayoutParams(0,dp(48),1));mic=actionButton("MIC",Color.TRANSPARENT,TEXT);vr.addView(mic,new LinearLayout.LayoutParams(dp(105),dp(48)));voice.addView(vr);TextView vt=text("Ketuk MIC → bicara → LUNA menjawab → mendengar lagi",11,TEXT,true);vt.setGravity(Gravity.CENTER);vt.setPadding(0,dp(5),0,0);voice.addView(vt);root.addView(voice);
        LinearLayout cr=new LinearLayout(this);cr.setGravity(Gravity.CENTER_VERTICAL);command=new EditText(this);command.setHint("Ketik pertanyaan atau perintah…");command.setHintTextColor(Color.rgb(105,128,159));command.setTextColor(TEXT);command.setTextSize(14);command.setSingleLine(true);command.setPadding(dp(16),0,dp(10),0);command.setBackground(round(CARD2,28));cr.addView(command,new LinearLayout.LayoutParams(0,dp(54),1));send=actionButton("➤",PURPLE,Color.WHITE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(58),dp(54));sp.setMargins(dp(8),0,0,0);cr.addView(send,sp);root.addView(cr);
        LinearLayout mr=new LinearLayout(this);mr.setPadding(0,dp(12),0,0);mr.setGravity(Gravity.CENTER);Button run=actionButton("▶  JALANKAN",PURPLE,Color.WHITE);mr.addView(run,new LinearLayout.LayoutParams(0,dp(54),1));stop=actionButton("■  STOP",RED,Color.WHITE);LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(0,dp(54),.45f);stp.setMargins(dp(7),0,0,0);mr.addView(stop,stp);root.addView(mr);
        LinearLayout tools=new LinearLayout(this);tools.setPadding(0,dp(10),0,0);access=outlineButton("♿  ACCESSIBILITY");key=outlineButton("⚿  GEMINI API KEY");tools.addView(access,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout kk=new LinearLayout(this);kk.setPadding(dp(8),0,0,0);kk.addView(key,new LinearLayout.LayoutParams(-1,dp(50)));tools.addView(kk,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(tools); Button enroll=outlineButton("🎙  DAFTAR SUARA PEMILIK"); LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(50)); ep.setMargins(0,dp(8),0,0); root.addView(enroll,ep); Button bridge=outlineButton("↔  LUNA BRIDGE"); bridge.setOnClickListener(v->showBridgeDialog()); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50)); bp.setMargins(0,dp(8),0,0); root.addView(bridge,bp); state=text("",10,MUTED,false);root.addView(state);TextView foot=text("LUNA  •  Dengar → Pahami → Amati → Bertindak → Verifikasi\nSTOP menghentikan operator dan mode suara.",10,MUTED,false);foot.setPadding(dp(4),dp(12),dp(4),0);root.addView(foot);setContentView(outer);
        send.setOnClickListener(v->runCommand(command.getText().toString()));run.setOnClickListener(v->runCommand(command.getText().toString()));stop.setOnClickListener(v->stopAll());access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));key.setOnClickListener(v->showKeyDialog());enroll.setOnClickListener(v->showVoiceEnrollment());mic.setOnClickListener(v->toggleVoice());tb.setOnClickListener(v->{command.requestFocus();command.setSelection(command.length());});refreshState();
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

    private void showBridgeDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(6),0,dp(6),0);
        EditText url=new EditText(this);url.setHint("wss://server-anda/imo");url.setSingleLine(true);url.setText(bridgeStore.url());url.setTextColor(TEXT);url.setHintTextColor(MUTED);
        EditText token=new EditText(this);token.setHint("Bridge token");token.setSingleLine(true);token.setTextColor(TEXT);token.setHintTextColor(MUTED);token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(url);box.addView(token);
        new AlertDialog.Builder(this).setTitle("LUNA ↔ IMO Bridge").setMessage("Bridge memakai WSS + Bearer token. Server harus mengautentikasi perangkat sebelum menerima perintah.")
          .setView(box).setPositiveButton("Simpan & Hubungkan",(d,w)->{
            try{String u=url.getText().toString().trim(),t=token.getText().toString().trim();if(u.isEmpty()||t.isEmpty()){setStatus("⚠ URL dan token bridge wajib diisi.");return;}bridgeStore.save(u,t);Intent i=new Intent(this,LunaBridgeService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);setStatus("↔ Bridge IMO sedang menghubungkan…");}catch(Exception e){setStatus("⚠ Gagal menyimpan bridge: "+safe(e));}
          }).setNegativeButton("Hapus & Putus",(d,w)->{bridgeStore.clear();stopService(new Intent(this,LunaBridgeService.class));setStatus("Bridge diputus.");}).setNeutralButton("Batal",null).show();
    }

    private void showKeyDialog(){EditText e=new EditText(this);e.setHint("AIza…");e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);try{String old=keyStore.load();if(old!=null&&!old.isEmpty())e.setText(old);}catch(Exception ignored){}new AlertDialog.Builder(this).setTitle("Gemini API Key").setMessage("Masukkan API key Gemini dari Google AI Studio. Key disimpan terenkripsi di perangkat.").setView(e).setPositiveButton("Simpan",(d,w)->{try{String k=e.getText().toString().trim();if(k.isEmpty()){setStatus("⚠ API key kosong.");return;}keyStore.save(k);setStatus("✓ Gemini API key tersimpan. Coba bicara: Halo LUNA.");}catch(Exception ex){setStatus("Gagal menyimpan key: "+ex.getMessage());}}).setNegativeButton("Hapus",(d,w)->{try{keyStore.clear();}catch(Exception ignored){}setStatus("Gemini API key dihapus.");}).setNeutralButton("Batal",null).show();}

    private void showVoiceEnrollment(){
        if(Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            micRequestMode=1; ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);
            setStatus("Izinkan mikrofon. Setelah diizinkan, pendaftaran suara akan dibuka otomatis.");
            return;
        }
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),0,dp(8),0);
        TextView info=text("Rekam 3 sampel suara dalam ruangan tenang. Ucapkan kalimat yang sama dengan suara normal. Profil disimpan terenkripsi di perangkat.",12,TEXT,false);
        box.addView(info);
        TextView stateView=text("Sampel 0/3 siap.",12,MUTED,true);stateView.setPadding(0,dp(12),0,0);box.addView(stateView);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Pendaftaran Suara Pemilik").setView(box)
                .setNegativeButton("Tutup",null).setPositiveButton("REKAM SAMPel 1",null).create();
        final float[][] samples=new float[3][];
        final int[] index={0};
        dialog.setOnShowListener(x->{
            Button b=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(v->{
                if(index[0]>=3)return;
                b.setEnabled(false);int n=index[0]+1;stateView.setText("🎙 Merekam sampel "+n+"/3… ucapkan: \"Halo LUNA, ini suara saya.\"");setStatus("🎙 Pendaftaran suara: sampel "+n+"/3…");
                voiceProfile.capture(1800,(feature,error)->runUi(()->{
                    if(error!=null){stateView.setText("⚠ "+error);setStatus("⚠ Gagal merekam sampel "+n+": "+error);b.setEnabled(true);return;}
                    if(feature==null){stateView.setText("⚠ Audio tidak valid.");b.setEnabled(true);return;}
                    samples[index[0]]=feature;index[0]++;
                    if(index[0]<3){stateView.setText("✓ Sampel "+index[0]+"/3 tersimpan. Siap merekam berikutnya.");b.setText("REKAM SAMPEL "+(index[0]+1));b.setEnabled(true);}
                    else{try{voiceProfile.saveAverage(samples);stateView.setText("✓ Profil suara tersimpan terenkripsi.");setStatus("✓ Pendaftaran suara selesai. LUNA akan memeriksa kecocokan suara sebelum mendengar perintah.");b.setText("SELESAI");b.setOnClickListener(doneView->dialog.dismiss());}catch(Exception e){stateView.setText("⚠ Gagal menyimpan profil: "+safe(e));b.setEnabled(true);}}
                }));
            });
        });
        dialog.show();
    }

    private void verifyVoiceThenStart(){
        if(!voiceMode||voiceGateBusy)return;
        voiceGateBusy=true;setStatus("🔐 Memeriksa kecocokan suara pemilik…");
        voiceProfile.capture(850,(feature,error)->runUi(()->{
            voiceGateBusy=false;
            if(!voiceMode)return;
            if(error!=null){setStatus("⚠ Verifikasi suara gagal: "+error);scheduleRetry(1200);return;}
            float score=voiceProfile.similarity(feature);
            if(score<0.78f){setStatus("🔒 Suara tidak cocok dengan profil pemilik. Saya tidak menjalankan perintah.");scheduleRetry(1200);return;}
            setStatus("✓ Suara pemilik terverifikasi. Mendengarkan perintah…");
            startRecognitionInternal();
        }));
    }

    private void startRecognitionInternal(){
        if(!voiceMode||recognitionBusy||speaking)return;
        createRecognizer();if(recognizer==null){scheduleRetry(1500);return;}
        try{
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"id-ID");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"id-ID");
            i.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,"id-ID");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
            i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,2300);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,1500);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,700);
            setStatus("🎙 Mendengarkan… silakan bicara sekarang");recognitionBusy=true;recognizer.startListening(i);
        }catch(Throwable t){recognitionBusy=false;destroyRecognizer();setStatus("⚠ Mikrofon gagal dimulai: "+safe(t));scheduleRetry(1200);}
    }

    private void toggleVoice(){
        if(voiceMode){stopVoiceOnly();return;}
        if(Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){micRequestMode=2; ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        voiceMode=true;recognitionRetries=0;mic.setText("STOP MIC");refreshState();setStatus("🎙 Menyiapkan mikrofon…");postListen(250);
    }
    private void stopVoiceOnly(){voiceMode=false;recognitionBusy=false;recognitionRetries=0;ttsPending=false;if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}}if(tts!=null){try{tts.stop();}catch(Exception ignored){}}speaking=false;mic.setText("MIC");setStatus("Mode suara dihentikan.");refreshState();}

    private void createRecognizer(){
        destroyRecognizer();
        try{
            if(!SpeechRecognizer.isRecognitionAvailable(this)){setStatus("⚠ Android tidak menemukan mesin pengenal suara. Aktifkan Google Speech Services.");return;}
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle p){recognitionBusy=true;setStatus("🎙 Mendengarkan… silakan bicara sekarang");}
                @Override public void onBeginningOfSpeech(){recognitionRetries=0;setStatus("🎙 Saya mendengar suara Anda…");}
                @Override public void onRmsChanged(float r){if(voiceMode&&r>-2)setStatus("🎙 Saya mendengar suara Anda…");}
                @Override public void onBufferReceived(byte[] b){}
                @Override public void onEndOfSpeech(){setStatus("🧠 Mengubah suara menjadi teks…");}
                @Override public void onError(int e){recognitionBusy=false;destroyRecognizer();if(!voiceMode)return;handleRecognitionError(e);}
                @Override public void onResults(Bundle b){recognitionBusy=false;String result=bestResult(b);destroyRecognizer();if(!voiceMode)return;if(result.isEmpty()){setStatus("⚠ Suara tertangkap tetapi teks kosong. Coba bicara lebih jelas.");scheduleRetry(800);return;}handleVoiceText(result);}
                @Override public void onPartialResults(Bundle b){String partial=bestResult(b);if(!partial.isEmpty()&&voiceMode)setStatus("🎙 Anda: \""+partial+"\"");}
                @Override public void onEvent(int a,Bundle b){}
            });
        }catch(Throwable t){recognizer=null;setStatus("⚠ Gagal menyiapkan pengenal suara: "+safe(t));}
    }
    private void startRecognition(){
        if(!voiceMode||recognitionBusy||speaking)return;
        if(Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){micRequestMode=2; ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        if(voiceProfile.hasProfile()){verifyVoiceThenStart();return;}
        startRecognitionInternal();
    }
    private void handleRecognitionError(int e){
        String msg=errorText(e);
        if(e==SpeechRecognizer.ERROR_NO_MATCH||e==SpeechRecognizer.ERROR_SPEECH_TIMEOUT){if(recognitionRetries<4){recognitionRetries++;setStatus(msg+" Saya dengarkan lagi… "+recognitionRetries+"/4");scheduleRetry(700);}else{recognitionRetries=0;setStatus("⚠ Saya belum menangkap ucapan Anda. Silakan bicara lagi.");scheduleRetry(900);}}else if(e==SpeechRecognizer.ERROR_RECOGNIZER_BUSY){setStatus("⚠ Mesin suara sedang sibuk. Saya reset lalu coba lagi.");scheduleRetry(1000);}else if(e==SpeechRecognizer.ERROR_NETWORK||e==SpeechRecognizer.ERROR_NETWORK_TIMEOUT){setStatus("⚠ Pengenalan suara membutuhkan koneksi. Coba lagi…");scheduleRetry(1600);}else if(e==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){voiceMode=false;mic.setText("MIC");setStatus("⚠ Izin mikrofon ditolak. Izinkan mikrofon untuk LUNA.");refreshState();}else{setStatus("⚠ Pengenalan suara: "+msg);scheduleRetry(1400);}
    }
    private String errorText(int e){switch(e){case SpeechRecognizer.ERROR_AUDIO:return "Audio mikrofon bermasalah.";case SpeechRecognizer.ERROR_CLIENT:return "Mesin suara direset.";case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return "Izin mikrofon belum diberikan.";case SpeechRecognizer.ERROR_NETWORK:return "Jaringan pengenal suara bermasalah.";case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return "Jaringan pengenal suara timeout.";case SpeechRecognizer.ERROR_NO_MATCH:return "Ucapan belum terbaca.";case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return "Mesin pengenal suara sedang sibuk.";case SpeechRecognizer.ERROR_SERVER:return "Server pengenal suara bermasalah.";case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return "Tidak terdengar ucapan.";default:return "kode error "+e;}}
    private String bestResult(Bundle b){if(b==null)return "";ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a==null)return "";for(String s:a)if(s!=null&&!s.trim().isEmpty())return s.trim();return "";}
    private void handleVoiceText(String result){command.setText(result);command.setSelection(command.length());setStatus("👤 Anda: \""+result+"\"\n🧠 LUNA memahami dan bekerja…");runCommand(result);}
    private void postListen(long ms){new Handler(Looper.getMainLooper()).postDelayed(this::startRecognition,Math.max(100,ms));}
    private void scheduleRetry(long ms){if(voiceMode)new Handler(Looper.getMainLooper()).postDelayed(this::startRecognition,ms);}
    private void destroyRecognizer(){if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}try{recognizer.destroy();}catch(Exception ignored){}recognizer=null;}recognitionBusy=false;}
    private String safe(Throwable t){return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}

    private void runCommand(String raw){
        final String cmd=raw==null?"":raw.trim();if(cmd.isEmpty()){setStatus("Ketik atau ucapkan perintah kepada LUNA.");return;}
        if(running){setStatus("LUNA masih menyelesaikan perintah sebelumnya. Tekan STOP bila ingin membatalkan.");return;}
        running=true;if(LunaAccessibilityService.get()!=null)LunaAccessibilityService.get().resumeNow();
        worker.execute(()->{LunaAccessibilityService service=LunaAccessibilityService.get();new LunaAgent(this,service,keyStore,new LunaAgent.Callback(){
            @Override public void status(String s){runUi(()->setStatus(s));}
            @Override public void speak(String s){runUi(()->speakLuna(s));}
            @Override public boolean confirm(JSONObject plan){return askConfirmation(plan);}
            @Override public void finished(){runUi(()->{running=false;if(voiceMode&&!speaking&&!recognitionBusy&&!ttsPending)postListen(700);});}
        }).run(cmd);});
    }
    private boolean askConfirmation(JSONObject plan){
        final boolean[] answer={false};CountDownLatchBox box=new CountDownLatchBox();runUi(()->{String action=plan==null?"tindakan sensitif":plan.optString("speak","tindakan sensitif");new AlertDialog.Builder(this).setTitle("Konfirmasi LUNA").setMessage("LUNA akan melakukan tindakan yang memerlukan persetujuan.\n\n"+action+"\n\nLanjutkan?").setNegativeButton("BATAL",(d,w)->{answer[0]=false;box.open();}).setPositiveButton("LANJUTKAN",(d,w)->{answer[0]=true;box.open();}).setOnCancelListener(d->{answer[0]=false;box.open();}).show();});box.await();return answer[0];
    }
    private static final class CountDownLatchBox{private final java.util.concurrent.CountDownLatch l=new java.util.concurrent.CountDownLatch(1);void open(){l.countDown();}void await(){try{l.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
    private void stopAll(){
        voiceMode=false;recognitionBusy=false;ttsPending=false;running=false;recognitionRetries=0;destroyRecognizer();if(tts!=null){try{tts.stop();}catch(Exception ignored){}}speaking=false;LunaAccessibilityService s=LunaAccessibilityService.get();if(s!=null)s.stopNow();mic.setText("MIC");setStatus("■ STOP — LUNA dihentikan. Tekan MIC untuk mode suara lagi.");refreshState();
    }
    private void speakLuna(String s){if(s==null||s.trim().isEmpty())return;setStatus("🔊 LUNA: "+s);if(!ttsReady||tts==null){if(voiceMode)postListen(500);return;}ttsPending=voiceMode;String id="luna-"+System.nanoTime();tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,id);}
    private void setStatus(String s){runUi(()->{if(status!=null)status.setText(s);});}
    private void runUi(Runnable r){if(Looper.myLooper()==Looper.getMainLooper())r.run();else runOnUiThread(r);}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);if(requestCode==REQ_MIC){boolean ok=grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED;int mode=micRequestMode;micRequestMode=0;if(!ok){voiceMode=false;setStatus("⚠ Izin mikrofon diperlukan agar LUNA dapat mendengar.");refreshState();return;}if(mode==1){showVoiceEnrollment();}else{voiceMode=true;mic.setText("STOP MIC");refreshState();postListen(250);}}}
    @Override protected void onResume(){super.onResume();new Handler(Looper.getMainLooper()).postDelayed(this::refreshState,300);}
    @Override protected void onDestroy(){voiceMode=false;destroyRecognizer();if(tts!=null){try{tts.stop();tts.shutdown();}catch(Exception ignored){}}worker.shutdownNow();super.onDestroy();}
}
