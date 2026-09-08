package com.imo.operator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private TextView status, chat;
    private Button mic;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private IMOEngine engine;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        tts = new TextToSpeech(this, this);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) { status.setText("Mendengarkan…"); }
            public void onBeginningOfSpeech() { status.setText("Saya mendengar, ji…"); }
            public void onRmsChanged(float v) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { mic.setText("🎙 MULAI BICARA"); }
            public void onError(int e) { mic.setText("🎙 MULAI BICARA"); status.setText("Tidak menangkap suara. Coba lagi."); }
            public void onResults(Bundle r) {
                ArrayList<String> a = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (a != null && !a.isEmpty()) command(a.get(0));
            }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int t, Bundle b) {}
        });
    }

    @Override protected void onResume() {
        super.onResume();
        engine = new IMOEngine(IMOAccessibilityService.instance);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 40, 32, 30);
        TextView title = new TextView(this); title.setText("IMO"); title.setTextSize(34); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView subtitle = new TextView(this); subtitle.setText("Intelligent Mobile Operator"); subtitle.setGravity(Gravity.CENTER); root.addView(subtitle);
        status = new TextView(this); status.setText("Siap. Saya menunggu perintah ji."); status.setTextSize(17); status.setPadding(0, 25, 0, 20); root.addView(status);
        chat = new TextView(this); chat.setText("IMO: Siap membantu."); chat.setTextSize(18); root.addView(chat);
        mic = new Button(this); mic.setText("🎙 MULAI BICARA"); mic.setOnClickListener(v -> listen()); root.addView(mic);
        Button accessibility = new Button(this); accessibility.setText("⚙ AKTIFKAN KENDALI HP"); accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(accessibility);
        setContentView(root);
    }

    private void listen() {
        if (speechRecognizer == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        mic.setText("⏹ MENDENGARKAN…");
        speechRecognizer.startListening(intent);
    }

    private void command(String input) {
        final String clean = input == null ? "" : input.trim();
        if (clean.isEmpty()) return;
        chat.setText("Anda: " + clean + "\nIMO: Saya memahami perintahnya dan mulai bekerja…");
        status.setText("Memproses…");
        speak("Baik, ji. Saya kerjakan.");

        engine = new IMOEngine(IMOAccessibilityService.instance);
        engine.execute(clean, new IMOEngine.Callback() {
            @Override public void onProgress(String message) {
                runOnUiThread(() -> status.setText(message));
            }
            @Override public void onFinished(String message, boolean success) {
                runOnUiThread(() -> {
                    status.setText(success ? "Selesai ✓" : "Belum selesai");
                    chat.setText("Anda: " + clean + "\nIMO: " + message);
                    speak(message);
                });
            }
        });
    }

    private void speak(String text) {
        if (tts != null && text != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "imo");
    }

    @Override public void onInit(int result) {
        if (result == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("id", "ID"));
            tts.setSpeechRate(.95f);
        }
    }

    @Override protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
