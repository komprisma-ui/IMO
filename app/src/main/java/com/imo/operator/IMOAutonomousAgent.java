package com.imo.operator;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded JARVIS-style loop: observe -> reason -> act -> verify -> re-plan. */
public final class IMOAutonomousAgent {
    public interface Callback {
        void onProgress(String message);
        void onSpeak(String message);
        void onFinished(boolean success, String message);
    }

    private static final int MAX_CYCLES = 8;
    private static final long CYCLE_TIMEOUT_MS = 30000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService worker = Executors.newSingleThreadExecutor();
    private final IMOConversationBrain brain;
    private final IMOEngine engine;
    private final IMOAccessibilityService service;
    private final IMOVisionCamera vision;
    private final Callback callback;
    private final IMOActionLoopGuard loopGuard = new IMOActionLoopGuard();
    private volatile boolean running;
    private int cycle;
    private String goal;

    public IMOAutonomousAgent(IMOConversationBrain brain, IMOEngine engine,
                              IMOAccessibilityService service, IMOVisionCamera vision,
                              Callback callback) {
        this.brain = brain;
        this.engine = engine;
        this.service = service;
        this.vision = vision;
        this.callback = callback;
    }

    public synchronized boolean isRunning() { return running; }

    public synchronized void start(String userGoal) {
        if (running) {
            callback.onSpeak("Saya masih menyelesaikan tugas sebelumnya. Tunggu sebentar.");
            return;
        }
        if (userGoal == null || userGoal.trim().isEmpty()) {
            callback.onFinished(false, "Tujuannya belum jelas.");
            return;
        }
        if (worker.isShutdown() || worker.isTerminated()) {
            worker = Executors.newSingleThreadExecutor();
        }
        goal = userGoal.trim();
        cycle = 0;
        loopGuard.reset();
        running = true;
        callback.onProgress("Mode JARVIS aktif: saya akan menyelesaikan tujuan ini dan memeriksa hasilnya.");
        nextCycle(0, null);
    }

    public synchronized void stop() {
        running = false;
        goal = null;
        loopGuard.reset();
        main.removeCallbacksAndMessages(null);
        if (worker != null) worker.shutdownNow();
    }

    private void nextCycle(long delay, String previousResult) {
        if (!running) return;
        if (cycle >= MAX_CYCLES) {
            finish(false, "Saya menghentikan tugas setelah batas percobaan tercapai agar tidak berputar tanpa kendali.");
            return;
        }
        Runnable task = () -> { if (running) runCycle(previousResult); };
        if (delay <= 0) worker.execute(task);
        else main.postDelayed(() -> { if (running) worker.execute(task); }, delay);
    }

    private void runCycle(String previousResult) {
        if (!running) return;
        cycle++;
        String screen = service == null ? "" : safeReadScreen();
        callback.onProgress("Siklus " + cycle + "/" + MAX_CYCLES + ": mengamati layar dan konteks visual, lalu menentukan langkah berikutnya.");
        try {
            String request = cycle == 1
                ? goal
                : "Lanjutkan tujuan ini: " + goal + ". Hasil langkah terakhir: " + safe(previousResult)
                  + ". Keadaan terbaru sudah dibaca. Jangan mengulang langkah yang sudah berhasil; tentukan langkah berikutnya yang paling tepat atau nyatakan tujuan selesai jika memang sudah tercapai.";

            IMOConversationBrain.Reply reply;
            if (vision != null && vision.hasFrame()) {
                IMOVisionReasoner.Reply visual = IMOVisionReasoner.think(brain.ai(), request, screen, vision.latestFrame());
                reply = new IMOConversationBrain.Reply(visual.text, visual.execute);
            } else {
                reply = brain.think(request, screen);
            }
            if (!running) return;
            if (reply == null || reply.text == null || reply.text.trim().isEmpty()) {
                nextCycle(250, "PENALARAN TIDAK MEMBERIKAN KEPUTUSAN YANG VALID");
                return;
            }

            if (!reply.execute) {
                verifyGoal(previousResult, reply.text, screen);
                return;
            }

            if (!loopGuard.allow(screen, reply.text)) {
                finish(false, "Saya mendeteksi langkah yang sama berulang tanpa kemajuan. Saya berhenti agar tidak terjebak dalam loop.");
                return;
            }

            final boolean[] callbackCalled = {false};
            engine.execute(reply.text, new IMOEngine.Callback() {
                @Override public void onProgress(String message) { callback.onProgress(message); }

                @Override public void onConfirmationRequired(String message) {
                    callbackCalled[0] = true;
                    callback.onSpeak(message);
                    finish(false, "Saya menunggu konfirmasi Anda sebelum melanjutkan tugas ini.");
                }

                @Override public void onFinished(String message, boolean success) {
                    callbackCalled[0] = true;
                    if (!running) return;
                    brain.rememberExecution(message);
                    String result = (success ? "LANGKAH SELESAI: " : "LANGKAH GAGAL: ") + message;
                    verifyGoal(result, reply.text, safeReadScreen());
                }
            });

            main.postDelayed(() -> {
                if (running && !callbackCalled[0]) {
                    finish(false, "Langkah terlalu lama tanpa hasil yang dapat diverifikasi. Saya berhenti dengan aman.");
                }
            }, CYCLE_TIMEOUT_MS);
        } catch (Exception e) {
            nextCycle(250, "PENALARAN GAGAL: " + safeMessage(e));
        }
    }

