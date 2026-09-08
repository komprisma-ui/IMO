package com.imo.operator;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lightweight speaker gate. It intentionally does not pretend to identify a person from
 * speech recognition text: callers must provide a speaker-similarity score from a future
 * on-device speaker encoder. Multiple consecutive accepted frames are required before the
 * gate opens, reducing false activation in noisy environments.
 */
public final class IMOSpeakerGate {
    public interface Listener {
        void onAccepted();
        void onRejected();
    }

    private final Deque<Boolean> recent = new ArrayDeque<>();
    private final int windowSize;
    private final int requiredAccepted;
    private final float threshold;
    private long cooldownUntil;

    public IMOSpeakerGate() { this(5, 4, 0.72f); }

    public IMOSpeakerGate(int windowSize, int requiredAccepted, float threshold) {
        if (windowSize < 1 || requiredAccepted < 1 || requiredAccepted > windowSize)
            throw new IllegalArgumentException("Invalid speaker gate configuration");
        this.windowSize = windowSize;
        this.requiredAccepted = requiredAccepted;
        this.threshold = threshold;
    }

    public synchronized boolean acceptScore(float similarity) {
        if (System.currentTimeMillis() < cooldownUntil) return false;
        boolean accepted = similarity >= threshold;
        recent.addLast(accepted);
        while (recent.size() > windowSize) recent.removeFirst();
        int count = 0;
        for (Boolean value : recent) if (value) count++;
        return count >= requiredAccepted;
    }

    public synchronized void cooldown(long millis) {
        cooldownUntil = Math.max(cooldownUntil, System.currentTimeMillis() + Math.max(0, millis));
        recent.clear();
    }

    public synchronized void reset() {
        recent.clear();
        cooldownUntil = 0;
    }

    public float threshold() { return threshold; }
    public synchronized int evidenceCount() {
        int count = 0;
        for (Boolean value : recent) if (value) count++;
        return count;
    }
}
