package com.imo.operator;

/** A small, deterministic action vocabulary used by the local IMO brain. */
public final class IMOAction {
    public enum Type { OPEN_APP, CLICK, TYPE, READ, BACK, HOME, WAIT, NONE }

    public final Type type;
    public final String value;
    public final long waitMs;

    private IMOAction(Type type, String value, long waitMs) {
        this.type = type;
        this.value = value;
        this.waitMs = waitMs;
    }

    public static IMOAction of(Type type, String value) { return new IMOAction(type, value, 0); }
    public static IMOAction waitFor(long ms) { return new IMOAction(Type.WAIT, null, ms); }
    public static IMOAction none() { return new IMOAction(Type.NONE, null, 0); }

    @Override public String toString() {
        return type + (value == null ? "" : "(" + value + ")");
    }
}
