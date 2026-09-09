package com.imo.operator;

/** Maps high-level device commands to the official Android device controller. */
public final class IMODeviceAction {
    private IMODeviceAction() {}
    public static String execute(String command) {
        if (command == null) return "";
        String s = command.toLowerCase().trim();
        if (s.contains("senter") && (s.contains("nyala") || s.contains("hidup") || s.contains("on"))) return "TORCH_ON";
        if (s.contains("senter") && (s.contains("mati") || s.contains("off"))) return "TORCH_OFF";
        if (s.contains("kunci layar") || s.equals("kunci hp") || s.contains("lock screen")) return "LOCK_SCREEN";
        if (s.contains("nyalakan layar") || s.contains("bangunkan layar") || s.contains("wake screen")) return "WAKE_SCREEN";
        if (s.contains("wifi") && (s.contains("buka") || s.contains("atur") || s.contains("pengaturan"))) return "WIFI_SETTINGS";
        if (s.contains("bluetooth") && (s.contains("buka") || s.contains("atur") || s.contains("pengaturan"))) return "BLUETOOTH_SETTINGS";
        if (s.contains("pengaturan") || s.contains("settings")) return "SYSTEM_SETTINGS";
        if (s.contains("volume") && (s.contains("naik") || s.contains("besar"))) return "VOLUME_UP";
        if (s.contains("volume") && (s.contains("turun") || s.contains("kecil"))) return "VOLUME_DOWN";
        if (s.contains("mute") || s.contains("senyap")) return "MUTE";
        return "NONE";
    }
}
