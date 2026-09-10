package com.imo.operator;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Observe -> plan -> act -> observe loop for LUNA. */
public final class LunaAgent {
    public interface Callback {
        void status(String text);
        boolean confirm(JSONObject plan);
    }

    private final MainActivity activity;
    private final LunaAccessibilityService service;
    private final SecureKeyStore keyStore;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final int MAX_STEPS = 8;

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
            String goal = command;
            for (int step = 1; step <= MAX_STEPS; step++) {
                if (service == null || service.isStopped()) { callback.status("STOP — LUNA menghentikan agent."); return; }
                callback.status("Mengamati layar — siklus " + step + "/" + MAX_STEPS + "…");
                String image = capture();
                String snapshot = service.snapshot();
                String prompt = goal + "\n\nMODE AGENT: ini adalah langkah " + step + " dari maksimal " + MAX_STEPS + ". " +
                        "Amati keadaan TERKINI. Tentukan HANYA langkah berikutnya yang paling tepat untuk mencapai tujuan. " +
                        "Jika tujuan sudah tercapai, kembalikan actions kosong. Jangan mengulang tindakan yang sudah berhasil. " +
                        "Jika elemen teks tidak tersedia tetapi terlihat pada screenshot, gunakan CLICK_POINT dengan koordinat layar. " +
                        "Untuk tindakan sensitif wajib confirm=true. Hanya satu action dalam array actions.";
                JSONObject plan = new JSONObject(ai.plan(prompt, snapshot, image));
                JSONArray actions = plan.optJSONArray("actions");
                if (actions == null || actions.length() == 0) {
                    callback.status(plan.optString("speak", "Tujuan selesai atau tidak ada tindakan yang diperlukan."));
                    return;
                }
                if (plan.optBoolean("confirm", false)) {
                    boolean approved = callback.confirm(plan);
                    if (!approved) { callback.status("Tindakan dibatalkan pengguna."); return; }
                }
                JSONObject action = actions.getJSONObject(0);
                boolean ok = execute(action);
                callback.status("Agent langkah " + step + ": " + action.optString("type") + " → " + (ok ? "berhasil" : "gagal"));
                if (!ok) {
                    // Give the model one fresh observation after a failed action instead of blindly continuing.
                    callback.status("Langkah gagal. LUNA mengamati ulang untuk pemulihan…");
                    String recoveryImage = capture();
                    String recoverySnapshot = service.snapshot();
                    JSONObject recovery = new JSONObject(ai.plan(goal +
                            "\n\nPEMULIHAN: langkah sebelumnya gagal (" + action.optString("type") + "). " +
                            "Cari cara alternatif berdasarkan layar TERKINI. Hanya satu action. Jangan mengulang action yang sama jika tidak diperlukan.",
                            recoverySnapshot, recoveryImage));
                    JSONArray ra = recovery.optJSONArray("actions");
                    if (ra == null || ra.length() == 0) { callback.status("LUNA tidak menemukan langkah pemulihan yang aman."); return; }
                    if (recovery.optBoolean("confirm", false) && !callback.confirm(recovery)) return;
                    JSONObject retry = ra.getJSONObject(0);
                    boolean recovered = execute(retry);
                    callback.status("Pemulihan: " + retry.optString("type") + " → " + (recovered ? "berhasil" : "gagal"));
                    if (!recovered) { callback.status("LUNA berhenti: pemulihan gagal."); return; }
                }
                Thread.sleep(350);
            }
            callback.status("LUNA berhenti setelah batas agent " + MAX_STEPS + " langkah.");
        } catch (Exception e) {
            callback.status("Agent error: " + e.getMessage());
        }
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
        if ("WAIT".equals(type)) { device.delay(Math.min(5000, Math.max(50, x.optLong("delayMs", 500)))); return true; }
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
