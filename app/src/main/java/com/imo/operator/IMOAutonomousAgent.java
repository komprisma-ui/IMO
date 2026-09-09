package com.imo.operator;

import android.os.Handler;
import android.os.Looper;

/**
 * Bounded JARVIS-style goal loop: observe -> reason -> act -> observe -> re-plan.
 * The agent never bypasses IMOEngine, so existing confirmation and safety policy remain authoritative.
 */
public final class IMOAutonomousAgent {
    public interface Callback {
        void onProgress(String message);
        void onSpeak(String message);
        void onFinished(boolean success, String message);
    }

    private static final int MAX_CYCLES = 8;
    private static final long CYCLE_TIMEOUT_MS = 30000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final IMOConversationBrain brain;
    private final IMOEngine engine;
    private final IMOAccessibilityService service;
    private final Callback callback;
    private volatile boolean running;
    private int cycle;
    private String goal;

    public IMOAutonomousAgent(IMOConversationBrain brain, IMOEngine engine,
                              IMOAccessibilityService service, Callback callback) {
        this.brain = brain;
        this.engine = engine;
        this.service = service;
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
        goal = userGoal.trim();
        cycle = 0;
        running = true;
        callback.onProgress("Mode JARVIS aktif: saya akan menyelesaikan tujuan ini dan memeriksa hasilnya.");
        nextCycle(0, null);
    }

    public synchronized void stop() {
        running = false;
        goal = null;
    }

    private void nextCycle(long delay, String previousResult) {
        if (!running) return;
        if (cycle >= MAX_CYCLES) {
            finish(false, "Saya menghentikan tugas setelah batas percobaan tercapai agar tidak berputar tanpa kendali.");
            return;
        }
        if (delay <= 0) runCycle(previousResult);
        else main.postDelayed(() -> runCycle(previousResult), delay);
    }

    private void runCycle(String previousResult) {
        if (!running) return;
        cycle++;
        String screen = service == null ? "" : safeReadScreen();
        callback.onProgress("Siklus " + cycle + "/" + MAX_CYCLES + ": mengamati keadaan HP dan menentukan langkah berikutnya.");
        try {
            IMOConversationBrain.Reply reply = brain.thinkAgent(goal, screen, previousResult, cycle, MAX_CYCLES);
            if (!running) return;
            if (!reply.execute) {
                finish(true, reply.text);
                return;
            }
            final boolean[] callbackCalled = {false};
            engine.execute(reply.text, new IMOEngine.Callback() {
                @Override public void onProgress(String message) { callback.onProgress(message); }
                @Override public void onConfirmationRequired(String message) {
                    callbackCalled[0] = true;
                    callback.onSpeak(message);
                    // A confirmation is deliberately a pause, not a failure. The normal conversation
                    // service can consume the user's YA/BATAL response through IMOEngine.
                    finish(false, "Saya menunggu konfirmasi Anda sebelum melanjutkan tugas ini.");
                }
                @Override public void onFinished(String message, boolean success) {
                    callbackCalled[0] = true;
                    if (!running) return;
                    brain.rememberExecution(message);
                    if (!success) {
                        nextCycle(250, "LANGKAH GAGAL: " + message);
                    } else {
                        nextCycle(350, "LANGKAH SELESAI: " + message);
                    }
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

    private String safeReadScreen() {
        try { return service.readScreen(); }
        catch (Exception e) { return "UI tidak dapat dibaca: " + safeMessage(e); }
    }

    private String safeMessage(Exception e) {
        String m = e == null ? "kesalahan tidak diketahui" : e.getMessage();
        return m == null || m.trim().isEmpty() ? "kesalahan tidak diketahui" : m.trim();
    }

    private synchronized void finish(boolean success, String message) {
        if (!running) return;
        running = false;
        goal = null;
        callback.onFinished(success, message == null ? "Tugas selesai." : message);
    }
}
