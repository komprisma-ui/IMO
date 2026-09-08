package com.imo.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local Indonesian intent parser. It is deliberately deterministic and offline. */
public final class IMOPlanner {
    private IMOPlanner() {}

    public static List<IMOAction> plan(String input) {
        List<IMOAction> actions = new ArrayList<>();
        if (input == null) return actions;
        String q = input.trim();
        String n = q.toLowerCase(Locale.ROOT);

        if (n.contains("buka whatsapp") || n.equals("whatsapp") || n.contains("masuk whatsapp")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.whatsapp"));
        } else if (n.contains("buka chrome") || n.contains("buka browser")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.android.chrome"));
        } else if (n.contains("buka youtube")) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, "com.google.android.youtube"));
        } else if (n.equals("kembali") || n.equals("back") || n.contains("kembali satu langkah")) {
            actions.add(IMOAction.of(IMOAction.Type.BACK, null));
        } else if (n.equals("home") || n.contains("halaman utama") || n.contains("layar utama")) {
            actions.add(IMOAction.of(IMOAction.Type.HOME, null));
        } else if (n.contains("baca layar") || n.contains("apa yang ada di layar") || n.contains("lihat layar")) {
            actions.add(IMOAction.of(IMOAction.Type.READ, null));
        } else if (n.startsWith("klik ") || n.startsWith("tekan ") || n.startsWith("pilih ")) {
            String value = q.replaceFirst("(?i)^(klik|tekan|pilih)\\s+", "").trim();
            if (!value.isEmpty()) actions.add(IMOAction.of(IMOAction.Type.CLICK, value));
        } else if (n.startsWith("ketik ") || n.startsWith("tulis ") || n.startsWith("isi ")) {
            String value = q.replaceFirst("(?i)^(ketik|tulis|isi)\\s+", "").trim();
            if (!value.isEmpty()) actions.add(IMOAction.of(IMOAction.Type.TYPE, value));
        }
        return actions;
    }
}
