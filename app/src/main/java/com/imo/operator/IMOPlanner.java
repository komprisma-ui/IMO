package com.imo.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic Indonesian planner. It creates safe primitives and preserves the user's text. */
public final class IMOPlanner {
    private IMOPlanner() {}

    public static List<IMOAction> plan(String input) {
        List<IMOAction> actions = new ArrayList<>();
        if (input == null) return actions;
        String q = input.trim();
        String n = q.toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return actions;

        // Multi-step common workflows first.
        if (n.contains("buka whatsapp") && (n.contains("cari ") || n.contains("temukan "))) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.whatsapp"));
            actions.add(IMOAction.waitFor(900));
            actions.add(IMOAction.of(IMOAction.Type.READ, null));
            return actions;
        }

        if (n.contains("buka whatsapp") || n.equals("whatsapp") || n.contains("masuk whatsapp")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.whatsapp"));
            return actions;
        }
        if (n.contains("buka chrome") || n.contains("buka browser")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.android.chrome"));
            return actions;
        }
        if (n.contains("buka youtube")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.google.android.youtube"));
            return actions;
        }
        if (n.equals("kembali") || n.equals("back") || n.contains("kembali satu langkah")) {
            actions.add(IMOAction.of(IMOAction.Type.BACK, null));
            return actions;
        }
        if (n.equals("home") || n.contains("halaman utama") || n.contains("layar utama")) {
            actions.add(IMOAction.of(IMOAction.Type.HOME, null));
            return actions;
        }
        if (n.contains("scroll ke bawah") || n.contains("gulir ke bawah") || n.contains("geser ke bawah")) {
            actions.add(IMOAction.of(IMOAction.Type.SCROLL_DOWN, null));
            return actions;
        }
        if (n.contains("scroll ke atas") || n.contains("gulir ke atas") || n.contains("geser ke atas")) {
            actions.add(IMOAction.of(IMOAction.Type.SCROLL_UP, null));
            return actions;
        }
        if (n.contains("baca layar") || n.contains("apa yang ada di layar") || n.contains("lihat layar")) {
            actions.add(IMOAction.of(IMOAction.Type.READ, null));
            return actions;
        }
        if (n.startsWith("klik ") || n.startsWith("tekan ") || n.startsWith("pilih ")) {
            String value = q.replaceFirst("(?i)^(klik|tekan|pilih)\\s+", "").trim();
            if (!value.isEmpty()) actions.add(IMOAction.of(IMOAction.Type.CLICK, value));
            return actions;
        }
        if (n.startsWith("ketik ") || n.startsWith("tulis ") || n.startsWith("isi ")) {
            String value = q.replaceFirst("(?i)^(ketik|tulis|isi)\\s+", "").trim();
            if (!value.isEmpty()) actions.add(IMOAction.of(IMOAction.Type.TYPE, value));
            return actions;
        }
        return actions;
    }
}
