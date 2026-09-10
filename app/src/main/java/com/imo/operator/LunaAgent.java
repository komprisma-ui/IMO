package com.imo.operator;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Observe -> plan -> act -> verify -> recover loop for LUNA. */
public final class LunaAgent {
    public interface Callback {
        void status(String text);
        boolean confirm(JSONObject plan);
    }

    private final MainActivity activity;
    private final LunaAccessibilityService service;
    private final SecureKeyStore keyStore;
    private final Callback callback;
    private static final int MAX_STEPS = 10;

    public LunaAgent(MainActivity activity, LunaAccessibilityService service, SecureKeyStore keyStore, Callback callback) {
        this.activity = activity;
        this.service = service;
        this.keyStore = keyStore;
        this.callback = callback;
    }

    public void run(String command) {
        try {
            String api = keyStore.load();
            OpenAIClient ai = new OpenAIClient(api, "gpt-5.6-luna");
            Set<String> recentActions = new HashSet<>();
            String lastAction = "";

            for (int step = 1; step <= MAX_STEPS; step++) {
                if (shouldStop()) return;
                callback.status("👁 Mengamati layar — langkah " + step + "/" + MAX_STEPS + "…");
                String image = capture();
                String snapshot = service.snapshot();

                String prompt = command + "\n\nMODE AGENT: langkah " + step + " dari maksimal " + MAX_STEPS + ". " +
                        "Ini adalah siklus OBSERVE → PLAN → ACT → VERIFY. Amati keadaan TERKINI dari accessibility dan screenshot. " +
                        "Tentukan HANYA SATU tindakan berikutnya. Setelah tindakan dilakukan, siklus berikutnya akan memverifikasi hasilnya. " +
                        "Jika tujuan SUDAH tercapai, actions harus kosong. Jangan mengulang tindakan yang sudah berhasil. " +
                        "Tindakan terakhir: " + lastAction + ". Hindari tindakan yang identik tanpa alasan kuat. " +
                        "Jika elemen tidak ada di accessibility tree tetapi terlihat jelas pada screenshot, gunakan CLICK_POINT. " +
                        "Jangan mengarang koordinat. Tindakan sensitif (kirim, hapus, beli, transfer, ubah keamanan, atau dampak permanen) wajib confirm=true. " +
                        "Untuk actions kosong, gunakan speak untuk menjelaskan hasil dalam Bahasa Indonesia.";

                JSONObject plan = new JSONObject(ai.plan(prompt, snapshot, image));
                JSONArray actions = plan.optJSONArray("actions");
                if (actions == null || actions.length() == 0) {
                    callback.status("✓ " + plan.optString("speak", "Tujuan selesai atau tidak ada tindakan yang diperlukan."));
                    return;
                }

                JSONObject action = actions.getJSONObject(0);
                String signature = signature(action);
                if (recentActions.contains(signature) && !"WAIT".equals(action.optString("type"))) {
                    callback.status("LUNA mendeteksi pengulangan tindakan. Mengamati ulang…");
                    recentActions.clear();
                    recentActions.add(signature);
                    lastAction = signature;
                    continue;
                }

                if (plan.optBoolean("confirm", false)) {
                    callback.status("⚠ Meminta konfirmasi untuk tindakan sensitif…");
                    if (!callback.confirm(plan)) {
                        callback.status("Tindakan dibatalkan pengguna.");
                        return;
                    }
                }

                recentActions.add(signature);
                lastAction = signature;
                callback.status("⚙ Menjalankan: " + action.optString("type") + "…");
                boolean ok = execute(action);
                callback.status("Agent langkah " + step + ": " + action.optString("type") + " → " + (ok ? "berhasil" : "gagal"));

                if (!ok) {
                    if (shouldStop()) return;
                    callback.status("↻ Langkah gagal. LUNA mengamati ulang untuk pemulihan…");
                    String recoveryImage = capture();
                    String recoverySnapshot = service.snapshot();
                    JSONObject recovery = new JSONObject(ai.plan(command +
                            "\n\nPEMULIHAN: tindakan sebelumnya gagal (" + action.optString("type") + "). " +
                            "Amati layar TERKINI dan pilih SATU cara alternatif. Jangan mengulang tindakan gagal yang sama. " +
                            "Tujuan akhir tetap harus tercapai dan hasilnya harus dapat diverifikasi pada siklus berikutnya.",
                            recoverySnapshot, recoveryImage));
                    JSONArray ra = recovery.optJSONArray("actions");
                    if (ra == null || ra.length() == 0) {
                        callback.status("LUNA tidak menemukan langkah pemulihan yang aman.");
                        return;
                    }
                    if (recovery.optBoolean("confirm", false) && !callback.confirm(recovery)) return;
                    JSONObject retry = ra.getJSONObject(0);
                    String retrySignature = signature(retry);
                    if (recentActions.contains(retrySignature) && !"WAIT".equals(retry.optString("type"))) {
                        callback.status("LUNA menghentikan pengulangan setelah kegagalan.");
                        return;
                    }
                    recentActions.add(retrySignature);
                    boolean recovered = execute(retry);
                    callback.status("↻ Pemulihan: " + retry.optString("type") + " → " + (recovered ? "berhasil" : "gagal"));
                    if (!recovered) {
                        callback.status("LUNA berhenti: pemulihan gagal.");
                        return;
                    }
                }

                if (!pauseResponsive(450)) return;
            }
            callback.status("LUNA berhenti setelah batas aman " + MAX_STEPS + " langkah. Silakan lanjutkan dengan perintah baru.");
        } catch (Exception e) {
            callback.status("LUNA mengalami error: " + e.getMessage());
        }
    }

