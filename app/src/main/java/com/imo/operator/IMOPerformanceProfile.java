package com.imo.operator;

/** Central performance policy for voice interaction. Keeps the hot path cheap while preserving full AI quality for complex tasks. */
public final class IMOPerformanceProfile {
    private IMOPerformanceProfile() {}

    public static final int MAX_FAST_HISTORY = 6;
    public static final int MAX_AI_HISTORY = 14;
    public static final int MAX_SCREEN_CHARS = 6500;

    public static int speechSilenceMs(String normalizedText) {
        int length = normalizedText == null ? 0 : normalizedText.trim().length();
        if (length <= 12) return 350;
        if (length <= 35) return 500;
        return 700;
    }

    public static boolean needsDeepReasoning(IMOAction action) {
        return action == null || action.getType() == IMOAction.Type.NONE;
    }

    public static int preActionDelayMs() { return 0; }
}
