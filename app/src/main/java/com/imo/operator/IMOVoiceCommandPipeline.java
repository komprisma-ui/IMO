package com.imo.operator;

import android.content.Context;

/** Voice pipeline: VAD -> quality -> multi-window speaker consensus -> same PCM to ASR. */
public final class IMOVoiceCommandPipeline {
    public interface Callback {
        void onState(String message);
        void onAccepted(short[] pcm16, int sampleRateHz);
        void onRejected(String message);
        void onError(String message);
    }

    private final Context context;
    private final IMOVoiceIdentity identity;
    private final float threshold;
    private volatile boolean running;

    public IMOVoiceCommandPipeline(Context context, IMOVoiceIdentity identity) { this(context, identity, 0.72f); }

    public IMOVoiceCommandPipeline(Context context, IMOVoiceIdentity identity, float threshold) {
        if (context == null || identity == null) throw new IllegalArgumentException("Context and identity are required");
        if (threshold < 0f || threshold > 1f) throw new IllegalArgumentException("Invalid speaker threshold");
        this.context = context.getApplicationContext(); this.identity = identity; this.threshold = threshold;
    }

    public synchronized boolean start(long durationMs, Callback callback) {
        if (running) return false;
        if (callback == null) throw new IllegalArgumentException("Callback is required");
        if (!identity.isEnrolled()) { callback.onRejected("Voiceprint belum terdaftar."); return false; }
        // Consensus needs enough speech for three overlapping speaker windows.
        if (durationMs < 3000 || durationMs > 10000) throw new IllegalArgumentException("Voice capture must be 3-10 seconds");
        running = true;
        new Thread(() -> captureAndVerify(durationMs, callback), "IMO-VoicePipeline").start();
        return true;
    }

    public boolean isRunning() { return running; }

    private void captureAndVerify(long durationMs, Callback callback) {
        IMOSherpaSpeakerEncoder encoder = null;
        try {
            callback.onState("Mendengarkan suara…");
            short[] pcm = IMOAudioRecorder.record(durationMs);
            if (!hasSpeech(pcm)) {
                callback.onRejected("Ucapan belum cukup jelas. Dekatkan ponsel dan bicara sedikit lebih jelas.");
                return;
            }
            short[] speech = trimSilence(pcm);
            if (!hasSpeech(speech) || speech.length < IMOAudioRecorder.SAMPLE_RATE * 2) {
                callback.onRejected("Suara terlalu singkat. Ucapkan perintah sedikit lebih lengkap.");
                return;
            }
            callback.onState("Mengenali suara dari beberapa bagian ucapan…");
            encoder = new IMOSherpaSpeakerEncoder(context);
            IMOSpeakerGate gate = new IMOSpeakerGate(3, 2, threshold);
            IMOVoiceVerifier verifier = new IMOVoiceVerifier(encoder, identity, gate);
            short[][] windows = makeWindows(speech, IMOAudioRecorder.SAMPLE_RATE);
            if (!verifier.verifyConsensus(windows, IMOAudioRecorder.SAMPLE_RATE)) {
                callback.onRejected("Suara belum cukup cocok. Coba ulangi dengan jarak ponsel yang sama dan bicara alami.");
                return;
            }
            callback.onState("Suara cocok ✓. Memahami perintah secara offline…");
            callback.onAccepted(speech, IMOAudioRecorder.SAMPLE_RATE);
        } catch (Exception e) {
            callback.onError(safe(e.getMessage()));
        } finally {
            if (encoder != null) encoder.release();
            running = false;
        }
    }

    /** Three overlapping windows preserve robustness when one portion contains noise or a pause. */
    static short[][] makeWindows(short[] pcm, int sampleRateHz) {
        int window = Math.max(sampleRateHz, (int)(sampleRateHz * 1.5));
        if (pcm == null || pcm.length < window) return new short[][]{pcm, pcm, pcm};
        int maxStart = pcm.length - window;
        int start1 = 0;
        int start2 = Math.max(0, maxStart / 2);
        int start3 = maxStart;
        return new short[][]{
                slice(pcm, start1, window),
                slice(pcm, start2, window),
                slice(pcm, start3, window)
        };
    }

    private static short[] slice(short[] pcm, int start, int length) {
        short[] out = new short[length];
        System.arraycopy(pcm, start, out, 0, length);
        return out;
    }

    static boolean hasSpeech(short[] pcm) {
        if (pcm == null || pcm.length < 4000) return false;
        long energy = 0;
        int peak = 0;
        int active = 0;
        for (short sample : pcm) {
            int a = Math.abs((int) sample);
            peak = Math.max(peak, a);
            energy += (long) a * a;
            if (a >= 82) active++;
        }
        double rms = Math.sqrt((double) energy / pcm.length) / 32768.0;
        double activeRatio = (double) active / pcm.length;
        return rms >= 0.0025 && peak >= 300 && activeRatio >= 0.01;
    }

    static short[] trimSilence(short[] pcm) {
        if (pcm == null || pcm.length == 0) return pcm;
        int first = 0, last = pcm.length - 1;
        final int threshold = 82;
        while (first < pcm.length && Math.abs((int) pcm[first]) < threshold) first++;
        while (last > first && Math.abs((int) pcm[last]) < threshold) last--;
        int padding = IMOAudioRecorder.SAMPLE_RATE / 8;
        first = Math.max(0, first - padding);
        last = Math.min(pcm.length - 1, last + padding);
        int length = last - first + 1;
        if (length < IMOAudioRecorder.SAMPLE_RATE / 2) return pcm;
        short[] out = new short[length];
        System.arraycopy(pcm, first, out, 0, length);
        return out;
    }

    private static String safe(String message) {
        return message == null || message.trim().isEmpty() ? "kesalahan audio tidak diketahui" : message;
    }
}
