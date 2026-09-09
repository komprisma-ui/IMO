package com.imo.operator;

import java.util.Locale;

/** Safety gate for actions that can create irreversible or externally visible effects. */
public final class IMOActionPolicy {
    public enum Risk { LOW, MEDIUM, HIGH }

    public static final class Assessment {
        public final Risk risk;
        public final boolean confirm;
        public final String reason;
        Assessment(Risk risk, boolean confirm, String reason) { this.risk = risk; this.confirm = confirm; this.reason = reason; }
    }

    private IMOActionPolicy() { }

    public static Assessment assess(String command) {
        String s = command == null ? "" : command.toLowerCase(Locale.ROOT);
        if (has(s, "transfer", "bayar", "beli", "hapus permanen", "factory reset", "reset pabrik"))
            return new Assessment(Risk.HIGH, true, "Dapat menyebabkan perubahan finansial atau kehilangan data.");
        if (has(s, "kirim", "telepon", "panggil", "posting", "publikasi", "uninstall", "hapus"))
            return new Assessment(Risk.MEDIUM, true, "Dapat menghasilkan tindakan eksternal atau perubahan data.");
        return new Assessment(Risk.LOW, false, "Tindakan lokal berisiko rendah.");
    }

    private static boolean has(String s, String... words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }
}
