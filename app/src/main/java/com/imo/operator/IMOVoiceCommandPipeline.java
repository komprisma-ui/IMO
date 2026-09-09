package com.imo.operator;

import android.content.Context;

/** Voice pipeline: VAD -> quality -> same PCM to ASR. Speaker identity is intentionally not required. */
public final class IMOVoiceCommandPipeline {
    public interface Callback {
        void onState(String message);
        void onAccepted(short[] pcm16, int sampleRateHz);
        void onRejected(String message);
        void onError(String message);
    }

    private final Context context;
    private volatile boolean running;

    /** Kept for source compatibility; voice identity is no longer used to gate commands. */
    public IMOVoiceCommandPipeline(Context context, IMOVoiceIdentity identity) { this(context, identity, 0f); }

    /** Kept for source compatibility; threshold is intentionally ignored. */
    public IMOVoiceCommandPipeline(Context context, IMOVoiceIdentity identity, float threshold) {
        if (context == null) throw new IllegalArgumentException("Context is required");
        this.context = context.getApplicationContext();
    }

    public synchronized boolean start(long durationMs, Callback callback) {
        if (running) return false;
        if (callback == null) throw new IllegalArgumentException("Callback is required");
        if (durationMs < 3000 || durationMs > 10000) throw new IllegalArgumentException("Voice capture must be 3-10 seconds");
        running = true;
        new Thread(() -> captureAndProcess(durationMs, callback), "IMO-VoicePipeline").start();
        return true;
    }

    public boolean isRunning() { return running; }

    private void captureAndProcess(long durationMs, Callback callback) {
        try {
            callback.onState("Mendengarkan suara…");
            short[] pcm = IMOAudioRecorder.record(durationMs);
            Quality quality = assessQuality(pcm);
            if (!quality.speech) {
                callback.onRejected("Suara belum cukup jelas. Coba bicara alami dengan jarak ponsel yang nyaman.");
                return;
            }
            short[] speech = trimSilence(pcm);
            Quality trimmedQuality = assessQuality(speech);
            if (!trimmedQuality.speech || speech.length < IMOAudioRecorder.SAMPLE_RATE * 2) {
                callback.onRejected("Ucapan belum cukup panjang atau jelas. Coba ulangi dengan suara alami.");
                return;
            }
            callback.onState("Suara diterima. Memahami perintah secara offline…");
            // The exact same cleaned PCM is passed to ASR. No speaker embedding or identity threshold
            // can block a valid command; any speaker may interact with IMO.
            callback.onAccepted(speech, IMOAudioRecorder.SAMPLE_RATE);
        } catch (Exception e) {
            callback.onError(safe(e.getMessage()));
        } finally {
            running = false;
        }
    }

    static boolean hasSpeech(short[] pcm) { return assessQuality(pcm).speech; }

    /** Quality-aware gate: reject silence/corrupt/clipped input, but never compare speaker identity. */
    static Quality assessQuality(short[] pcm) {
        if (pcm == null || pcm.length < 4000) return new Quality(false, 0, 0, 0);
        double energy = 0;
        int peak = 0;
        int active = 0;
        int clipped = 0;
        for (short sample : pcm) {
            int a = Math.abs((int) sample);
            peak = Math.max(peak, a);
            energy += (double) a * a;
            if (a >= 82) active++;
            if (a >= 32700) clipped++;
        }
        double rms = Math.sqrt(energy / pcm.length) / 32768.0;
        double activeRatio = (double) active / pcm.length;
        double clippingRatio = (double) clipped / pcm.length;
        boolean speech = rms >= 0.0010 && peak >= 180 && activeRatio >= 0.004 && activeRatio <= 0.995 && clippingRatio < 0.12;
        return new Quality(speech, rms, activeRatio, clippingRatio);
    }

    static short[] trimSilence(short[] pcm) {
        if (pcm == null || pcm.length == 0) return pcm;
        int first = 0, last = pcm.length - 1;
        final int threshold = 60;
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

    static final class Quality {
        final boolean speech; final double rms; final double activeRatio; final double clippingRatio;
        Quality(boolean speech, double rms, double activeRatio, double clippingRatio) {
            this.speech = speech; this.rms = rms; this.activeRatio = activeRatio; this.clippingRatio = clippingRatio;
        }
    }

    private static String safe(String message) {
        return message == null || message.trim().isEmpty() ? "kesalahan audio tidak diketahui" : message;
    }
}
