package com.imo.operator;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;

/** Local voice-identity profile. Stores an embedding, never raw microphone audio. */
public final class IMOVoiceIdentity {
    private static final String PREF = "imo_voice_identity";
    private static final String KEY_EMBEDDING = "embedding";
    private final SharedPreferences prefs;

    public IMOVoiceIdentity(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized void enroll(float[] embedding) {
        if (!valid(embedding)) throw new IllegalArgumentException("Invalid voice embedding");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) b.append(',');
            b.append(Float.toString(embedding[i]));
        }
        prefs.edit().putString(KEY_EMBEDDING, b.toString()).apply();
    }

    public synchronized boolean isEnrolled() {
        return !prefs.getString(KEY_EMBEDDING, "").isEmpty();
    }

    public synchronized float[] load() {
        String raw = prefs.getString(KEY_EMBEDDING, "");
        if (raw.isEmpty()) return null;
        String[] parts = raw.split(",");
        float[] result = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i]);
            return valid(result) ? result : null;
        } catch (NumberFormatException e) { return null; }
    }

    public synchronized void clear() { prefs.edit().remove(KEY_EMBEDDING).apply(); }

    public static float cosineSimilarity(float[] a, float[] b) {
        if (!valid(a) || !valid(b) || a.length != b.length) return -1f;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i];
        }
        if (aa <= 0 || bb <= 0) return -1f;
        return (float)(dot / (Math.sqrt(aa) * Math.sqrt(bb)));
    }

    private static boolean valid(float[] v) {
        if (v == null || v.length < 8) return false;
        for (float x : v) if (Float.isNaN(x) || Float.isInfinite(x)) return false;
        return true;
    }
}
