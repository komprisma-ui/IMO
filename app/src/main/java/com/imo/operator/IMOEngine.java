package com.imo.operator;

import android.os.Handler;
import android.os.Looper;
import java.util.List;

/** IMO execution loop with explicit confirmation and bounded recovery. */
public final class IMOEngine {
    public interface Callback {
        void onProgress(String message);
        void onFinished(String message, boolean success);
        void onConfirmationRequired(String message);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final IMOAccessibilityService service;
    private final IMOConfirmation confirmation;
    private static final int MAX_RECOVERY = 2;

    public IMOEngine(IMOAccessibilityService service) { this(service, new IMOConfirmation()); }
    public IMOEngine(IMOAccessibilityService service, IMOConfirmation confirmation) {
        this.service = service;
        this.confirmation = confirmation;
    }

    public boolean hasPendingConfirmation() { return confirmation.pending() != null; }

    public void execute(String input, Callback callback) {
        IMOAction confirmed = confirmation.consumeIfConfirmed(input);
        if (confirmed != null) {
            if (confirmed.type == IMOAction.Type.NONE) {
                callback.onFinished("Baik, saya batalkan tindakan tersebut.", false);
                return;
            }
            run(java.util.Collections.singletonList(confirmed), 0, 0, callback, true);
            return;
        }
        List<IMOAction> actions = IMOPlanner.plan(input);
        if (actions.isEmpty()) {
            callback.onFinished("Saya belum memahami tujuan itu. Jelaskan apa yang ingin dicapai.", false);
            return;
        }
        run(actions, 0, 0, callback, false);
    }

    private void run(List<IMOAction> actions, int index, int recovery, Callback callback, boolean confirmed) {
        if (index >= actions.size()) {
            callback.onFinished("Selesai. Semua langkah yang direncanakan telah dijalankan.", true);
            return;
        }
        IMOAction action = actions.get(index);
        callback.onProgress("Mengamati dan menjalankan: " + action);
        if (action.type == IMOAction.Type.WAIT) {
            main.postDelayed(() -> run(actions, index + 1, 0, callback, confirmed), Math.max(100, action.waitMs));
            return;
        }
        if (service == null) {
            callback.onFinished("Kendali Accessibility IMO belum aktif. Aktifkan layanan IMO terlebih dahulu.", false);
            return;
        }
        if (action.sensitive && !confirmed) {
            confirmation.request(action);
            callback.onConfirmationRequired("Tindakan ini membutuhkan konfirmasi: " + action.value + ". Ucapkan YA untuk melanjutkan atau BATAL untuk membatalkan.");
            return;
        }

        boolean ok = false;
        String result = null;
        switch (action.type) {
            case OPEN_APP: ok = service.openApp(action.value); break;
            case CLICK: ok = service.clickText(action.value); break;
            case TYPE: ok = service.typeText(action.value); break;
            case READ: result = service.readScreen(); ok = result != null && !result.contains("Tidak ada elemen UI"); break;
            case BACK: ok = service.goBack(); break;
            case HOME: ok = service.goHome(); break;
            case SCROLL_DOWN: ok = service.scrollDown(); break;
            case SCROLL_UP: ok = service.scrollUp(); break;
            case LONG_CLICK: ok = service.longClickText(action.value); break;
            default: break;
        }
        if (!ok) {
            if (recovery < MAX_RECOVERY && action.type == IMOAction.Type.CLICK) {
                callback.onProgress("Aksi belum cocok. Saya mencoba pencarian elemen yang lebih toleran…");
                main.postDelayed(() -> run(actions, index, recovery + 1, callback, confirmed), 300);
                return;
            }
            callback.onFinished(result != null ? result : "Langkah gagal dan saya berhenti agar tidak mengambil tindakan yang keliru.", false);
            return;
        }
        if (result != null) callback.onProgress(result);
        main.postDelayed(() -> verifyAndContinue(actions, index, callback, confirmed), action.type == IMOAction.Type.OPEN_APP ? 1000 : 400);
    }

    private void verifyAndContinue(List<IMOAction> actions, int index, Callback callback, boolean confirmed) {
        if (service != null) {
            String observation = service.readScreen();
            if (observation != null && !observation.isEmpty()) callback.onProgress("Verifikasi layar selesai.");
        }
        run(actions, index + 1, 0, callback, confirmed);
    }
}
