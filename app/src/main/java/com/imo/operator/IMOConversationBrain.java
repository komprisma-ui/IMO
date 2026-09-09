package com.imo.operator;

import android.content.Context;
import com.komprisma.imo.core.IMOContextMemory;
import com.komprisma.imo.core.IMOEmotionalState;
import com.komprisma.imo.core.IMOPersonalityEngine;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * High-level cognitive layer: understands intent, context and screen state, then emits
 * deterministic commands for the device executor. The model never directly controls the OS.
 */
public final class IMOConversationBrain {
    public static final class Reply {
        public final String text;
        public final boolean execute;
        public Reply(String text, boolean execute) { this.text = text; this.execute = execute; }
    }

    private final IMOAIClient ai;
    private final Deque<String> history = new ArrayDeque<>();
    private final IMOContextMemory contextMemory = new IMOContextMemory();
    private final IMOPersonalityEngine personality = new IMOPersonalityEngine();
    private final com.komprisma.imo.core.IMOEmotionEngine emotion = new com.komprisma.imo.core.IMOEmotionEngine();
    private final int maxTurns = 14;

    private static final String SYSTEM =
        "Anda adalah IMO (Intelligent Mobile Operator), otak operator HP berbahasa Indonesia. " +
        "Anda bukan chatbot pasif: pahami tujuan pengguna, amati keadaan layar, susun langkah paling tepat, " +
        "dan serahkan eksekusi hanya kepada executor IMO.\n" +
        "PRINSIP UTAMA: observasi -> pahami tujuan -> rencanakan -> jalankan -> verifikasi -> koreksi bila perlu. " +
        "Jangan mengarang keadaan perangkat dan jangan pernah menyatakan aksi berhasil sebelum executor memverifikasinya.\n" +
        "KONTEKS: gunakan riwayat dan hasil eksekusi untuk memahami 'itu', 'yang tadi', 'di sana', 'lanjutkan', " +
        "'kembali', dan rujukan lain. Pertahankan aplikasi/objek yang sedang dibahas kecuali pengguna mengubah tujuan.\n" +
        "LAYAR: gunakan teks/UI yang diberikan untuk memilih target yang benar. Jika ada beberapa kandidat yang ambigu, " +
        "minta satu klarifikasi singkat. Jangan menebak akun, kontak, penerima, nominal, atau target yang tidak jelas.\n" +
        "EKSEKUSI: untuk tindakan perangkat keluarkan tepat satu baris ACTION: diikuti perintah natural bahasa Indonesia. " +
        "Perintah ACTION boleh berisi beberapa langkah yang dihubungkan dengan 'lalu' atau 'kemudian'. " +
        "Jangan gunakan JSON, markdown, code fence, atau awalan lain. Untuk jawaban percakapan gunakan SAY:.\n" +
        "SAFETY: transfer, pembayaran, pembelian, pengiriman pesan, panggilan, penghapusan permanen, uninstall, " +
        "dan tindakan sensitif lain harus melewati mekanisme konfirmasi executor. Jangan meminta, membaca, atau mengekspos " +
        "password, PIN, OTP, token, atau rahasia. Jangan mencoba melewati lock screen atau pembatas keamanan Android.\n" +
        "Jika tindakan pertama gagal, executor dapat melakukan recovery. Anda boleh menyarankan strategi alternatif " +
        "hanya bila didukung keadaan layar yang terlihat.";

    public IMOConversationBrain(Context context) { ai = new IMOAIClient(context); }
    public boolean aiConfigured() { return ai.configured(); }
    public IMOAIClient ai() { return ai; }

