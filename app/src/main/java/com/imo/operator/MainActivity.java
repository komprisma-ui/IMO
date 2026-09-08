package com.imo.operator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** IMO operator UI: local speaker lock, local ASR, task execution, confirmation and memory. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private TextView status, chat, voiceStatus;
    private Button mic;
    private TextToSpeech tts;
    private IMOEngine engine;
    private IMOConfirmation confirmation;
    private IMOMemory memory;
    private IMOVoiceIdentity voiceIdentity;
    private volatile boolean voiceBusy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); buildUi();
        memory = new IMOMemory(this); confirmation = new IMOConfirmation(); voiceIdentity = new IMOVoiceIdentity(this);
        tts = new TextToSpeech(this, this);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        refreshVoiceStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (confirmation == null) confirmation = new IMOConfirmation();
        engine = new IMOEngine(IMOAccessibilityService.instance, confirmation); refreshVoiceStatus();
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
        voiceStatus.setText(voiceIdentity.isEnrolled() ? "🔐 Voiceprint: TERDAFTAR — terenkripsi di perangkat" : "🔒 Voiceprint: BELUM TERDAFTAR — voice lock belum aktif");
    }

    private void enrollVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10); return; }
        status.setText("Siap. Ikuti instruksi dengan suara alami.");
        speak("Baik. Mari kita daftarkan suara Anda. Setelah saya selesai berbicara, silakan ucapkan secara alami.");
        new Thread(() -> {
            try {
                Thread.sleep(1200);
                IMOSherpaSpeakerEncoder encoder = new IMOSherpaSpeakerEncoder(this);
                IMOVoiceEnrollment enrollment = new IMOVoiceEnrollment(encoder, voiceIdentity);
                enrollment.enroll(3, 4000, new IMOVoiceEnrollment.Callback() {
                    @Override public void onProgress(String message) { runOnUiThread(() -> { status.setText(message); speak(message); }); }
                    @Override public void onFinished(boolean success, String message) { encoder.release(); runOnUiThread(() -> { status.setText(success ? "Voiceprint siap ✓" : "Pendaftaran suara perlu diulang"); chat.setText("IMO: " + message); refreshVoiceStatus(); speak(message); }); }
                });
            } catch (Exception e) { runOnUiThread(() -> { status.setText("Pendaftaran suara perlu diulang"); chat.setText("IMO: Enrollment gagal: " + safe(e.getMessage())); speak("Pendaftaran suara perlu diulang. Silakan coba sekali lagi."); }); }
        }, "IMO-Voice-Setup").start();
    }

    /** One capture: VAD -> speaker verification -> same PCM -> local multilingual ASR. */
    private void listen() {
        if (voiceBusy) return;
        if (!voiceIdentity.isEnrolled()) { status.setText("Voiceprint belum terdaftar. Daftarkan suara terlebih dahulu."); speak("Silakan daftarkan suara Anda terlebih dahulu."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10); return; }
        voiceBusy = true; mic.setText("🔐 MENDENGARKAN & MEMERIKSA…");
        IMOVoiceCommandPipeline pipeline = new IMOVoiceCommandPipeline(this, voiceIdentity, 0.72f);
        boolean started;
        try { started = pipeline.start(5000, new IMOVoiceCommandPipeline.Callback() {
            @Override public void onState(String message) { runOnUiThread(() -> status.setText(message)); }
            @Override public void onAccepted(short[] pcm16, int sampleRateHz) { IMOLocalAsr asr = null; try { runOnUiThread(() -> status.setText("Suara cocok ✓. Memahami perintah secara offline…")); asr = new IMOLocalAsr(MainActivity.this); String text = asr.transcribe(pcm16, sampleRateHz); if (text.isEmpty()) runOnUiThread(() -> { status.setText("Ucapan belum terbaca."); chat.setText("IMO: Saya belum menangkap perintahnya."); speak("Saya belum menangkap perintahnya."); }); else runOnUiThread(() -> command(text)); } catch (Exception e) { runOnUiThread(() -> { status.setText("ASR lokal gagal."); chat.setText("IMO: Tidak dapat memahami suara: " + safe(e.getMessage())); speak("Saya belum dapat memahami ucapan itu."); }); } finally { if (asr != null) asr.release(); voiceBusy = false; runOnUiThread(() -> mic.setText("🎙 MULAI BICARA")); } }
            @Override public void onRejected(String message) { voiceBusy = false; runOnUiThread(() -> { mic.setText("🎙 MULAI BICARA"); status.setText("Akses suara ditolak."); chat.setText("IMO: " + message); speak(message); }); }
            @Override public void onError(String message) { voiceBusy = false; runOnUiThread(() -> { mic.setText("🎙 MULAI BICARA"); status.setText("Pipeline suara berhenti aman."); chat.setText("IMO: " + message); }); }
        }); } catch (Exception e) { started = false; }
        if (!started) { voiceBusy = false; mic.setText("🎙 MULAI BICARA"); status.setText("Tidak dapat memulai pipeline suara."); }
    }

    private void command(String input) {
        final String clean = input == null ? "" : input.trim(); if (clean.isEmpty()) return;
        chat.setText("Anda: " + clean + "\nIMO: Saya memahami perintahnya dan mulai bekerja…"); status.setText("Memproses…");
        engine = new IMOEngine(IMOAccessibilityService.instance, confirmation);
        engine.execute(clean, new IMOEngine.Callback() {
            @Override public void onProgress(String message) { runOnUiThread(() -> status.setText(message)); }
            @Override public void onConfirmationRequired(String message) { memory.remember(clean, message); runOnUiThread(() -> { status.setText("Menunggu konfirmasi"); chat.setText("Anda: " + clean + "\nIMO: " + message); speak(message); }); }
            @Override public void onFinished(String message, boolean success) { memory.remember(clean, message); runOnUiThread(() -> { status.setText(success ? "Selesai ✓" : "Belum selesai"); chat.setText("Anda: " + clean + "\nIMO: " + message); speak(message); }); }
        });
    }

    private void speak(String text) { if (tts != null && text != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "imo"); }
    @Override public void onInit(int result) { if (result == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("id","ID")); tts.setSpeechRate(.88f); tts.setPitch(.78f); } }
    @Override protected void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "kesalahan tidak diketahui" : s; }
}
