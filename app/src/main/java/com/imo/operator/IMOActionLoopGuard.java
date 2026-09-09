package com.imo.operator;

import java.security.MessageDigest;

/** Prevents autonomous execution from repeating the same decision on an unchanged screen. */
public final class IMOActionLoopGuard {
    private String lastFingerprint = "";
    private int repeats;
    private static final int MAX_REPEATS = 2;

    public synchronized boolean allow(String screen, String action) {
        String fp = hash((screen == null ? "" : screen) + "\nACTION:" + (action == null ? "" : action));
        if (fp.equals(lastFingerprint)) repeats++; else { lastFingerprint = fp; repeats = 0; }
        return repeats <= MAX_REPEATS;
    }

    public synchronized void reset() { lastFingerprint = ""; repeats = 0; }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) { return value; }
    }
}
