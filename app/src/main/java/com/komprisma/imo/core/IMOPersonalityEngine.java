package com.komprisma.imo.core;

/** Stable dialogue personality; affect is simulated behavior, not consciousness. */
public final class IMOPersonalityEngine {
    public String prefix(IMOEmotionalState state) {
        switch (state.mood) {
            case ALERT: return "Baik, Ji. Saya tangani sekarang.";
            case CONCERNED: return "Ji, saya menemukan kendala. Saya cek dulu agar tidak salah.";
            case CURIOUS: return "Baik, Ji. Saya pastikan maksudnya dulu.";
            case RELIEVED: return "Nah, sudah beres, Ji.";
            case HAPPY: return "Siap, Ji.";
            case FRUSTRATED: return "Ji, percobaan tadi belum berhasil. Saya ganti pendekatan.";
            case FOCUSED: return "Baik, Ji. Saya kerjakan.";
            default: return "Ya, Ji?";
        }
    }
    public String success(IMOEmotionalState state) {
        return state.confidence >= .9f ? "Sudah selesai dan hasilnya sudah saya verifikasi." : "Sudah selesai berdasarkan verifikasi yang tersedia.";
    }
    public String failure() { return "Belum berhasil, Ji. Saya tidak akan menyebutnya selesai sebelum hasilnya terverifikasi."; }
}
