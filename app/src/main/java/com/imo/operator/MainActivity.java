package com.imo.operator;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class MainActivity extends Activity {
    private EditText command, status;
    private TextView state;
    private Button mic, send, stop, key, access;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private SecureKeyStore keyStore;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running;

    @Override public void onCreate(Bundle b) { super.onCreate(b); keyStore=new SecureKeyStore(this); buildUi();
        tts=new TextToSpeech(this, s -> { if(s==TextToSpeech.SUCCESS) tts.setLanguage(new Locale("id","ID")); });
        if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer=SpeechRecognizer.createSpeechRecognizer(this);
    }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,24,28,20);
        TextView title=new TextView(this); title.setText("LUNA\nAI ANDROID DEVICE CONTROL"); title.setTextSize(24); title.setPadding(0,0,0,14); root.addView(title);
        state=new TextView(this); state.setText("● Siap — operator belum aktif"); state.setTextSize(16); root.addView(state);
        status=new EditText(this); status.setHint("Status / hasil eksekusi"); status.setMinLines(5); status.setGravity(Gravity.TOP); status.setEnabled(false); root.addView(status,new LinearLayout.LayoutParams(-1,0,1));
        command=new EditText(this); command.setHint("Katakan/perintahkan: buka WhatsApp, cari Budi..."); command.setSingleLine(false); root.addView(command,new LinearLayout.LayoutParams(-1,120));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        send=button("JALANKAN"); mic=button("🎙 MIC"); stop=button("■ STOP"); row.addView(send,new LinearLayout.LayoutParams(0,60,2)); row.addView(mic,new LinearLayout.LayoutParams(0,60,1)); row.addView(stop,new LinearLayout.LayoutParams(0,60,1)); root.addView(row);
        LinearLayout row2=new LinearLayout(this); access=button("AKTIFKAN ACCESSIBILITY"); key=button("API KEY"); row2.addView(access,new LinearLayout.LayoutParams(0,60,2)); row2.addView(key,new LinearLayout.LayoutParams(0,60,1)); root.addView(row2);
        setContentView(root);
        send.setOnClickListener(v->runCommand(command.getText().toString())); stop.setOnClickListener(v->stopAll()); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); key.setOnClickListener(v->showKeyDialog()); mic.setOnClickListener(v->listen());
        refreshState();
    }
    private Button button(String s){ Button b=new Button(this); b.setText(s); return b; }
    private void refreshState(){ boolean on=LunaAccessibilityService.get()!=null; state.setText(on?"● Operator Android AKTIF":"● Accessibility belum aktif"); }
    @Override protected void onResume(){super.onResume();refreshState();}
    private void showKeyDialog(){
        EditText e=new EditText(this); e.setHint("sk-… (jangan kirim ke chat)"); e.setInputType(129);
        try { String old=keyStore.load(); if(old!=null&&!old.isEmpty()) e.setText(old); } catch(Exception ignored){}
        new AlertDialog.Builder(this).setTitle("OpenAI API Key").setMessage("Kunci disimpan terenkripsi di perangkat. Jangan commit atau membagikannya. Untuk APK produksi, gunakan backend.").setView(e)
            .setPositiveButton("Simpan",(d,w)->{try{keyStore.save(e.getText().toString().trim()); setStatus("API key tersimpan lokal.");}catch(Exception ex){setStatus("Gagal menyimpan key: "+ex.getMessage());}})
            .setNegativeButton("Hapus",(d,w)->keyStore.clear()).setNeutralButton("Batal",null).show();
    }
    private void listen(){
        if(recognizer==null){setStatus("Speech recognition tidak tersedia.");return;}
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"id-ID"); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizer.setRecognitionListener(new android.speech.RecognitionListener(){
            public void onReadyForSpeech(Bundle p){setStatus("Mendengarkan…");} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){}
            public void onError(int e){setStatus("Voice error: "+e);} public void onResults(Bundle r){ArrayList<String> a=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){command.setText(a.get(0));runCommand(a.get(0));}}
            public void onPartialResults(Bundle r){} public void onEvent(int t,Bundle p){}
        }); recognizer.startListening(i);
    }
    private void runCommand(String text){
        if(text==null||text.trim().isEmpty()) return; if(running){setStatus("Luna masih menjalankan tugas. Tekan STOP bila ingin menghentikan.");return;}
        final LunaAccessibilityService svc=LunaAccessibilityService.get(); if(svc==null){setStatus("Aktifkan Accessibility Service LUNA terlebih dahulu.");return;}
        final String cmd=text.trim(); running=true; send.setEnabled(false); setStatus("Luna mengamati layar dan menyusun rencana…");
        worker.submit(()->{
            try{
                String api=keyStore.load(); OpenAIClient ai=new OpenAIClient(api,"gpt-5.6-luna"); String plan=ai.plan(cmd,svc.snapshot());
                JSONObject p=new JSONObject(plan); boolean confirm=p.optBoolean("confirm",false); String speak=p.optString("speak","");
                runOnUiThread(()->{ if(!speak.isEmpty()) say(speak); });
                if(confirm){ runOnUiThread(()->confirmAndExecute(p)); }
                else executePlan(p);
            }catch(Exception e){runOnUiThread(()->setStatus("Gagal: "+e.getMessage()));}
            finally{if(!running){ } else {running=false;runOnUiThread(()->send.setEnabled(true));}}
        });
    }
    private void confirmAndExecute(JSONObject p){ new AlertDialog.Builder(this).setTitle("Konfirmasi tindakan Luna").setMessage(p.optString("speak","Luna akan melakukan tindakan pada perangkat."))
        .setNegativeButton("Batal",(d,w)->{running=false;send.setEnabled(true);}).setPositiveButton("Lanjutkan",(d,w)->worker.submit(()->executePlan(p))).show(); }
    private void executePlan(JSONObject p){
        LunaAccessibilityService svc=LunaAccessibilityService.get(); if(svc==null){running=false;return;} svc.resumeNow(); JSONArray a=p.optJSONArray("actions"); if(a==null){running=false;return;}
        DeviceExecutor device=new DeviceExecutor(this);
        for(int i=0;i<a.length()&&running&&!svc.isStopped();i++){
            try{JSONObject x=a.getJSONObject(i);String type=x.optString("type");boolean ok=false;
                if("OPEN_APP".equals(type)) ok=device.openApp(x.optString("package"),x.optString("label")); else if("CLICK_TEXT".equals(type)) ok=svc.clickText(x.optString("value")); else if("CLICK_DESC".equals(type)) ok=svc.clickDescription(x.optString("value")); else if("TYPE".equals(type)) ok=svc.typeText(x.optString("value")); else if("SCROLL".equals(type)) ok=svc.scroll(x.optString("direction")); else if("BACK".equals(type)) ok=svc.globalBack(); else if("HOME".equals(type)) ok=svc.globalHome(); else if("RECENTS".equals(type)) ok=svc.globalRecents(); else if("OPEN_URL".equals(type)) ok=device.openUrl(x.optString("value")); else if("WAIT".equals(type)){device.delay(x.optLong("delayMs",500));ok=true;}
                setStatus("Langkah "+(i+1)+": "+type+" → "+(ok?"berhasil":"gagal")); device.delay(Math.max(120,x.optLong("delayMs",250)));
            }catch(Exception e){setStatus("Langkah gagal: "+e.getMessage());}
        }
        if(running) setStatus("Tugas selesai. Luna memverifikasi keadaan layar."); running=false; runOnUiThread(()->send.setEnabled(true));
    }
    private void stopAll(){running=false;LunaAccessibilityService s=LunaAccessibilityService.get();if(s!=null)s.stopNow();setStatus("STOP — eksekusi Luna dihentikan.");send.setEnabled(true);}
    private void say(String s){if(tts!=null&&!s.isEmpty())tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"LUNA");setStatus(s);}
    private void setStatus(String s){runOnUiThread(()->status.setText(s));}
    @Override protected void onDestroy(){running=false;if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();worker.shutdownNow();super.onDestroy();}
}
