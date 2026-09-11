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
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 4101;
    private static final int BG = Color.rgb(7,17,31);
    private static final int CARD = Color.rgb(13,28,48);
    private static final int CARD2 = Color.rgb(18,37,61);
    private static final int TEXT = Color.rgb(241,245,249);
    private static final int MUTED = Color.rgb(154,169,188);
    private static final int PURPLE = Color.rgb(139,124,255);
    private static final int GREEN = Color.rgb(74,222,128);

    private EditText command;
    private TextView status, state, titleSub;
    private Button mic, send, stop, key, access;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private SecureKeyStore keyStore;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile boolean voiceMode;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.rgb(5,10,18));
        keyStore = new SecureKeyStore(this);
        buildUi();
        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("id", "ID"));
                tts.setSpeechRate(0.96f);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault());
            }
        });
        if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer = SpeechRecognizer.createSpeechRecognizer(this);
    }

    private void buildUi() {
        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(22));
        outer.addView(root);

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        TextView orb = new TextView(this);
        orb.setText("L"); orb.setTextColor(Color.WHITE); orb.setTextSize(25); orb.setGravity(Gravity.CENTER); orb.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        orb.setBackground(round(PURPLE, 100));
        hero.addView(orb, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(dp(14),0,0,0);
        TextView title = text("LUNA", 27, TEXT, true); names.addView(title);
        titleSub = text("ANDROID INTELLIGENT OPERATOR", 11, MUTED, true); titleSub.setLetterSpacing(.08f); names.addView(titleSub);
        hero.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(hero);

        Space gap = new Space(this); root.addView(gap, new LinearLayout.LayoutParams(1,dp(18)));
        LinearLayout chip = new LinearLayout(this); chip.setGravity(Gravity.CENTER_VERTICAL); chip.setPadding(dp(14),dp(10),dp(14),dp(10));
        state = text("●  Menyiapkan operator…", 14, TEXT, true); chip.addView(state, new LinearLayout.LayoutParams(0,-2,1));
        TextView badge = text("ONLINE", 10, GREEN, true); badge.setGravity(Gravity.CENTER); badge.setPadding(dp(9),dp(5),dp(9),dp(5)); badge.setBackground(round(Color.rgb(15,55,39),40)); chip.addView(badge);
        chip.setBackground(round(CARD, 18)); root.addView(chip);

        root.addView(label("AKTIVITAS LUNA"));
        LinearLayout logCard = new LinearLayout(this); logCard.setPadding(dp(16),dp(14),dp(16),dp(14));
        status = text("LUNA siap. Aktifkan Accessibility dan API key untuk mulai.", 14, MUTED, false); status.setGravity(Gravity.TOP); status.setLineSpacing(0,1.12f);
        ScrollView logScroll = new ScrollView(this); logScroll.setFillViewport(true); logScroll.addView(status); logCard.addView(logScroll,new LinearLayout.LayoutParams(-1,dp(190))); logCard.setBackground(round(CARD,20)); root.addView(logCard);

        root.addView(label("PERINTAH"));
        command = new EditText(this); command.setHint("Contoh: buka Facebook lalu cari marketplace"); command.setHintTextColor(Color.rgb(108,126,149)); command.setTextColor(TEXT); command.setTextSize(15); command.setPadding(dp(16),dp(13),dp(16),dp(13)); command.setSingleLine(false); command.setMinLines(2); command.setGravity(Gravity.TOP|Gravity.START); command.setBackground(round(CARD2,18));
        root.addView(command,new LinearLayout.LayoutParams(-1,dp(86)));

        LinearLayout mainRow = new LinearLayout(this); mainRow.setPadding(0,dp(12),0,0);
        send = actionButton("JALANKAN", PURPLE, Color.WHITE); mainRow.addView(send,new LinearLayout.LayoutParams(0,dp(54),2));
        mic = actionButton("MIC", CARD2, TEXT); LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(54),1);mp.setMargins(dp(8),0,0,0);mainRow.addView(mic,mp);
        stop = actionButton("STOP", Color.rgb(122,44,58), Color.WHITE); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(54),1);sp.setMargins(dp(8),0,0,0);mainRow.addView(stop,sp); root.addView(mainRow);

        LinearLayout tools = new LinearLayout(this); tools.setPadding(0,dp(10),0,0);
        access=outlineButton("ACCESSIBILITY"); key=outlineButton("API KEY"); tools.addView(access,new LinearLayout.LayoutParams(0,dp(50),1)); LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(0,dp(50),1);kp.setMargins(dp(8),0,0,0);tools.addView(key,kp);root.addView(tools);

        TextView hint=text("LUNA  •  Observe → Reason → Act → Verify\nTekan STOP kapan saja untuk menghentikan operator.",11,MUTED,false); hint.setPadding(2,dp(16),2,0);root.addView(hint);
        setContentView(outer);
        send.setOnClickListener(v -> runCommand(command.getText().toString()));
        stop.setOnClickListener(v -> stopAll());
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        key.setOnClickListener(v -> showKeyDialog());
        mic.setOnClickListener(v -> toggleVoice());
        refreshState();
    }

    private TextView label(String s){ TextView v=text(s,11,MUTED,true); v.setPadding(2,dp(18),2,dp(8)); v.setLetterSpacing(.08f); return v; }
    private TextView text(String s,float size,int color,boolean bold){ TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v; }
    private Button actionButton(String s,int bg,int fg){ Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(0,0,0,0);b.setBackground(round(bg,16));return b; }
    private Button outlineButton(String s){ Button b=actionButton(s,Color.TRANSPARENT,TEXT);b.setBackground(stroke(CARD2,Color.rgb(65,87,113),16,dp(1)));return b; }
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable stroke(int color,int line,int radius,int width){GradientDrawable g=round(color,radius);g.setStroke(width,line);return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void refreshState() {
        boolean on=LunaAccessibilityService.get()!=null;
        state.setText(on?(voiceMode?"●  Operator aktif  •  MODE SUARA":"●  Operator Android aktif"):"●  Accessibility belum aktif");
    }
    @Override protected void onResume(){super.onResume();refreshState();}

    private void showKeyDialog(){
        EditText e=new EditText(this);e.setHint("sk-…");e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        try{String old=keyStore.load();if(!old.isEmpty())e.setText(old);}catch(Exception ignored){}
        AlertDialog d=new AlertDialog.Builder(this).setTitle("OpenAI API Key").setMessage("Disimpan terenkripsi di perangkat. Jangan kirim key ke siapa pun.").setView(e).setPositiveButton("Simpan",(x,w)->{try{keyStore.save(e.getText().toString().trim());setStatus("✓ API key tersimpan.");}catch(Exception ex){setStatus("Gagal menyimpan API key: "+ex.getMessage());}}).setNegativeButton("Hapus",(x,w)->{keyStore.clear();setStatus("API key dihapus dari perangkat.");}).setNeutralButton("Batal",null).create();d.show();
    }

    private void toggleVoice(){
        if(!voiceMode){
            if(Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
            voiceMode=true;mic.setText("STOP MIC");refreshState();setStatus("🎙 LUNA siap mendengarkan…");listen();
        }else{voiceMode=false;mic.setText("MIC");if(recognizer!=null)recognizer.cancel();setStatus("Mode suara dihentikan.");refreshState();}
    }

    private void listen(){
        if(!voiceMode)return;
        if(recognizer==null){setStatus("Speech recognition tidak tersedia di perangkat ini.");voiceMode=false;refreshState();return;}
        if(Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        try{
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"id-ID");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"id-ID");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
            recognizer.setRecognitionListener(new android.speech.RecognitionListener(){
                public void onReadyForSpeech(Bundle p){setStatus("🎙 Mendengarkan…");} public void onBeginningOfSpeech(){setStatus("🎙 Saya dengar…");} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){setStatus("🧠 Memahami perintah…");}
                public void onError(int e){if(!voiceMode)return;String m=e==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Izin mikrofon ditolak.":e==SpeechRecognizer.ERROR_NO_MATCH?"Tidak terdengar jelas. Coba lagi.":e==SpeechRecognizer.ERROR_NETWORK?"Koneksi voice bermasalah. Coba cek internet.":"Voice error: "+e;setStatus(m);if(e!=SpeechRecognizer.ERROR_CLIENT)new Handler(Looper.getMainLooper()).postDelayed(()->listen(),700);}
                public void onResults(Bundle r){ArrayList<String>a=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()&&a.get(0)!=null&&!a.get(0).trim().isEmpty()){String heard=a.get(0).trim();command.setText(heard);runCommand(heard);}else if(voiceMode)listen();}
                public void onPartialResults(Bundle r){} public void onEvent(int t,Bundle p){}
            });recognizer.startListening(i);
        }catch(Throwable t){setStatus("Mic gagal dimulai: "+t.getMessage());}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQ_MIC){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){voiceMode=true;mic.setText("STOP MIC");refreshState();listen();}else{voiceMode=false;mic.setText("MIC");setStatus("Izin mikrofon belum diberikan.");}}}

    private void runCommand(String text){
        if(text==null||text.trim().isEmpty()){setStatus("Tulis atau ucapkan perintah terlebih dahulu.");return;}
        if(running){setStatus("LUNA masih bekerja. Tekan STOP jika ingin membatalkan.");return;}
        LunaAccessibilityService svc=LunaAccessibilityService.get();if(svc==null){setStatus("⚠ Aktifkan Accessibility Service LUNA terlebih dahulu.");return;}
        final String cmd=text.trim();running=true;send.setEnabled(false);svc.resumeNow();setStatus("🧠 Memahami tujuan…\n👁 Mengamati layar…\n⚙ Menyiapkan tindakan…");
        worker.submit(()->{try{LunaAgent agent=new LunaAgent(this,svc,keyStore,new LunaAgent.Callback(){public void status(String t){setStatus(t);}public void speak(String t){speak(t);}public boolean confirm(JSONObject p){return confirmAndWait(p);}public void finished(){if(voiceMode)new Handler(Looper.getMainLooper()).postDelayed(()->listen(),450);}});agent.run(cmd);}finally{running=false;runOnUiThread(()->send.setEnabled(true));}});
    }

    private boolean confirmAndWait(JSONObject plan){CountDownLatch latch=new CountDownLatch(1);AtomicBoolean approved=new AtomicBoolean(false);runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Konfirmasi tindakan").setMessage(plan.optString("speak","LUNA meminta izin melakukan tindakan sensitif.")).setNegativeButton("Batal",(d,w)->{approved.set(false);latch.countDown();}).setPositiveButton("Lanjutkan",(d,w)->{approved.set(true);latch.countDown();}).setOnCancelListener(d->latch.countDown()).show());try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}return approved.get()&&running;}

    private void stopAll(){running=false;voiceMode=false;if(recognizer!=null)recognizer.cancel();LunaAccessibilityService s=LunaAccessibilityService.get();if(s!=null)s.stopNow();setStatus("■ STOP — LUNA dihentikan.");runOnUiThread(()->{send.setEnabled(true);mic.setText("MIC");refreshState();});}
    private void speak(String t){if(t==null||t.trim().isEmpty())return;setStatus("🗣 LUNA: "+t);runOnUiThread(()->{if(tts!=null)tts.speak(t,TextToSpeech.QUEUE_FLUSH,null,"LUNA");});}
    private void setStatus(String s){runOnUiThread(()->{status.setText(s);});}
    @Override protected void onDestroy(){running=false;voiceMode=false;if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();worker.shutdownNow();super.onDestroy();}
}
