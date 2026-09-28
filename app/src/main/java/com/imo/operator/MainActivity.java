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
        LinearLayout mt=new LinearLayout(this);mt.setOrientation(LinearLayout.VERTICAL);mt.setPadding(dp(12),0,0,0);mt.addView(text("Model AI",11,MUTED,false));mt.addView(text("Gemini 3.8 Flash • GA",14,TEXT,true));mt.addView(text("Teks • Screenshot • Operator",10,MUTED,false));mode.addView(mt,new LinearLayout.LayoutParams(0,-2,1));
        TextView rd=text("SIAP",11,GREEN,true);rd.setGravity(Gravity.CENTER);rd.setPadding(dp(11),dp(6),dp(11),dp(6));rd.setBackground(stroke(Color.TRANSPARENT,GREEN,20,dp(1)));mode.addView(rd);root.addView(mode);
        root.addView(section("PROSES KERJA"));LinearLayout process=card();process.setGravity(Gravity.CENTER);String[] ic={"◉","♬","◈","⚙","✓"};String[] lb={"Dengar","Pahami","Amati","Bertindak","Verifikasi"};
        for(int i=0;i<5;i++){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setGravity(Gravity.CENTER);TextView x=text(ic[i],20,i==4?Color.CYAN:PURPLE,true);x.setGravity(Gravity.CENTER);x.setBackground(round(Color.rgb(26,29,88),100));p.addView(x,new LinearLayout.LayoutParams(dp(42),dp(42)));TextView y=text(lb[i],9,MUTED,true);y.setGravity(Gravity.CENTER);p.addView(y,new LinearLayout.LayoutParams(dp(58),dp(28)));process.addView(p,new LinearLayout.LayoutParams(0,-2,1));if(i<4)process.addView(text("—",14,LINE,true),new LinearLayout.LayoutParams(dp(10),dp(45)));}root.addView(process);
        root.addView(section("PERINTAH CEPAT"));LinearLayout quick=card();quick.setOrientation(LinearLayout.VERTICAL);LinearLayout q1=row();q1.addView(quickButton("▦  Buka Facebook","buka Facebook"));q1.addView(quickButton("●  Buka WhatsApp","buka WhatsApp"));q1.addView(quickButton("☁  Cek cuaca","cek cuaca hari ini"));quick.addView(q1);LinearLayout q2=row();q2.addView(quickButton("▶  Buka YouTube","buka YouTube"));q2.addView(quickButton("🔊 Volume 50","volume 50"));q2.addView(quickButton("⚙ Pengaturan","buka pengaturan"));quick.addView(q2);root.addView(quick);
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