package com.imo.operator;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Small persistent conversation/task memory. Secrets are intentionally not stored here. */
public final class IMOMemory {
    private static final String PREF = "imo_memory";
    private static final String HISTORY = "history";
    private final SharedPreferences prefs;

    public IMOMemory(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized void remember(String user, String imo) {
        String old = prefs.getString(HISTORY, "");
        String line = "USER: " + safe(user) + "\nIMO: " + safe(imo);
        String next = old.isEmpty() ? line : old + "\n---\n" + line;
        if (next.length() > 12000) next = next.substring(next.length() - 12000);
        prefs.edit().putString(HISTORY, next).apply();
    }

    public synchronized String recent(int maxChars) {
        String h = prefs.getString(HISTORY, "");
        if (h.length() <= maxChars) return h;
        return h.substring(h.length() - maxChars);
    }

    public synchronized void clear() { prefs.edit().remove(HISTORY).apply(); }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').trim();
    }
}
