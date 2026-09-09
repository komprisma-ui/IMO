package com.imo.operator;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** Robust multi-sample local voice enrollment. Raw audio is never persisted. */
public final class IMOVoiceEnrollment {
    public interface Callback {
        void onProgress(String message);
        void onFinished(boolean success, String message);
    }

    private static final int MIN_SAMPLES = 3;
    private static final int MAX_ATTEMPTS_PER_SAMPLE = 3;
    private static final long TTS_GUARD_MS = 1200L;
    private static final double MIN_RMS = 0.0018;
    private static final int MIN_SPEECH_SAMPLES = 4000; // 0.25 s at 16 kHz
    private final IMOSpeakerEncoder encoder;
    private final IMOVoiceIdentity identity;
    private final Handler main = new Handler(Looper.getMainLooper());

    public IMOVoiceEnrollment(IMOSpeakerEncoder encoder, IMOVoiceIdentity identity) {
        this.encoder = encoder;
        this.identity = identity;
    }

    public void enroll(int sampleCount, long clipMs, Callback callback) {
        if (encoder == null) {
            callback.onFinished(false, "Model verifikasi suara belum tersedia. Voice lock tidak diaktifkan secara palsu.");
            return;
        }
        if (sampleCount < MIN_SAMPLES || clipMs < 3000) {
            callback.onFinished(false, "Durasi pendaftaran terlalu pendek. Gunakan minimal 3 detik per sampel.");
            return;
        }
        new Thread(() -> {
            try {
                List<float[]> embeddings = new ArrayList<>();
                for (int i = 0; i < sampleCount; i++) {
                    int number = i + 1;
                    boolean captured = false;
                    String lastDiagnostic = "audio belum terbaca";
                    for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_SAMPLE && !captured; attempt++) {
                        post(callback, "Sampel " + number + " dari " + sampleCount + " — ucapkan kalimat pendek setelah instruksi selesai.");
                        Thread.sleep(TTS_GUARD_MS);
                        short[] pcm;
                        try {
                            pcm = IMOAudioRecorder.record(clipMs);
                        } catch (Exception audioError) {
                            lastDiagnostic = safe(audioError.getMessage());
                            if (attempt < MAX_ATTEMPTS_PER_SAMPLE) {
                                post(callback, "Mikrofon belum siap: " + lastDiagnostic + ". Saya mencoba jalur mikrofon lain…");
                            }
                            continue;
                        }
                        VoiceQuality quality = assess(pcm);
                        if (!quality.usable) {
                            lastDiagnostic = quality.message;
                            if (attempt < MAX_ATTEMPTS_PER_SAMPLE)
                                post(callback, "Sampel " + number + " belum terbaca: " + quality.message + ". Ulangi (" + (attempt + 1) + "/" + MAX_ATTEMPTS_PER_SAMPLE + ").");
                            continue;
                        }
                        short[] speech = trimSilence(pcm, quality.rms);
                        post(callback, "Sampel " + number + " terbaca. Membuat profil suara…");
                        float[] embedding = encoder.embed(speech, IMOAudioRecorder.SAMPLE_RATE);
                        if (embedding == null || embedding.length < 8)
                            throw new IllegalStateException("Encoder menghasilkan voice embedding yang tidak valid");
                        embeddings.add(normalize(embedding));
                        captured = true;
                    }
                    if (!captured) {
                        postFinish(callback, false, "Sampel " + number + " belum dapat dibaca: " + lastDiagnostic + ". Pastikan izin mikrofon aktif, tidak ada panggilan/rekaman lain yang memakai mikrofon, lalu coba lagi.");
                        return;
                    }
                }
                float[] profile = normalize(average(embeddings));
                post(callback, "Semua sampel terbaca. Mengamankan voiceprint di perangkat…");
                identity.enroll(profile);
                float[] stored = identity.load();
                if (stored == null || stored.length != profile.length)
                    throw new IllegalStateException("Voiceprint tersimpan tetapi read-back verification gagal");
                float similarity = IMOVoiceIdentity.cosineSimilarity(profile, stored);
                if (similarity < 0.99f)
                    throw new IllegalStateException("Voiceprint tersimpan tetapi integritas profil tidak cocok");
                postFinish(callback, true, "Berhasil. 3 sampel suara terbaca, voiceprint terenkripsi tersimpan, dan verifikasi penyimpanan berhasil.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postFinish(callback, false, "Pendaftaran suara dihentikan.");
            } catch (Exception e) {
                postFinish(callback, false, "Pendaftaran suara gagal pada tahap penyimpanan/validasi: " + safe(e.getMessage()) + ".");
            }
        }, "IMO-Voice-Enrollment").start();
    }

    private static VoiceQuality assess(short[] pcm) {
        if (pcm == null || pcm.length < 16000) return new VoiceQuality(false, 0, "rekaman terlalu pendek");
        double sum = 0;
        int peak = 0;
        int nonSilent = 0;
        for (short sample : pcm) {
            int a = Math.abs((int) sample);
            peak = Math.max(peak, a);
            double x = sample / 32768.0;
            sum += x * x;
            if (Math.abs(x) >= 0.0025) nonSilent++;
        }
        double rms = Math.sqrt(sum / pcm.length);
        double speechRatio = (double) nonSilent / pcm.length;
        if (peak < 150 || rms < MIN_RMS || nonSilent < MIN_SPEECH_SAMPLES || speechRatio < 0.008)
            return new VoiceQuality(false, rms, "suara terlalu pelan atau input mikrofon kosong");
        if (peak > 32700 && speechRatio > 0.75)
            return new VoiceQuality(false, rms, "sinyal mikrofon terlalu keras/terdistorsi");
        return new VoiceQuality(true, rms, "ok");
    }

    private static short[] trimSilence(short[] pcm, double rms) {
        if (pcm == null || pcm.length == 0) return pcm;
        double threshold = Math.max(0.002, Math.min(0.010, rms * 0.30));
        int first = 0, last = pcm.length - 1;
        while (first < pcm.length && Math.abs(pcm[first] / 32768.0) < threshold) first++;
        while (last > first && Math.abs(pcm[last] / 32768.0) < threshold) last--;
        int padding = IMOAudioRecorder.SAMPLE_RATE / 8;
        first = Math.max(0, first - padding);
        last = Math.min(pcm.length - 1, last + padding);
        int length = last - first + 1;
        if (length < 12000) return pcm;
        short[] out = new short[length];
        System.arraycopy(pcm, first, out, 0, length);
        return out;
    }

    private static float[] average(List<float[]> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("No voice samples captured");
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

    private static final class VoiceQuality {
        final boolean usable;
        final double rms;
        final String message;
        VoiceQuality(boolean usable, double rms, String message) { this.usable = usable; this.rms = rms; this.message = message; }
    }
}
