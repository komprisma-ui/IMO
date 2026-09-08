package com.imo.operator;

/** Fail-closed speaker verification using the enrolled local embedding and repeated evidence gate. */
public final class IMOVoiceVerifier {
    private final IMOSpeakerEncoder encoder;
    private final IMOVoiceIdentity identity;
    private final IMOSpeakerGate gate;

    public IMOVoiceVerifier(IMOSpeakerEncoder encoder, IMOVoiceIdentity identity, IMOSpeakerGate gate) {
        this.encoder = encoder;
        this.identity = identity;
        this.gate = gate;
    }

    public boolean isReady() { return encoder != null && identity.isEnrolled(); }

    public boolean verify(short[] pcm16, int sampleRateHz) {
        if (!isReady() || pcm16 == null || pcm16.length == 0) { gate.reset(); return false; }
        try {
            float[] candidate = encoder.embed(pcm16, sampleRateHz);
            float[] enrolled = identity.load();
            float score = IMOVoiceIdentity.cosineSimilarity(enrolled, candidate);
            return score >= 0 && gate.acceptScore(score);
        } catch (Exception ignored) {
            gate.reset();
            return false;
        }
    }

    public void reset() { gate.reset(); }
}
