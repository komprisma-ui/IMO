package com.imo.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic semantic planner for common Indonesian operator intents. */
public final class IMOPlanner {
    private IMOPlanner() {}

    public static List<IMOAction> plan(String input) {
        List<IMOAction> actions = new ArrayList<>();
        if (input == null) return actions;
        String q = input.trim();
        String n = normalize(q);
        if (n.isEmpty()) return actions;

        if (containsAny(n, "buka whatsapp", "masuk whatsapp", "jalankan whatsapp") || n.equals("whatsapp")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.whatsapp"));
            return actions;
        }
        if (containsAny(n, "buka chrome", "buka browser", "jalankan chrome")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.android.chrome"));
            return actions;
        }
        if (containsAny(n, "buka youtube", "jalankan youtube")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.google.android.youtube"));
            return actions;
        }
        if (containsAny(n, "kembali", "back", "mundur")) {
            actions.add(IMOAction.of(IMOAction.Type.BACK, null));
            return actions;
        }
        if (containsAny(n, "home", "layar utama", "halaman utama")) {
            actions.add(IMOAction.of(IMOAction.Type.HOME, null));
            return actions;
        }
        if (containsAny(n, "baca layar", "lihat layar", "apa yang ada di layar", "bacakan layar")) {
            actions.add(IMOAction.of(IMOAction.Type.READ, null));
            return actions;
        }
        if (containsAny(n, "scroll bawah", "scroll ke bawah", "gulir bawah", "gulir ke bawah", "geser ke bawah")) {
            actions.add(IMOAction.of(IMOAction.Type.SCROLL_DOWN, null));
            return actions;
        }
        if (containsAny(n, "scroll atas", "scroll ke atas", "gulir atas", "gulir ke atas", "geser ke atas")) {
            actions.add(IMOAction.of(IMOAction.Type.SCROLL_UP, null));
            return actions;
        }

        String click = extractAfter(n, q, "klik", "tekan", "pilih");
        if (!click.isEmpty()) {
            actions.add(IMOAction.of(IMOAction.Type.CLICK, click));
            return actions;
        }
        String type = extractAfter(normalize(q), q, "ketik", "tulis", "isi", "masukkan");
        if (!type.isEmpty()) {
            actions.add(IMOAction.of(IMOAction.Type.TYPE, type));
            return actions;
        }

        if (containsAny(n, "kirim", "send", "hapus", "delete", "bayar", "transfer", "beli")) {
            actions.add(IMOAction.sensitive(IMOAction.Type.CONFIRM, q));
            return actions;
        }

        String target = extractSearchTarget(n, q);
        if (!target.isEmpty()) {
            actions.add(IMOAction.of(IMOAction.Type.CLICK, "cari"));
            actions.add(IMOAction.waitFor(500));
            actions.add(IMOAction.of(IMOAction.Type.TYPE, target));
            actions.add(IMOAction.waitFor(800));
            actions.add(IMOAction.of(IMOAction.Type.READ, null));
        }
        return actions;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String s, String... values) {
        for (String v : values) if (s.contains(v)) return true;
        return false;
    }

    private static String extractAfter(String normalized, String original, String... prefixes) {
        for (String p : prefixes) {
            if (normalized.startsWith(p + " ")) return original.substring(p.length()).trim();
        }
        return "";
    }

    private static String extractSearchTarget(String normalized, String original) {
        String[] prefixes = {"cari ", "carikan ", "temukan ", "temuin ", "search "};
        for (String p : prefixes) if (normalized.startsWith(p)) return original.substring(p.length()).trim();
        return "";
    }
}