    /**
     * Separates planning from completion. The model must explicitly verify the
     * current screen/state before IMO declares the user's goal complete.
     */
    private void verifyGoal(String previousResult, String lastAction, String screen) {
        if (!running) return;
        worker.execute(() -> {
            if (!running) return;
            try {
                String prompt = "Tujuan pengguna: " + safe(goal)
                    + "\nHasil langkah terakhir: " + safe(previousResult)
                    + "\nPerintah terakhir: " + safe(lastAction)
                    + "\nKeadaan layar TERBARU:\n" + safe(screen)
                    + "\nNilai apakah tujuan benar-benar sudah tercapai. Jangan menganggap berhasil hanya karena perintah dikirim. Jika belum tercapai, gunakan CONTINUE. Jika terhalang atau keadaan tidak jelas, gunakan BLOCKED.";
                String raw = brain.ai().reason(IMOGoalVerifier.systemInstruction(), prompt, screen);
                IMOGoalVerifier.Result result = IMOGoalVerifier.parse(raw);
                if (!running) return;

                if (result.status == IMOGoalVerifier.Status.DONE) {
                    finish(true, result.message.isEmpty() ? "Tugas selesai dan hasilnya sudah diverifikasi." : result.message);
                } else if (result.status == IMOGoalVerifier.Status.CONTINUE) {
                    callback.onProgress("Verifikasi: tujuan belum selesai. Saya mencari langkah berikutnya.");
                    nextCycle(350, result.message.isEmpty() ? previousResult : result.message);
                } else if (result.status == IMOGoalVerifier.Status.BLOCKED) {
                    finish(false, result.message.isEmpty() ? "Saya terhenti karena keadaan perangkat belum cukup jelas untuk melanjutkan dengan aman." : result.message);
                } else {
                    finish(false, "Saya tidak dapat memverifikasi keadaan terakhir dengan cukup yakin, jadi saya berhenti dengan aman.");
                }
            } catch (Exception e) {
                finish(false, "Verifikasi tujuan gagal: " + safeMessage(e));
            }
        });
    }

    private String safeReadScreen() {
        try { return service.readScreen(); }
        catch (Exception e) { return "UI tidak dapat dibaca: " + safeMessage(e); }
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "tidak ada informasi" : value.trim();
    }

    private String safeMessage(Exception e) {
        String m = e == null ? "kesalahan tidak diketahui" : e.getMessage();
        return m == null || m.trim().isEmpty() ? "kesalahan tidak diketahui" : m.trim();
    }

    private synchronized void finish(boolean success, String message) {
        if (!running) return;
        running = false;
        goal = null;
        main.removeCallbacksAndMessages(null);
        callback.onFinished(success, message == null ? "Tugas selesai." : message);
    }
}
