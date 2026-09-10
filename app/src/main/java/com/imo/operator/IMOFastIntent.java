package com.imo.operator;

import java.util.Locale;

/** Local zero-network intent router. Handles high-confidence Indonesian commands without cloud reasoning. */
public final class IMOFastIntent {
    private IMOFastIntent() {}

    public static IMOAction parse(String input) {
        if (input == null) return null;
        String raw = input.trim();
        if (raw.isEmpty()) return null;
        String n = normalize(raw);
        if (n.isEmpty() || isNegative(n)) return null;
        if (hasAny(n, "kembali", "back", "mundur", "ke halaman sebelumnya", "kembali ke halaman sebelumnya")) return IMOAction.of(IMOAction.Type.BACK, null);
        if (hasAny(n, "home", "beranda", "layar utama", "ke beranda", "ke layar utama", "pulang ke beranda")) return IMOAction.of(IMOAction.Type.HOME, null);
        if (hasAny(n, "aplikasi terbaru", "recent apps", "recent app", "aplikasi terakhir", "buka aplikasi terbaru")) return IMOAction.of(IMOAction.Type.RECENTS, null);
        if (hasAny(n, "notifikasi", "buka notifikasi", "lihat notifikasi", "cek notifikasi", "notification")) return IMOAction.of(IMOAction.Type.NOTIFICATIONS, null);
        if (hasAny(n, "pengaturan cepat", "quick settings", "panel cepat", "panel pengaturan cepat")) return IMOAction.of(IMOAction.Type.QUICK_SETTINGS, null);
        if (hasAny(n, "geser atas", "geser ke atas", "scroll atas", "scroll ke atas", "gulir ke atas", "naik ke atas")) return IMOAction.of(IMOAction.Type.SCROLL_UP, null);
        if (hasAny(n, "geser bawah", "geser ke bawah", "scroll bawah", "scroll ke bawah", "gulir ke bawah", "turun ke bawah")) return IMOAction.of(IMOAction.Type.SCROLL_DOWN, null);
        if (hasAny(n, "besarkan volume", "volume naik", "naikkan volume", "keraskan suara", "besarkan suara", "naikkan suara", "tambah volume", "volume lebih besar")) return IMOAction.of(IMOAction.Type.VOLUME_UP, null);
        if (hasAny(n, "kecilkan volume", "volume turun", "turunkan volume", "pelankan suara", "kecilkan suara", "turunkan suara", "kurangi volume", "volume lebih kecil")) return IMOAction.of(IMOAction.Type.VOLUME_DOWN, null);
        if (hasAny(n, "mute", "senyapkan suara", "diamkan suara", "bisukan suara", "matikan suara")) return IMOAction.of(IMOAction.Type.MUTE, null);
        if (hasAny(n, "nyalakan senter", "hidupkan senter", "senter nyala", "senter hidup", "flashlight on", "aktifkan senter")) return IMOAction.of(IMOAction.Type.TORCH_ON, null);
        if (hasAny(n, "matikan senter", "senter mati", "senter padam", "flashlight off", "nonaktifkan senter")) return IMOAction.of(IMOAction.Type.TORCH_OFF, null);
        if (hasAny(n, "buka wifi", "buka wi fi", "pengaturan wifi", "pengaturan wi fi", "setelan wifi", "atur wifi", "wifi settings")) return IMOAction.of(IMOAction.Type.OPEN_WIFI_SETTINGS, null);
        if (hasAny(n, "buka bluetooth", "pengaturan bluetooth", "setelan bluetooth", "atur bluetooth", "bluetooth settings")) return IMOAction.of(IMOAction.Type.OPEN_BLUETOOTH_SETTINGS, null);
        if (hasAny(n, "buka pengaturan", "buka setelan", "pengaturan hp", "setelan hp", "settings", "pengaturan ponsel")) return IMOAction.of(IMOAction.Type.OPEN_SYSTEM_SETTINGS, null);
        String app = appTarget(n);
        if (app != null) return IMOAction.of(IMOAction.Type.OPEN_APP, app);
        return null;
    }

    private static boolean isNegative(String n) {
        return n.equals("jangan") || n.startsWith("jangan ") || n.startsWith("tidak usah ") || n.startsWith("tak usah ") || n.startsWith("ga usah ") || n.startsWith("gak usah ") || n.startsWith("nggak usah ") || n.startsWith("jangan dulu ");
    }

    private static String appTarget(String n) {
        String[] prefixes = {"buka ", "bukakan ", "jalankan ", "open ", "tolong buka ", "tolong bukakan ", "bisa buka ", "coba buka ", "masuk ke ", "buka aplikasi "};
        for (String prefix : prefixes) {
            if (!n.startsWith(prefix)) continue;
            String v = stripPoliteSuffix(n.substring(prefix.length()).trim());
            if (v.isEmpty()) return null;
            String known = knownPackage(v);
            return known == null ? v : known;
        }
        return null;
    }

    private static String knownPackage(String v) {
        if (v.equals("whatsapp") || v.equals("wa") || v.equals("what sap") || v.equals("whats app") || v.equals("watsapp") || v.equals("watshap") || v.equals("whatsap")) return "com.whatsapp";
        if (v.equals("chrome") || v.equals("browser") || v.equals("google chrome")) return "com.android.chrome";
        if (v.equals("youtube") || v.equals("you tube") || v.equals("yu tub") || v.equals("yutub")) return "com.google.android.youtube";
        if (v.equals("telegram") || v.equals("tele gram")) return "org.telegram.messenger";
        if (v.equals("instagram") || v.equals("ig") || v.equals("insta") || v.equals("instagraman")) return "com.instagram.android";
        if (v.equals("facebook") || v.equals("fb") || v.equals("fesbuk") || v.equals("face book")) return "com.facebook.katana";
        if (v.equals("gmail") || v.equals("g mail") || v.equals("email google")) return "com.google.android.gm";
        if (v.equals("google maps") || v.equals("maps") || v.equals("map")) return "com.google.android.apps.maps";
        if (v.equals("play store") || v.equals("google play") || v.equals("playstore")) return "com.android.vending";
        if (v.equals("kamera") || v.equals("camera") || v.equals("camera hp")) return "com.android.camera2";
        return null;
    }

    private static String stripPoliteSuffix(String v) {
        String out = v.replaceFirst("\\s+(dong|ya|yah|deh|saja|sekarang|dulu|sebentar)$", "").trim();
        return out.replaceFirst("\\s+(tolong|please)$", "").trim();
    }

    private static boolean hasAny(String n, String... values) {
        for (String v : values) if (n.equals(v) || n.contains(" " + v + " ") || n.startsWith(v + " ") || n.endsWith(" " + v)) return true;
        return false;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}\\s]", " ").replaceAll("\\s+", " ").trim();
    }
}
