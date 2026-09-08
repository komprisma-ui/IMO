package com.imo.operator;

/** In-memory confirmation gate for sensitive actions. Nothing sensitive is persisted. */
public final class IMOConfirmation {
    private IMOAction pending;
    private long expiresAt;
    private static final long TTL_MS = 60_000L;

    public synchronized void request(IMOAction action) {
        pending = action;
        expiresAt = System.currentTimeMillis() + TTL_MS;
    }

    public synchronized IMOAction consumeIfConfirmed(String input) {
        if (pending == null || System.currentTimeMillis() > expiresAt) { clear(); return null; }
        String s = input == null ? "" : input.toLowerCase().trim();
        boolean yes = s.equals("ya") || s.equals("iya") || s.equals("setuju") || s.equals("kirim") || s.equals("lanjut") || s.equals("lakukan");
        boolean no = s.equals("tidak") || s.equals("nggak") || s.equals("batal") || s.equals("cancel") || s.equals("jangan");
        if (no) { clear(); return IMOAction.none(); }
        if (!yes) return null;
        IMOAction action = pending;
        clear();
        return action;
    }

    public synchronized IMOAction pending() {
        if (pending != null && System.currentTimeMillis() <= expiresAt) return pending;
        clear();
        return null;
    }

    public synchronized void clear() { pending = null; expiresAt = 0; }
}
