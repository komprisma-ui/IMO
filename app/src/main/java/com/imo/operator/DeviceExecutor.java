package com.imo.operator;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import java.util.Locale;

public final class DeviceExecutor {
    private final Context context;
    public DeviceExecutor(Context context) { this.context = context.getApplicationContext(); }

    public boolean openApp(String packageName, String label) {
        try {
            String pkg = packageName;
            if (pkg == null || pkg.trim().isEmpty()) pkg = resolvePackage(label);
            if (pkg == null) return false;
            Intent i = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i); return true;
        } catch (Exception e) { return false; }
    }

    private String resolvePackage(String label) {
        if (label == null) return null;
        String x = label.toLowerCase(Locale.ROOT);
        if (x.contains("whatsapp")) return "com.whatsapp";
        if (x.contains("youtube")) return "com.google.android.youtube";
        if (x.contains("chrome")) return "com.android.chrome";
        if (x.contains("telegram")) return "org.telegram.messenger";
        if (x.contains("facebook")) return "com.facebook.katana";
        if (x.contains("instagram")) return "com.instagram.android";
        if (x.contains("maps") || x.contains("google maps")) return "com.google.android.apps.maps";
        return null;
    }

    public boolean openUrl(String url) {
        try { Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); return true; }
        catch (Exception e) { return false; }
    }

    public void delay(long ms) { SystemClock.sleep(Math.min(Math.max(ms, 0), 5000)); }
}
