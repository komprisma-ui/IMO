package com.imo.operator;

import android.os.Handler;
import android.os.Looper;
import java.util.List;

/** Observe -> Think -> Act -> Verify engine. Verification is intentionally conservative. */
public final class IMOEngine {
    public interface Callback {
        void onProgress(String message);
        void onFinished(String message, boolean success);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final IMOAccessibilityService service;

    public IMOEngine(IMOAccessibilityService service) { this.service = service; }

    public void execute(String input, Callback callback) {
        List<IMOAction> actions = IMOPlanner.plan(input);
        if (actions.isEmpty()) {
            callback.onFinished("Saya belum memahami tindakan yang diminta. Coba jelaskan tujuan Anda dengan lebih sederhana.", false);
            return;
        }
        run(actions, 0, callback);
    }

    private void run(List<IMOAction> actions, int index, Callback callback) {
        if (index >= actions.size()) {
            callback.onFinished("Selesai. Saya sudah memeriksa hasil aksi terakhir.", true);
            return;
        }
        IMOAction action = actions.get(index);
        callback.onProgress("Saya menjalankan: " + action);

        if (action.type == IMOAction.Type.WAIT) {
            main.postDelayed(() -> run(actions, index + 1, callback), action.waitMs);
            return;
        }
        if (service == null) {
            callback.onFinished("Kendali Accessibility IMO belum aktif.", false);
            return;
        }

        boolean ok = false;
        String result = null;
        switch (action.type) {
            case OPEN_APP:
                service.openApp(action.value);
                ok = true;
                main.postDelayed(() -> run(actions, index + 1, callback), 900);
                return;
            case CLICK:
                ok = service.clickText(action.value);
                break;
            case TYPE:
                ok = service.typeText(action.value);
                break;
            case READ:
                result = service.readScreen();
                ok = result != null && !result.contains("belum bisa membaca");
                break;
            case BACK:
                ok = service.goBack();
                break;
            case HOME:
                ok = service.goHome();
                break;
            default:
                break;
        }
        if (!ok) {
            callback.onFinished(result != null ? result : "Aksi belum berhasil. Saya berhenti agar tidak melakukan tindakan yang salah.", false);
            return;
        }
        if (result != null) callback.onProgress(result);
        main.postDelayed(() -> run(actions, index + 1, callback), 350);
    }
}