    public synchronized Reply think(String user, String screen) throws Exception {
        if (user == null || user.trim().isEmpty()) return new Reply("Saya mendengarkan. Silakan lanjutkan.", false);
        String cleanUser = user.trim();
        boolean urgent = cleanUser.matches("(?i).*\\b(segera|darurat|urgent|cepat|sekarang juga)\\b.*");
        boolean ambiguous = cleanUser.matches("(?i).*\\b(itu|yang tadi|di sana|yang ini)\\b.*") && (screen == null || screen.trim().isEmpty());
        emotion.onUserInput(urgent, ambiguous);
        contextMemory.add("USER", cleanUser);
        history.addLast("USER: " + cleanUser);
        trimHistory();
        if (!ai.configured()) return offline(cleanUser);

        StringBuilder context = new StringBuilder();
        for (String h : history) context.append(h).append('\n');
        context.append("STATE: ").append(emotion.snapshot()).append('\n');
        context.append("RECENT CONTEXT: ");
        for (IMOContextMemory.Event event : contextMemory.recent(6)) context.append(event.kind).append('=').append(event.summary).append("; ");
        String answer = ai.reason(SYSTEM, context.toString(), screen == null ? "" : screen);
        history.addLast("IMO: " + answer);
        contextMemory.add("IMO", answer);
        trimHistory();
        return parseReply(answer);
    }

    private Reply parseReply(String answer) {
        String cleaned = answer == null ? "" : answer.trim();
        cleaned = cleaned.replaceFirst("^```(?:text|plaintext)?\\s*", "")
                         .replaceFirst("\\s*```$", "").trim();

        if (cleaned.regionMatches(true, 0, "ACTION:", 0, 7)) {
            String body = cleaned.substring(7).trim();
            String[] lines = body.split("\\r?\\n");
            StringBuilder command = new StringBuilder();
            for (String line : lines) {
                String x = line.trim();
                if (x.isEmpty()) continue;
                if (x.regionMatches(true, 0, "ACTION:", 0, 7)) x = x.substring(7).trim();
                if (x.isEmpty()) continue;
                if (command.length() > 0) command.append(" lalu ");
                command.append(x);
            }
            return command.length() == 0
                ? new Reply("Saya belum memahami tindakan yang diminta.", false)
                : new Reply(command.toString(), true);
        }
        if (cleaned.regionMatches(true, 0, "SAY:", 0, 4)) {
            String speech = cleaned.substring(4).trim();
            return new Reply(speech.isEmpty() ? "Saya mendengarkan." : speech, false);
        }
        return new Reply(cleaned.isEmpty() ? "Saya belum menerima jawaban yang jelas dari AI." : cleaned, false);
    }

    public synchronized void rememberExecution(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String clean = text.trim();
        boolean success = clean.matches("(?i).*(berhasil|selesai|terverifikasi|sukses).*" ) && !clean.matches("(?i).*(gagal|tidak berhasil|belum berhasil).*" );
        if (success) emotion.onTaskSuccess(); else emotion.onTaskFailure();
        contextMemory.add("RESULT", clean);
        history.addLast("RESULT: " + clean);
        trimHistory();
    }

    public synchronized IMOEmotionalState emotionalState() { return emotion.snapshot(); }

    public synchronized String personalityPrefix() { return personality.prefix(emotion.snapshot()); }

    public synchronized void clear() { history.clear(); contextMemory.clear(); }
    private void trimHistory() { while (history.size() > maxTurns * 2) history.removeFirst(); }

    private Reply offline(String user) {
        String n = user.toLowerCase();
        if (n.contains("siapa kamu") || n.contains("kamu siapa"))
            return new Reply("Saya IMO, operator HP Anda. Saya dapat memahami perintah, membaca layar, dan menjalankan tindakan yang diizinkan.", false);
        if (n.contains("terima kasih") || n.contains("makasih"))
            return new Reply("Sama-sama, Ji. Saya siap melanjutkan.", false);
        if (n.contains("halo") || n.equals("hai") || n.equals("hi"))
            return new Reply("Halo, Ji. Saya siap mendengarkan.", false);
        if (n.contains("apa yang bisa kamu lakukan"))
            return new Reply("Saya dapat membuka aplikasi, membaca layar, mengetik, menavigasi, mengatur fungsi perangkat, dan menjalankan rangkaian tindakan dengan verifikasi serta konfirmasi untuk tindakan sensitif.", false);
        return new Reply("Saya masih dapat menjalankan perintah lokal, tetapi penalaran bahasa bebas dan konteks mendalam memerlukan AI Cerdas. Silakan konfigurasi AI Cerdas.", false);
    }
}
