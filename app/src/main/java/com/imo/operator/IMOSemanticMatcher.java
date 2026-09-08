package com.imo.operator;

import java.util.Locale;

/** Lightweight Indonesian/English semantic matching for Android UI labels. */
public final class IMOSemanticMatcher {
    private IMOSemanticMatcher() {}

    public static String canonical(String value) {
        if (value == null) return "";
        String s = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u00c0-\\u024f\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        s = s.replace("pencarian", "cari").replace("search", "cari")
                .replace("temukan", "cari").replace("temuin", "cari")
                .replace("kembali", "back").replace("mundur", "back")
                .replace("beranda", "home").replace("layar utama", "home")
                .replace("hapus", "delete").replace("buang", "delete")
                .replace("kirimkan", "kirim").replace("send", "kirim");
        return s;
    }

    public static int score(String query, IMOUISnapshot.Node node) {
        if (node == null) return 0;
        String q = canonical(query), label = canonical(node.label());
        if (q.isEmpty() || label.isEmpty()) return 0;
        int score = 0;
        if (label.equals(q)) score += 120;
        if (label.contains(q)) score += 70;
        if (q.contains(label) && label.length() > 2) score += 40;
        String[] tokens = q.split(" ");
        for (String token : tokens) if (token.length() > 1 && label.contains(token)) score += 15;
        if (node.clickable) score += 20;
        if (node.enabled) score += 8;
        if (node.editable && (q.contains("cari") || q.contains("ketik"))) score += 15;
        if (node.description != null && !node.description.isEmpty()) score += 3;
        return score;
    }
}
