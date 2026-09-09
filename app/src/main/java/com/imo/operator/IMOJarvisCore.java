package com.imo.operator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * IMO's local command-intelligence layer.
 * Turns natural Indonesian speech into a normalized task while keeping safety explicit.
 * This is deliberately deterministic/offline: no fake cloud intelligence and no hidden actions.
 */
public final class IMOJarvisCore {
    public enum Intent {
        OPEN_APP, CLICK, TYPE, READ, NAVIGATE, SCROLL, NONE
    }

    public static final class Decision {
        public final String original;
        public final String normalized;
        public final Intent intent;
        public final float confidence;
        public final boolean sensitive;
        public final boolean needsConfirmation;
        public final String explanation;

        Decision(String original, String normalized, Intent intent, float confidence,
                 boolean sensitive, boolean needsConfirmation, String explanation) {
            this.original = original;
            this.normalized = normalized;
            this.intent = intent;
            this.confidence = confidence;
            this.sensitive = sensitive;
            this.needsConfirmation = needsConfirmation;
            this.explanation = explanation;
        }
    }

    private IMOJarvisCore() { }

    public static Decision understand(String input) {
        String original = input == null ? "" : input.trim();
        String s = normalize(original);
        if (s.isEmpty()) return new Decision(original, "", Intent.NONE, 0f, false, false, "Perintah kosong");

        boolean sensitive = containsAny(s, "hapus", "delete", "uninstall", "kirim", "transfer", "bayar", "beli", "pesan", "posting", "publikasi", "telepon", "panggil");
        Intent intent = Intent.NONE;
        String normalized = original;
        float confidence = .35f;

        if (containsAny(s, "buka", "bukakan", "jalankan", "open", "launch")) {
            intent = Intent.OPEN_APP; confidence = .94f;
        } else if (containsAny(s, "klik", "tekan", "tap", "pilih")) {
            intent = Intent.CLICK; confidence = .90f;
        } else if (containsAny(s, "ketik", "tuliskan", "isi", "masukkan")) {
            intent = Intent.TYPE; confidence = .91f;
        } else if (containsAny(s, "baca", "bacakan", "lihat", "apa isi", "informasi layar")) {
            intent = Intent.READ; confidence = .89f;
        } else if (containsAny(s, "kembali", "back", "pulang")) {
            intent = Intent.NAVIGATE; confidence = .97f; normalized = "kembali";
        } else if (containsAny(s, "home", "beranda")) {
            intent = Intent.NAVIGATE; confidence = .97f; normalized = "home";
        } else if (containsAny(s, "scroll", "gulir", "geser")) {
            intent = Intent.SCROLL; confidence = .86f;
        }

        boolean needsConfirmation = sensitive || confidence < .55f;
        String explanation = needsConfirmation
                ? "Tindakan perlu pemeriksaan/konfirmasi sebelum eksekusi."
                : "Perintah dipahami dengan keyakinan tinggi.";
        return new Decision(original, normalized, intent, confidence, sensitive, needsConfirmation, explanation);
    }

    /** Lightweight synonym expansion used before the legacy planner. */
    public static String normalizeForPlanner(String input) {
        String s = normalize(input);
        if (s.isEmpty()) return s;
        List<String> out = new ArrayList<>(Arrays.asList(s.split("\\s+")));
        if (containsAny(s, "bukakan", "jalankan", "luncurkan")) out.add(0, "buka");
        if (containsAny(s, "tap", "tekanlah", "pilihkan")) out.add(0, "klik");
        if (containsAny(s, "bacakan", "tampilkan", "tunjukkan")) out.add(0, "baca");
        if (containsAny(s, "gulir", "geser", "turunkan")) out.add(0, "scroll");
        return join(out);
    }

    private static String normalize(String input) {
        String s = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        return s;
    }

    private static boolean containsAny(String s, String... terms) {
        for (String term : terms) if (s.equals(term) || s.contains(" " + term + " ") || s.startsWith(term + " ") || s.endsWith(" " + term)) return true;
        return false;
    }

    private static String join(List<String> words) {
        if (words == null || words.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String w : words) { if (w == null || w.isEmpty()) continue; if (b.length() > 0) b.append(' '); b.append(w); }
        return b.toString();
    }
}
