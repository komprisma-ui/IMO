package com.imo.operator;

import java.util.ArrayDeque;
import java.util.Deque;

/** Lightweight on-device conversation context for LUNA. */
public final class ConversationMemory {
    private static final int MAX_TURNS = 10;
    private final Deque<String> turns = new ArrayDeque<>();

    public synchronized void addUser(String text) {
        if (text == null || text.trim().isEmpty()) return;
        turns.addLast("PENGGUNA: " + text.trim());
        trim();
    }

    public synchronized void addAssistant(String text) {
        if (text == null || text.trim().isEmpty()) return;
        turns.addLast("LUNA: " + text.trim());
        trim();
    }

    public synchronized String prompt() {
        if (turns.isEmpty()) return "Belum ada percakapan sebelumnya.";
        StringBuilder b = new StringBuilder();
        for (String t : turns) b.append(t).append('\n');
        return b.toString().trim();
    }

    public synchronized void clear() { turns.clear(); }

    private void trim() {
        while (turns.size() > MAX_TURNS * 2) turns.removeFirst();
    }
}