    private boolean shouldStop() {
        if (service == null || service.isStopped()) {
            callback.status("■ STOP — LUNA menghentikan agent.");
            return true;
        }
        return false;
    }

    private boolean pauseResponsive(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (shouldStop()) return false;
            try { Thread.sleep(Math.min(100, end - System.currentTimeMillis())); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return true;
    }

    private String signature(JSONObject x) {
        return x.optString("type") + "|" + x.optString("value") + "|" + x.optString("package") +
                "|" + x.optString("label") + "|" + x.optString("direction") + "|" +
                x.optString("x") + "|" + x.optString("y");
    }

    private boolean execute(JSONObject x) {
        String type = x.optString("type");
        DeviceExecutor device = new DeviceExecutor(activity);
        if ("OPEN_APP".equals(type)) return device.openApp(x.optString("package"), x.optString("label"));
        if ("CLICK_TEXT".equals(type)) return service.clickText(x.optString("value"));
        if ("CLICK_DESC".equals(type)) return service.clickDescription(x.optString("value"));
        if ("CLICK_POINT".equals(type)) return service.clickPoint((float)x.optDouble("x", 0), (float)x.optDouble("y", 0));
        if ("TYPE".equals(type)) return service.typeText(x.optString("value"));
        if ("SCROLL".equals(type)) return service.scroll(x.optString("direction"));
        if ("BACK".equals(type)) return service.globalBack();
        if ("HOME".equals(type)) return service.globalHome();
        if ("RECENTS".equals(type)) return service.globalRecents();
        if ("OPEN_URL".equals(type)) return device.openUrl(x.optString("value"));
        if ("WAIT".equals(type)) {
            device.delay(Math.min(5000, Math.max(50, x.optLong("delayMs", 500))));
            return !shouldStop();
        }
        return false;
    }

    private String capture() throws InterruptedException {
        AtomicReference<String> image = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);
        service.captureScreen(Runnable::run, b64 -> { image.set(b64); latch.countDown(); });
        latch.await(8, TimeUnit.SECONDS);
        return image.get();
    }
}
