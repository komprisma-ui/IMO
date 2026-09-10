package com.imo.operator;

/**
 * Lightweight goal-state verifier used by the autonomous loop.
 * It deliberately fails closed when the model response is malformed.
 */
public final class IMOGoalVerifier {
    public enum Status { DONE, CONTINUE, BLOCKED, UNKNOWN }

    public static final class Result {
        public final Status status;
        public final String message;
        public final String action;
        public Result(Status status, String message, String action) {
            this.status = status;
            this.message = message == null ? "" : message.trim();
            this.action = action == null ? "" : action.trim();
        }
    }

    private IMOGoalVerifier() {}

    public static Result parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new Result(Status.UNKNOWN, "", "");
        Status status = Status.UNKNOWN;
        String message = "";
        String action = "";
        for (String line : raw.split("\\r?\\n")) {
            String s = line.trim();
            if (s.regionMatches(true, 0, "STATUS:", 0, 7)) {
                String value = s.substring(7).trim().toUpperCase();
                try { status = Status.valueOf(value); } catch (IllegalArgumentException ignored) { status = Status.UNKNOWN; }
            } else if (s.regionMatches(true, 0, "SAY:", 0, 4)) {
                message = s.substring(4).trim();
            } else if (s.regionMatches(true, 0, "ACTION:", 0, 7)) {
                action = s.substring(7).trim();
            }
        }
        return new Result(status, message, action);
    }

    public static String systemInstruction() {
        return "Untuk verifikasi tujuan, keluarkan tepat tiga baris: STATUS: DONE atau CONTINUE atau BLOCKED; " +
               "SAY: jawaban singkat Bahasa Indonesia; ACTION: perintah berikutnya hanya jika STATUS CONTINUE. " +
               "Jika keadaan tidak cukup jelas, gunakan STATUS: BLOCKED dan jangan mengarang fakta.";
    }
}
