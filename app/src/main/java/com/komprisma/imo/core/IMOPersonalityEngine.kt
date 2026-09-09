package com.komprisma.imo.core

/** Stable dialogue personality; affect is simulated behavior, not a claim of consciousness. */
class IMOPersonalityEngine {
    fun prefix(state: IMOEmotionalState): String = when (state.mood) {
        IMOEmotionalState.Mood.ALERT -> "Baik, Ji. Saya tangani sekarang."
        IMOEmotionalState.Mood.CONCERNED -> "Ji, saya menemukan kendala. Saya cek dulu agar tidak salah."
        IMOEmotionalState.Mood.CURIOUS -> "Baik, Ji. Saya pastikan maksudnya dulu."
        IMOEmotionalState.Mood.RELIEVED -> "Nah, sudah beres, Ji."
        IMOEmotionalState.Mood.HAPPY -> "Siap, Ji."
        IMOEmotionalState.Mood.FRUSTRATED -> "Ji, percobaan tadi belum berhasil. Saya ganti pendekatan."
        IMOEmotionalState.Mood.FOCUSED -> "Baik, Ji. Saya kerjakan."
        IMOEmotionalState.Mood.CALM -> "Ya, Ji?"
    }
    fun success(state: IMOEmotionalState) = if (state.confidence >= .9f) "Sudah selesai dan hasilnya sudah saya verifikasi." else "Sudah selesai berdasarkan verifikasi yang tersedia."
    fun failure() = "Belum berhasil, Ji. Saya tidak akan menyebutnya selesai sebelum hasilnya terverifikasi."
}
