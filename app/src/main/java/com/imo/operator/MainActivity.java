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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); buildUi();
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
            public void onResults(Bundle r) { ArrayList<String> a = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (a != null && !a.isEmpty()) command(a.get(0)); }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int t, Bundle b) {}
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32, 40, 32, 30);
        TextView title = new TextView(this); title.setText("IMO"); title.setTextSize(34); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView subtitle = new TextView(this); subtitle.setText("Intelligent Mobile Operator"); subtitle.setGravity(Gravity.CENTER); root.addView(subtitle);
        status = new TextView(this); status.setText("Siap. Saya menunggu perintah ji."); status.setTextSize(17); status.setPadding(0, 25, 0, 20); root.addView(status);
        chat = new TextView(this); chat.setText("IMO: Siap membantu."); chat.setTextSize(18); root.addView(chat);
        mic = new Button(this); mic.setText("🎙 MULAI BICARA"); mic.setOnClickListener(v -> listen()); root.addView(mic);
        Button accessibility = new Button(this); accessibility.setText("⚙ AKTIFKAN KENDALI HP"); accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(accessibility);
        setContentView(root);
    }

    private void listen() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        mic.setText("⏹ BERHENTI"); speechRecognizer.startListening(intent);
    }

    private void command(String input) {
        String q = input.toLowerCase(Locale.ROOT); String reply;
        IMOAccessibilityService service = IMOAccessibilityService.instance;
        if (q.contains("buka whatsapp")) {
            reply = "Baik, ji. Saya buka WhatsApp.";
            if (service != null) service.openApp("com.whatsapp"); else status.setText("Aktifkan Accessibility IMO terlebih dahulu.");
        } else if (q.startsWith("klik ")) {
            String value = input.substring(5).trim(); boolean ok = service != null && service.clickText(value);
            reply = ok ? "Baik, saya klik " + value + "." : "Saya belum menemukan " + value + ".";
        } else if (q.startsWith("ketik ")) {
            String value = input.substring(6).trim(); boolean ok = service != null && service.typeText(value);
            reply = ok ? "Baik, saya mengetikkan teksnya." : "Saya belum menemukan kolom teks.";
        } else if (q.contains("baca layar")) {
            reply = service == null ? "Accessibility belum aktif." : service.readScreen();
        } else if (q.equals("kembali") || q.equals("back")) {
            reply = "Baik, saya kembali."; if (service != null) service.goBack();
        } else if (q.contains("home") || q.contains("halaman utama")) {
            reply = "Baik, saya ke halaman utama."; if (service != null) service.goHome();
        } else if (q.contains("halo") || q.contains("hai")) {
            reply = "Baik, ji. Saya IMO. Saya siap membantu.";
        } else {
            reply = "Saya mendengar: " + input + ". Fondasi IMO aktif; berikutnya kita pasang otak AI dan loop observe-think-act-verify.";
        }
        chat.setText("IMO: " + reply);
        if (tts != null) tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "imo");
    }

    @Override public void onInit(int result) { if (result == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("id", "ID")); tts.setSpeechRate(.95f); } }
    @Override protected void onDestroy() { if (speechRecognizer != null) speechRecognizer.destroy(); if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
}
