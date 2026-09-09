package com.komprisma.imo.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded conversational/task context. Secrets are redacted before storage. */
public final class IMOContextMemory {
    public static final class Event {
        public final long timestamp; public final String kind; public final String summary;
        Event(long timestamp, String kind, String summary) { this.timestamp = timestamp; this.kind = kind; this.summary = summary; }
    }
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(pin|otp|password|passcode|kode verifikasi)\\b[^,.!?]*");
    private final int maxEvents; private final Deque<Event> events = new ArrayDeque<>();
    public IMOContextMemory() { this(32); }
    public IMOContextMemory(int maxEvents) { this.maxEvents = Math.max(4, Math.min(128, maxEvents)); }
    public synchronized void add(String kind, String summary) {
        if (summary == null) return;
        String clean = SECRET.matcher(summary).replaceAll("[REDACTED]");
        events.addLast(new Event(System.currentTimeMillis(), kind == null ? "EVENT" : kind.substring(0, Math.min(32, kind.length())), clean.substring(0, Math.min(300, clean.length()))));
        while (events.size() > maxEvents) events.removeFirst();
    }
    public synchronized List<Event> recent(int limit) {
        int n = Math.max(1, Math.min(16, limit));
        ArrayList<Event> out = new ArrayList<>(events);
        return out.subList(Math.max(0, out.size() - n), out.size());
    }
    public synchronized void clear() { events.clear(); }
}
