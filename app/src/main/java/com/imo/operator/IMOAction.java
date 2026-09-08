package com.imo.operator;

/** Structured action vocabulary used by IMO's planning/execution loop. */
public final class IMOAction {
    public enum Type {
        OPEN_APP, CLICK, TYPE, READ, BACK, HOME, WAIT,
        SCROLL_DOWN, SCROLL_UP, SWIPE, LONG_CLICK,
        CONFIRM, NONE
    }

    public final Type type;
    public final String value;
    public final long waitMs;
    public final boolean sensitive;

    private IMOAction(Type type, String value, long waitMs, boolean sensitive) {
        this.type = type;
        this.value = value;
        this.waitMs = waitMs;
        this.sensitive = sensitive;
    }

    public static IMOAction of(Type type, String value) {
        return new IMOAction(type, value, 0, isSensitive(type, value));
    }

    public static IMOAction sensitive(Type type, String value) {
        return new IMOAction(type, value, 0, true);
    }

    public static IMOAction waitFor(long ms) {
        return new IMOAction(Type.WAIT, null, ms, false);
    }

    public static IMOAction none() {
        return new IMOAction(Type.NONE, null, 0, false);
    }

    private static boolean isSensitive(Type type, String value) {
        if (type == Type.CONFIRM) return true;
        String v = value == null ? "" : value.toLowerCase();
        return v.contains("kirim") || v.contains("hapus") || v.contains("bayar") ||
                v.contains("transfer") || v.contains("beli") || v.contains("password") ||
                v.contains("kode otp");
    }

    @Override public String toString() {
        return type + (value == null ? "" : "(" + value + ")") + (sensitive ? " [SENSITIVE]" : "");
    }
}
