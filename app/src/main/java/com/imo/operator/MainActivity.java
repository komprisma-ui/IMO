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
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private EditText command, status;
    private TextView state;
    private Button mic, send, stop, key, access;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private SecureKeyStore keyStore;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        keyStore = new SecureKeyStore(this);
        buildUi();
        tts = new TextToSpeech(this, s -> { if (s == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("id", "ID")); });
        if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer = SpeechRecognizer.createSpeechRecognizer(this);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 24, 28, 20);
        TextView title = new TextView(this);
        title.setText("LUNA\nAI ANDROID DEVICE OPERATOR");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 14);
        root.addView(title);
        state = new TextView(this);
        state.setText("● Siap — operator belum aktif");
        state.setTextSize(16);
        root.addView(state);
        status = new EditText(this);
        status.setHint("Status / hasil eksekusi");
        status.setMinLines(5);
        status.setGravity(Gravity.TOP);
        status.setEnabled(false);
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
        command = new EditText(this);
        command.setHint("Perintah: buka WhatsApp, cari Budi, lalu buka chatnya");
        command.setSingleLine(false);
        root.addView(command, new LinearLayout.LayoutParams(-1, 120));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        send = button("JALANKAN"); mic = button("🎙 MIC"); stop = button("■ STOP");
        row.addView(send, new LinearLayout.LayoutParams(0, 60, 2));
        row.addView(mic, new LinearLayout.LayoutParams(0, 60, 1));
        row.addView(stop, new LinearLayout.LayoutParams(0, 60, 1));
        root.addView(row);
        LinearLayout row2 = new LinearLayout(this);
        access = button("AKTIFKAN ACCESSIBILITY"); key = button("API KEY");
        row2.addView(access, new LinearLayout.LayoutParams(0, 60, 2));
        row2.addView(key, new LinearLayout.LayoutParams(0, 60, 1));
        root.addView(row2);
        setContentView(root);
        send.setOnClickListener(v -> runCommand(command.getText().toString()));
        stop.setOnClickListener(v -> stopAll());
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        key.setOnClickListener(v -> showKeyDialog());
        mic.setOnClickListener(v -> listen());
        refreshState();
    }

    private Button button(String s) { Button b = new Button(this); b.setText(s); return b; }
    private void refreshState() { boolean on = LunaAccessibilityService.get() != null; state.setText(on ? "● Operator Android AKTIF" : "● Accessibility belum aktif"); }
    @Override protected void onResume() { super.onResume(); refreshState(); }

    private void showKeyDialog() {
        EditText e = new EditText(this);
        e.setHint("sk-… (jangan kirim ke chat)");
        e.setInputType(129);
        try { String old = keyStore.load(); if (!old.isEmpty()) e.setText(old); } catch (Exception ignored) {}
        new AlertDialog.Builder(this)
                .setTitle("OpenAI API Key")
                .setMessage("Kunci disimpan terenkripsi di perangkat. Jangan commit atau membagikannya. Untuk APK produksi publik, gunakan backend.")
                .setView(e)
                .setPositiveButton("Simpan", (d,w) -> { try { keyStore.save(e.getText().toString().trim()); setStatus("API key tersimpan lokal."); } catch(Exception ex) { setStatus("Gagal menyimpan key: " + ex.getMessage()); } })
                .setNegativeButton("Hapus", (d,w) -> keyStore.clear())
                .setNeutralButton("Batal", null).show();
    }

    private void listen() {
        if (recognizer == null) { setStatus("Speech recognition tidak tersedia."); return; }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { setStatus("Mendengarkan…"); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float r) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() {}
            public void onError(int e) { setStatus("Voice error: " + e); }
            public void onResults(Bundle r) { ArrayList<String>a=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(a!=null&&!a.isEmpty()){ command.setText(a.get(0)); runCommand(a.get(0)); } }
            public void onPartialResults(Bundle r) {}
            public void onEvent(int t, Bundle p) {}
        });
        recognizer.startListening(i);
    }

    private void runCommand(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (running) { setStatus("LUNA masih menjalankan tugas. Tekan STOP untuk menghentikan."); return; }
        LunaAccessibilityService svc = LunaAccessibilityService.get();
        if (svc == null) { setStatus("Aktifkan Accessibility Service LUNA terlebih dahulu."); return; }
        final String cmd = text.trim();
        running = true; send.setEnabled(false); svc.resumeNow();
        setStatus("LUNA memulai loop Observe → Think → Act → Verify…");
        worker.submit(() -> {
            try {
                LunaAgent agent = new LunaAgent(this, svc, keyStore, new LunaAgent.Callback() {
                    @Override public void status(String text) { setStatus(text); }
                    @Override public boolean confirm(JSONObject plan) { return confirmAndWait(plan); }
                });
                agent.run(cmd);
            } finally {
                running = false;
                runOnUiThread(() -> send.setEnabled(true));
            }
        });
    }

    private boolean confirmAndWait(JSONObject plan) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean approved = new AtomicBoolean(false);
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Konfirmasi tindakan LUNA")
                .setMessage(plan.optString("speak", "LUNA meminta izin melakukan tindakan sensitif pada perangkat."))
                .setNegativeButton("Batal", (d,w) -> { approved.set(false); latch.countDown(); })
                .setPositiveButton("Lanjutkan", (d,w) -> { approved.set(true); latch.countDown(); })
                .setOnCancelListener(d -> latch.countDown())
                .show());
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        return approved.get() && running;
    }

    private void stopAll() {
        running = false;
        LunaAccessibilityService s = LunaAccessibilityService.get();
        if (s != null) s.stopNow();
        setStatus("STOP — eksekusi LUNA dihentikan sekarang.");
        send.setEnabled(true);
    }

    private void say(String s) { if (tts != null && s != null && !s.isEmpty()) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "LUNA"); setStatus(s); }
    private void setStatus(String s) { runOnUiThread(() -> status.setText(s)); }
    @Override protected void onDestroy() { running=false; if(recognizer!=null)recognizer.destroy(); if(tts!=null)tts.shutdown(); worker.shutdownNow(); super.onDestroy(); }
}
