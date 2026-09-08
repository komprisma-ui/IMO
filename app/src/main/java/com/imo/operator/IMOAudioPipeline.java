package com.imo.operator;

/** Single-buffer voice pipeline foundation: the same PCM is used for verification and future local ASR. */
public final class IMOAudioPipeline {
    public interface Listener {
        void onState(String state);
        void onAccepted(short[] pcm16);
        void onRejected(String reason);
        void onError(String reason);
    }

    private final IMOSpeakerEncoder encoder;
    private final IMOVoiceIdentity identity;
    private final IMOSpeakerGate gate;
    private final float minRms;

    public IMOAudioPipeline(IMOSpeakerEncoder encoder, IMOVoiceIdentity identity,
                            IMOSpeakerGate gate, float minRms) {
        this.encoder = encoder;
        this.identity = identity;
        this.gate = gate;
        this.minRms = Math.max(0.001f, minRms);
    }

    /** Captures one utterance window and sends that exact PCM buffer through verification. */
    public void captureAndVerify(long durationMs, Listener listener) {
        if (listener == null) return;
        new Thread(() -> {
            try {
                listener.onState("Merekam satu buffer suara…");
                short[] pcm = IMOAudioRecorder.record(durationMs);
                if (rms(pcm) < minRms) {
                    gate.reset();
                    listener.onRejected("Suara terlalu lemah atau tidak terdeteksi.");
                    return;
                }
                listener.onState("Memverifikasi pembicara…");
                IMOVoiceVerifier verifier = new IMOVoiceVerifier(encoder, identity, gate);
                if (!verifier.verify(pcm, IMOAudioRecorder.SAMPLE_RATE)) {
                    listener.onRejected("Suara tidak cocok dengan voiceprint.");
                    return;
                }
                listener.onAccepted(pcm);
            } catch (Exception e) {
                gate.reset();
                listener.onError(e.getMessage() == null ? "Audio pipeline gagal." : e.getMessage());
            }
        }, "IMO-Audio-Pipeline").start();
    }

    static float rms(short[] pcm) {
        if (pcm == null || pcm.length == 0) return 0f;
        double sum = 0.0;
        for (short sample : pcm) {
            double v = sample / 32768.0;
            sum += v * v;
        }
        return (float) Math.sqrt(sum / pcm.length);
    }
}
