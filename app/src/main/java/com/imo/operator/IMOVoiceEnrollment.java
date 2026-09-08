package com.imo.operator;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** User-friendly multi-sample local voice enrollment with basic audio quality checks. */
public final class IMOVoiceEnrollment {
    public interface Callback {
        void onProgress(String message);
        void onFinished(boolean success, String message);
    }

    private static final int MIN_SAMPLES = 3;
    private static final long TTS_GUARD_MS = 1200L;
    private static final double MIN_RMS = 0.008;
    private final IMOSpeakerEncoder encoder;
    private final IMOVoiceIdentity identity;
    private final Handler main = new Handler(Looper.getMainLooper());

    public IMOVoiceEnrollment(IMOSpeakerEncoder encoder, IMOVoiceIdentity identity) {
        this.encoder = encoder;
        this.identity = identity;
    }

    public void enroll(int sampleCount, long clipMs, Callback callback) {
        if (encoder == null) {
            callback.onFinished(false, "Model verifikasi suara belum tersedia. IMO tidak mengaktifkan voice lock secara palsu.");
            return;
        }
        if (sampleCount < MIN_SAMPLES || clipMs < 2000) {
            callback.onFinished(false, "Parameter pendaftaran suara terlalu kecil.");
            return;
        }
        new Thread(() -> {
            try {
                List<float[]> embeddings = new ArrayList<>();
                for (int i = 0; i < sampleCount; i++) {
                    int number = i + 1;
                    post(callback, "Silakan bicara secara alami — sampel " + number + " dari " + sampleCount + ".");
                    // Prevent IMO's TTS prompt from leaking into the microphone capture.
                    Thread.sleep(TTS_GUARD_MS);
                    short[] pcm = IMOAudioRecorder.record(clipMs);
                    if (!hasUsableVoice(pcm)) {
                        post(callback, "Suara belum cukup jelas. Kita ulangi sampel " + number + ".");
                        i--;
                        continue;
                    }
                    float[] embedding = encoder.embed(trimSilence(pcm), IMOAudioRecorder.SAMPLE_RATE);
                    if (embedding == null || embedding.length < 8)
                        throw new IllegalStateException("Encoder menghasilkan voice embedding yang tidak valid");
                    embeddings.add(normalize(embedding));
                }
                float[] profile = normalize(average(embeddings));
                identity.enroll(profile);
                postFinish(callback, true, "Berhasil. Suara Anda sudah terdaftar dan disimpan secara aman di perangkat.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postFinish(callback, false, "Pendaftaran suara dihentikan.");
            } catch (Exception e) {
                postFinish(callback, false, "Pendaftaran suara belum berhasil: " + safe(e.getMessage()) + ". Silakan coba lagi di tempat yang lebih tenang.");
            }
        }, "IMO-Voice-Enrollment").start();
    }

    private static boolean hasUsableVoice(short[] pcm) {
        if (pcm == null || pcm.length < 32000) return false;
        double sum = 0;
        for (short sample : pcm) {
            double x = sample / 32768.0;
            sum += x * x;
        }
        return Math.sqrt(sum / pcm.length) >= MIN_RMS;
    }

    private static short[] trimSilence(short[] pcm) {
        if (pcm == null || pcm.length == 0) return pcm;
        double threshold = 0.012;
        int first = 0, last = pcm.length - 1;
        while (first < pcm.length && Math.abs(pcm[first] / 32768.0) < threshold) first++;
        while (last > first && Math.abs(pcm[last] / 32768.0) < threshold) last--;
        int padding = IMOAudioRecorder.SAMPLE_RATE / 10;
        first = Math.max(0, first - padding);
        last = Math.min(pcm.length - 1, last + padding);
        short[] out = new short[last - first + 1];
        System.arraycopy(pcm, first, out, 0, out.length);
        return out.length >= 16000 ? out : pcm;
    }

    private static float[] average(List<float[]> values) {
        int n = values.get(0).length;
        float[] out = new float[n];
        for (float[] v : values) {
            if (v.length != n) throw new IllegalArgumentException("Embedding dimensions differ");
            for (int i = 0; i < n; i++) out[i] += v[i];
        }
        for (int i = 0; i < n; i++) out[i] /= values.size();
        return out;
    }

    private static float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm <= 1e-9) throw new IllegalArgumentException("Voice embedding has zero magnitude");
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    private void post(Callback cb, String msg) { main.post(() -> cb.onProgress(msg)); }
    private void postFinish(Callback cb, boolean ok, String msg) { main.post(() -> cb.onFinished(ok, msg)); }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "kesalahan tidak diketahui" : s; }
}
