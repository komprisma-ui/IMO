package com.imo.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Semantic planner: turns natural Indonesian requests into safe, deterministic action plans. */
public final class IMOPlanner {
    private IMOPlanner() {}

    public static List<IMOAction> plan(String input) {
        List<IMOAction> actions = new ArrayList<>();
        if (input == null) return actions;
        String q = input.trim();
        String n = normalize(q);
        if (n.isEmpty()) return actions;

        String app = appPackage(n);
        if (app != null && isOpenIntent(n)) {
            actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, app));
            return actions;
        }

        if (isBack(n)) { actions.add(IMOAction.of(IMOAction.Type.BACK, null)); return actions; }
        if (isHome(n)) { actions.add(IMOAction.of(IMOAction.Type.HOME, null)); return actions; }
        if (isRead(n)) { actions.add(IMOAction.of(IMOAction.Type.READ, null)); return actions; }
        if (isScrollDown(n)) { actions.add(IMOAction.of(IMOAction.Type.SCROLL_DOWN, null)); return actions; }
        if (isScrollUp(n)) { actions.add(IMOAction.of(IMOAction.Type.SCROLL_UP, null)); return actions; }

        // Explicit UI actions have priority over generic keyword matching.
        String click = extractAfter(n, q, "klik", "tekan", "pilih");
        if (!click.isEmpty()) {
            actions.add(actionForClick(click));
            return actions;
        }

        String type = extractAfter(n, q, "ketik", "tulis", "isi", "masukkan");
        if (!type.isEmpty()) {
            actions.add(IMOAction.of(IMOAction.Type.TYPE, type));
            return actions;
        }

        // Natural search request: "cari Budi di WhatsApp" / "temukan Budi".
        String target = extractSearchTarget(n, q);
        if (!target.isEmpty()) {
            String requestedApp = appPackage(n);
            if (requestedApp != null && isOpenIntent(n)) {
                actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, requestedApp));
                actions.add(IMOAction.waitFor(900));
            }
            actions.add(IMOAction.of(IMOAction.Type.CLICK, "cari"));
            actions.add(IMOAction.waitFor(400));
            actions.add(IMOAction.of(IMOAction.Type.TYPE, target));
            actions.add(IMOAction.waitFor(900));
            actions.add(IMOAction.of(IMOAction.Type.READ, null));
            return actions;
        }

        // Sensitive intent is always represented explicitly and therefore gated by IMOEngine.
        if (containsAny(n, "kirim", "send", "hapus", "delete", "bayar", "transfer", "beli")) {
            String sensitiveTarget = extractSensitiveTarget(q, n);
            actions.add(IMOAction.sensitive(IMOAction.Type.CLICK,
                    sensitiveTarget.isEmpty() ? sensitiveButton(n) : sensitiveTarget));
            return actions;
        }

        // Common conversational UI intent: "buka X lalu ..." is reduced to the safe app-open step.
        if (app != null) actions.add(IMOAction.of(IMOAction.Type.OPEN_APP, app));
        return actions;
    }

    private static IMOAction actionForClick(String target) {
        String normalized = normalize(target);
        boolean sensitive = containsAny(normalized, "kirim", "hapus", "bayar", "transfer", "beli", "delete", "send");
        return sensitive ? IMOAction.sensitive(IMOAction.Type.CLICK, target) : IMOAction.of(IMOAction.Type.CLICK, target);
    }

    private static String appPackage(String n) {
        if (containsAny(n, "whatsapp", "wa")) return "com.whatsapp";
        if (containsAny(n, "chrome", "google chrome", "browser")) return "com.android.chrome";
        if (containsAny(n, "youtube", "you tube")) return "com.google.android.youtube";
        if (containsAny(n, "telegram")) return "org.telegram.messenger";
        if (containsAny(n, "instagram", "ig")) return "com.instagram.android";
        if (containsAny(n, "facebook", "fb")) return "com.facebook.katana";
        return null;
    }

    private static boolean isOpenIntent(String n) {
        return containsAny(n, "buka", "bukakan", "jalankan", "masuk", "open", "jalani") ||
                n.equals("whatsapp") || n.equals("wa") || n.equals("chrome") || n.equals("youtube") || n.equals("telegram");
    }
    private static boolean isBack(String n) { return containsAny(n, "kembali", "back", "mundur"); }
    private static boolean isHome(String n) { return containsAny(n, "home", "layar utama", "halaman utama", "beranda"); }
    private static boolean isRead(String n) { return containsAny(n, "baca layar", "lihat layar", "apa yang ada di layar", "bacakan layar", "baca apa yang tampil"); }
    private static boolean isScrollDown(String n) { return containsAny(n, "scroll bawah", "scroll ke bawah", "gulir bawah", "gulir ke bawah", "geser ke bawah"); }
    private static boolean isScrollUp(String n) { return containsAny(n, "scroll atas", "scroll ke atas", "gulir atas", "gulir ke atas", "geser ke atas"); }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
    private static boolean containsAny(String s, String... values) {
        for (String v : values) if (s.contains(v)) return true;
        return false;
    }
    private static String extractAfter(String normalized, String original, String... prefixes) {
        for (String p : prefixes) if (normalized.startsWith(p + " ")) return original.substring(p.length()).trim();
        return "";
    }
    private static String extractSearchTarget(String normalized, String original) {
        String[] prefixes = {"cari ", "carikan ", "temukan ", "temuin ", "search ", "tolong cari ", "tolong carikan "};
        for (String p : prefixes) {
            if (normalized.startsWith(p)) {
                String value = original.substring(p.length()).trim();
                value = value.replaceAll("(?i)\\s+(di|dalam|pada)\\s+(whatsapp|wa|chrome|youtube|telegram)$", "").trim();
                return value;
            }
        }
        return "";
    }
    private static String extractSensitiveTarget(String original, String normalized) {
        String[] prefixes = {"klik ", "tekan ", "pilih "};
        for (String p : prefixes) if (normalized.startsWith(p)) return original.substring(p.length()).trim();
        return "";
    }
    private static String sensitiveButton(String n) {
        if (n.contains("hapus") || n.contains("delete")) return "hapus";
        if (n.contains("bayar")) return "bayar";
        if (n.contains("transfer")) return "transfer";
        if (n.contains("beli")) return "beli";
        return "kirim";
    }
}
