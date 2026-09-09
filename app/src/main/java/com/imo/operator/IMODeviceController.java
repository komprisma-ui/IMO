package com.imo.operator;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

/** Official Android device controls. Never bypasses Android security or the lock credential. */
public final class IMODeviceController {
    private IMODeviceController() {}

    public static boolean lockScreen(Context context) {
        IMOAccessibilityService s = IMOAccessibilityService.instance;
        return Build.VERSION.SDK_INT >= 28 && s != null && s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    }

    public static boolean wakeScreen(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return false;
            if (Build.VERSION.SDK_INT >= 21) {
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "IMO:WakeScreen");
                wl.acquire(1500L);
                wl.release();
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean setTorch(Context context, boolean enabled) {
        if (Build.VERSION.SDK_INT < 23) return false;
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return false;
            String chosen = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash)) {
                    chosen = id;
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) break;
                }
            }
            if (chosen == null) return false;
            cm.setTorchMode(chosen, enabled);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static boolean openWifiSettings(Context context) {
        try { context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (Exception e) { return false; }
    }

    public static boolean openBluetoothSettings(Context context) {
        try { context.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (Exception e) { return false; }
    }

    public static boolean setVolume(Context context, int stream, int direction) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            am.adjustStreamVolume(stream, direction, 0);
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean call(Context context, String number) {
        if (number == null || number.trim().isEmpty()) return false;
        try {
            Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number.trim()))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean openSystemSettings(Context context) {
        try { context.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (Exception e) { return false; }
    }
}
