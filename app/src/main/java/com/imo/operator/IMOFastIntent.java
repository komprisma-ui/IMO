package com.imo.operator;

import java.util.Locale;

/** Local zero-network router for common commands. Keeps simple interactions instant. */
public final class IMOFastIntent {
    private IMOFastIntent() {}

    public static IMOAction parse(String input) {
        if (input == null) return null;
        String n = normalize(input);
        if (n.isEmpty()) return null;
        if (matches(n, "kembali", "back", "mundur", "tolong kembali", "bisa kembali", "kembali dong")) return IMOAction.of(IMOAction.Type.BACK, null);
        if (matches(n, "home", "beranda", "layar utama", "ke beranda", "tolong ke beranda", "bisa ke beranda")) return IMOAction.of(IMOAction.Type.HOME, null);
        if (matches(n, "aplikasi terbaru", "recent apps", "recent app", "aplikasi terakhir", "tolong buka aplikasi terbaru")) return IMOAction.of(IMOAction.Type.RECENTS, null);
        if (matches(n, "notifikasi", "buka notifikasi", "lihat notifikasi", "notification", "tolong buka notifikasi", "bisa buka notifikasi")) return IMOAction.of(IMOAction.Type.NOTIFICATIONS, null);
        if (matches(n, "pengaturan cepat", "quick settings", "panel cepat", "panel pengaturan cepat", "buka pengaturan cepat")) return IMOAction.of(IMOAction.Type.QUICK_SETTINGS, null);
        if (matches(n, "geser atas", "geser ke atas", "scroll atas", "scroll ke atas", "gulir ke atas", "tolong geser ke atas")) return IMOAction.of(IMOAction.Type.SCROLL_UP, null);
        if (matches(n, "geser bawah", "geser ke bawah", "scroll bawah", "scroll ke bawah", "gulir ke bawah", "tolong geser ke bawah")) return IMOAction.of(IMOAction.Type.SCROLL_DOWN, null);
        if (matches(n, "besarkan volume", "volume naik", "naikkan volume", "keraskan suara", "tolong naikkan volume", "naikkan volume sedikit", "besarkan suara")) return IMOAction.of(IMOAction.Type.VOLUME_UP, null);
        if (matches(n, "kecilkan volume", "volume turun", "turunkan volume", "pelankan suara", "tolong turunkan volume", "kecilkan volume sedikit")) return IMOAction.of(IMOAction.Type.VOLUME_DOWN, null);
        if (matches(n, "mute", "senyapkan suara", "diamkan suara", "tolong mute", "bisukan suara")) return IMOAction.of(IMOAction.Type.MUTE, null);
        if (matches(n, "nyalakan senter", "hidupkan senter", "senter nyala", "flashlight on", "tolong nyalakan senter", "bisa nyalakan senter")) return IMOAction.of(IMOAction.Type.TORCH_ON, null);
        if (matches(n, "matikan senter", "senter mati", "flashlight off", "tolong matikan senter", "bisa matikan senter")) return IMOAction.of(IMOAction.Type.TORCH_OFF, null);
        String app = appAfter(n, "buka ");
        if (app == null) app = appAfter(n, "bukakan ");
        if (app == null) app = appAfter(n, "jalankan ");
        if (app == null) app = appAfter(n, "open ");
        if (app != null) return IMOAction.of(IMOAction.Type.OPEN_APP, app);
        return null;
    }

    private static String appAfter(String n, String prefix) {
        if (!n.startsWith(prefix)) return null;
        String v = stripPoliteSuffix(n.substring(prefix.length()).trim());
        if (v.isEmpty()) return null;
        if (v.equals("whatsapp") || v.equals("wa")) return "com.whatsapp";
        if (v.equals("chrome") || v.equals("browser")) return "com.android.chrome";
        if (v.equals("youtube") || v.equals("you tube")) return "com.google.android.youtube";
        if (v.equals("telegram")) return "org.telegram.messenger";
        if (v.equals("instagram") || v.equals("ig")) return "com.instagram.android";
        if (v.equals("facebook") || v.equals("fb")) return "com.facebook.katana";
        return v;
    }

    private static String stripPoliteSuffix(String v) {
        return v.replaceFirst("\\s+(dong|ya|yah|deh|saja)$", "").trim();
    }

    private static boolean matches(String n, String... values) {
        for (String v : values) if (n.equals(v)) return true;
        return false;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}\\s]", " ").replaceAll("\\s+", " ").trim();
    }
}
