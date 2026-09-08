package com.imo.operator;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** Multi-sample voice enrollment. Fails closed when the real encoder is unavailable. */
public final class IMOVoiceEnrollment {
    public interface Callback {
        void onProgress(String message);
        void onFinished(boolean success, String message);
    }

    private final IMOSpeakerEncoder encoder;
    private final IMOVoiceIdentity identity;
    private final Handler main = new Handler(Looper.getMainLooper());

    public IMOVoiceEnrollment(IMOSpeakerEncoder encoder, IMOVoiceIdentity identity) {
        this.encoder = encoder;
        this.identity = identity;
    }

    public void enroll(int sampleCount, long clipMs, Callback callback) {
        if (encoder == null) { callback.onFinished(false, "Model verifikasi suara belum tersedia. IMO tidak mengaktifkan voice lock secara palsu."); return; }
        if (sampleCount < 3 || clipMs < 1000) { callback.onFinished(false, "Parameter enrollment terlalu kecil."); return; }
        new Thread(() -> {
            try {
                List<float[]> embeddings = new ArrayList<>();
                for (int i = 0; i < sampleCount; i++) {
                    int number = i + 1;
                    post(callback, "Rekam sampel suara " + number + " dari " + sampleCount + "…");
                    short[] pcm = IMOAudioRecorder.record(clipMs);
                    float[] embedding = encoder.embed(pcm, IMOAudioRecorder.SAMPLE_RATE);
                    if (embedding == null || embedding.length < 8) throw new IllegalStateException("Encoder menghasilkan voice embedding yang tidak valid");
                    embeddings.add(embedding);
                }
                float[] profile = average(embeddings);
                identity.enroll(profile);
                postFinish(callback, true, "Voiceprint berhasil didaftarkan secara lokal.");
            } catch (Exception e) {
                postFinish(callback, false, "Enrollment gagal: " + safe(e.getMessage()));
            }
        }, "IMO-Voice-Enrollment").start();
    }

    private static float[] average(List<float[]> values) {
        int n = values.get(0).length; float[] out = new float[n];
        for (float[] v : values) { if (v.length != n) throw new IllegalArgumentException("Embedding dimensions differ"); for (int i = 0; i < n; i++) out[i] += v[i]; }
        for (int i = 0; i < n; i++) out[i] /= values.size();
        return out;
    }
    private void post(Callback cb, String msg) { main.post(() -> cb.onProgress(msg)); }
    private void postFinish(Callback cb, boolean ok, String msg) { main.post(() -> cb.onFinished(ok, msg)); }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "kesalahan tidak diketahui" : s; }
}
