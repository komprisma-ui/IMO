package com.imo.operator;

/** Fail-closed speaker verification using local embeddings and consensus evidence. */
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

    /**
     * Verifies several overlapping speech windows. One bad/noisy window cannot decide identity;
     * at least two windows must clear the threshold and the median must also clear it.
     */
    public boolean verifyConsensus(short[][] windows, int sampleRateHz) {
        if (!isReady() || windows == null || windows.length < 3) { gate.reset(); return false; }
        try {
            float[] enrolled = identity.load();
            int accepted = 0;
            float[] scores = new float[windows.length];
            int valid = 0;
            for (short[] window : windows) {
                if (window == null || window.length < sampleRateHz / 2) continue;
                float[] candidate = encoder.embed(window, sampleRateHz);
                float score = IMOVoiceIdentity.cosineSimilarity(enrolled, candidate);
                if (score < 0) continue;
                scores[valid++] = score;
                if (score >= gate.threshold()) accepted++;
            }
            if (valid < 3 || accepted < 2) { gate.reset(); return false; }
            java.util.Arrays.sort(scores, 0, valid);
            float median = scores[valid / 2];
            gate.reset();
            return median >= gate.threshold();
        } catch (Exception ignored) {
            gate.reset();
            return false;
        }
    }

    public void reset() { gate.reset(); }
}
