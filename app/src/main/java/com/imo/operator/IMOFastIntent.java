package com.imo.operator;

import java.util.Locale;

/** Local zero-network router for common commands. Keeps simple interactions instant. */
public final class IMOFastIntent {
    private IMOFastIntent() {}

    public static IMOAction parse(String input) {
        if (input == null) return null;
        String n = normalize(input);
        if (n.isEmpty()) return null;

        if (is(n, "kembali", "back", "mundur")) return IMOAction.of(IMOAction.Type.BACK, null);
        if (is(n, "home", "beranda", "layar utama", "ke beranda")) return IMOAction.of(IMOAction.Type.HOME, null);
        if (is(n, "aplikasi terbaru", "recent apps", "recent app", "aplikasi terakhir")) return IMOAction.of(IMOAction.Type.RECENTS, null);
        if (is(n, "notifikasi", "buka notifikasi", "lihat notifikasi", "notification")) return IMOAction.of(IMOAction.Type.NOTIFICATIONS, null);
        if (is(n, "pengaturan cepat", "quick settings", "panel cepat", "panel pengaturan cepat")) return IMOAction.of(IMOAction.Type.QUICK_SETTINGS, null);
        if (is(n, "geser atas", "geser ke atas", "scroll atas", "scroll ke atas", "gulir ke atas")) return IMOAction.of(IMOAction.Type.SCROLL_UP, null);
        if (is(n, "geser bawah", "geser ke bawah", "scroll bawah", "scroll ke bawah", "gulir ke bawah")) return IMOAction.of(IMOAction.Type.SCROLL_DOWN, null);
        if (is(n, "besarkan volume", "volume naik", "naikkan volume", "keraskan suara")) return IMOAction.of(IMOAction.Type.VOLUME_UP, null);
        if (is(n, "kecilkan volume", "volume turun", "turunkan volume", "pelankan suara")) return IMOAction.of(IMOAction.Type.VOLUME_DOWN, null);
        if (is(n, "mute", "senyapkan suara", "diamkan suara")) return IMOAction.of(IMOAction.Type.MUTE, null);
        if (is(n, "nyalakan senter", "hidupkan senter", "senter nyala", "flashlight on")) return IMOAction.of(IMOAction.Type.TORCH_ON, null);
        if (is(n, "matikan senter", "senter mati", "flashlight off")) return IMOAction.of(IMOAction.Type.TORCH_OFF, null);

        String app = appAfter(n, "buka ");
        if (app == null) app = appAfter(n, "bukakan ");
        if (app == null) app = appAfter(n, "jalankan ");
        if (app == null) app = appAfter(n, "open ");
        if (app != null) return IMOAction.of(IMOAction.Type.OPEN_APP, app);
        return null;
    }

    private static String appAfter(String n, String prefix) {
        if (!n.startsWith(prefix)) return null;
        String v = n.substring(prefix.length()).trim();
        if (v.isEmpty()) return null;
        if (v.equals("whatsapp") || v.equals("wa")) return "com.whatsapp";
        if (v.equals("chrome") || v.equals("browser")) return "com.android.chrome";
        if (v.equals("youtube") || v.equals("you tube")) return "com.google.android.youtube";
        if (v.equals("telegram")) return "org.telegram.messenger";
        if (v.equals("instagram") || v.equals("ig")) return "com.instagram.android";
        if (v.equals("facebook") || v.equals("fb")) return "com.facebook.katana";
        return v;
    }

    private static boolean is(String n, String... values) {
        for (String v : values) if (n.equals(v)) return true;
        return false;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}\\s]", " ").replaceAll("\\s+", " ").trim();
    }
}
