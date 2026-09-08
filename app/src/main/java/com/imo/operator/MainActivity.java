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

/** IMO operator UI: Indonesian voice I/O, task execution, secure voice enrollment and local memory. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private TextView status, chat, voiceStatus;
    private Button mic;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private IMOEngine engine;
    private IMOConfirmation confirmation;
    private IMOMemory memory;
    private IMOVoiceIdentity voiceIdentity;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); buildUi();
        memory = new IMOMemory(this);
        confirmation = new IMOConfirmation();
        voiceIdentity = new IMOVoiceIdentity(this);
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
        refreshVoiceStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (confirmation == null) confirmation = new IMOConfirmation();
        engine = new IMOEngine(IMOAccessibilityService.instance, confirmation);
        refreshVoiceStatus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,40,32,30);
        TextView title = new TextView(this); title.setText("IMO"); title.setTextSize(34); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView subtitle = new TextView(this); subtitle.setText("Intelligent Mobile Operator"); subtitle.setGravity(Gravity.CENTER); root.addView(subtitle);
        status = new TextView(this); status.setText("Siap. Saya menunggu perintah ji."); status.setTextSize(17); status.setPadding(0,25,0,20); root.addView(status);
        chat = new TextView(this); chat.setText("IMO: Siap membantu."); chat.setTextSize(18); root.addView(chat);
        voiceStatus = new TextView(this); voiceStatus.setTextSize(15); voiceStatus.setPadding(0,15,0,10); root.addView(voiceStatus);
        mic = new Button(this); mic.setText("🎙 MULAI BICARA"); mic.setOnClickListener(v -> listen()); root.addView(mic);
        Button enroll = new Button(this); enroll.setText("🔐 DAFTARKAN SUARA SAYA"); enroll.setOnClickListener(v -> enrollVoice()); root.addView(enroll);
        Button accessibility = new Button(this); accessibility.setText("⚙ AKTIFKAN KENDALI HP"); accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(accessibility);
        setContentView(root);
    }

    private void refreshVoiceStatus() {
        if (voiceStatus == null || voiceIdentity == null) return;
        voiceStatus.setText(voiceIdentity.isEnrolled()
                ? "🔐 Voiceprint: TERDAFTAR — tersimpan terenkripsi di perangkat"
                : "🔒 Voiceprint: BELUM TERDAFTAR — voice lock belum aktif");
    }

    private void enrollVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10); return;
        }
        if (speechRecognizer != null) speechRecognizer.cancel();
        status.setText("Menyiapkan pendaftaran suara…");
        new Thread(() -> {
            try {
                IMOSherpaSpeakerEncoder encoder = new IMOSherpaSpeakerEncoder(this);
                IMOVoiceEnrollment enrollment = new IMOVoiceEnrollment(encoder, voiceIdentity);
                enrollment.enroll(3, 2500, new IMOVoiceEnrollment.Callback() {
                    @Override public void onProgress(String message) { runOnUiThread(() -> { status.setText(message); speak(message); }); }
                    @Override public void onFinished(boolean success, String message) {
                        encoder.release();
                        runOnUiThread(() -> { status.setText(success ? "Voiceprint siap ✓" : "Pendaftaran gagal"); chat.setText("IMO: " + message); refreshVoiceStatus(); speak(message); });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { status.setText("Model suara belum siap"); chat.setText("IMO: Enrollment gagal: " + e.getMessage()); speak("Pendaftaran suara gagal."); });
            }
        }, "IMO-Voice-Setup").start();
    }

    private void listen() {
        if (speechRecognizer == null) return;
        if (!voiceIdentity.isEnrolled()) {
            status.setText("Voiceprint belum terdaftar. Daftarkan suara terlebih dahulu.");
            speak("Silakan daftarkan suara Anda terlebih dahulu."); return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        mic.setText("⏹ MENDENGARKAN…"); speechRecognizer.startListening(intent);
    }

    private void command(String input) {
        final String clean = input == null ? "" : input.trim(); if (clean.isEmpty()) return;
        chat.setText("Anda: " + clean + "\nIMO: Saya memahami perintahnya dan mulai bekerja…"); status.setText("Memproses…");
        engine = new IMOEngine(IMOAccessibilityService.instance, confirmation);
        engine.execute(clean, new IMOEngine.Callback() {
            @Override public void onProgress(String message) { runOnUiThread(() -> status.setText(message)); }
            @Override public void onConfirmationRequired(String message) {
                memory.remember(clean, message);
                runOnUiThread(() -> { status.setText("Menunggu konfirmasi"); chat.setText("Anda: " + clean + "\nIMO: " + message); speak(message); });
            }
            @Override public void onFinished(String message, boolean success) {
                memory.remember(clean, message);
                runOnUiThread(() -> { status.setText(success ? "Selesai ✓" : "Belum selesai"); chat.setText("Anda: " + clean + "\nIMO: " + message); speak(message); });
            }
        });
    }

    private void speak(String text) { if (tts != null && text != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "imo"); }
    @Override public void onInit(int result) { if (result == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("id","ID")); tts.setSpeechRate(.95f); } }
    @Override protected void onDestroy() { if (speechRecognizer != null) speechRecognizer.destroy(); if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
}
