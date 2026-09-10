package com.imo.operator;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class LunaAccessibilityService extends AccessibilityService {
    private static volatile LunaAccessibilityService instance;
    private volatile boolean stopped;

    @Override public void onServiceConnected() {
        instance = this; stopped = false;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_CLICKED |
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }
    public static LunaAccessibilityService get() { return instance; }
    public void stopNow() { stopped = true; }
    public void resumeNow() { stopped = false; }
    public boolean isStopped() { return stopped; }

    public String snapshot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "No active accessibility window.";
        StringBuilder out = new StringBuilder(); appendNode(root, out, 0);
        return out.length() > 16000 ? out.substring(0, 16000) : out.toString();
    }

    private void appendNode(AccessibilityNodeInfo n, StringBuilder out, int depth) {
        if (n == null || depth > 28 || out.length() > 16000) return;
        CharSequence text = n.getText(), desc = n.getContentDescription(), id = n.getViewIdResourceName();
        if ((text != null && text.length() > 0) || (desc != null && desc.length() > 0) || id != null || n.isClickable() || n.isEditable() || n.isScrollable()) {
            Rect r = new Rect(); n.getBoundsInScreen(r);
            out.append("[class=").append(n.getClassName()).append("]")
               .append("[id=").append(id == null ? "" : id).append("]")
               .append("[text=").append(text == null ? "" : text).append("]")
               .append("[desc=").append(desc == null ? "" : desc).append("]")
               .append("[click=").append(n.isClickable()).append("]")
               .append("[longClick=").append(n.isLongClickable()).append("]")
               .append("[edit=").append(n.isEditable()).append("]")
               .append("[scroll=").append(n.isScrollable()).append("]")
               .append("[visible=").append(n.isVisibleToUser()).append("]")
               .append("[bounds=").append(r.left).append(',').append(r.top).append(',').append(r.right).append(',').append(r.bottom).append("]\n");
        }
        for (int i = 0; i < n.getChildCount(); i++) appendNode(n.getChild(i), out, depth + 1);
    }

    public void captureScreen(Executor executor, ScreenCallback callback) {
        if (Build.VERSION.SDK_INT < 30 || stopped) { callback.onResult(null); return; }
        try { takeScreenshot(Display.DEFAULT_DISPLAY, executor, new TakeScreenshotCallbackCompat(callback)); }
        catch (Throwable t) { callback.onResult(null); }
    }
    public interface ScreenCallback { void onResult(String base64Jpeg); }
    private static final class TakeScreenshotCallbackCompat implements AccessibilityService.TakeScreenshotCallback {
        private final ScreenCallback callback;
        TakeScreenshotCallbackCompat(ScreenCallback callback) { this.callback = callback; }
        @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
            try {
                Bitmap hardware = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                if (hardware == null) { callback.onResult(null); return; }
                Bitmap copy = hardware.copy(Bitmap.Config.ARGB_8888, false); hardware.recycle();
                if (copy == null) { callback.onResult(null); return; }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                copy.compress(Bitmap.CompressFormat.JPEG, 70, out); copy.recycle();
                callback.onResult(android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP));
            } catch (Throwable t) { callback.onResult(null); }
        }
        @Override public void onFailure(int errorCode) { callback.onResult(null); }
    }

    public boolean clickText(String value) { return clickMatch(value, false, false); }
    public boolean clickDescription(String value) { return clickMatch(value, true, false); }
    public boolean clickId(String value) { return clickMatch(value, false, true); }
    public boolean longClickText(String value) { return clickMatch(value, false, false, true); }

    private boolean clickMatch(String value, boolean descOnly, boolean idOnly) { return clickMatch(value, descOnly, idOnly, false); }
    private boolean clickMatch(String value, boolean descOnly, boolean idOnly, boolean longClick) {
        if (stopped || value == null || value.trim().isEmpty()) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow(); if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = new ArrayList<>(); collectMatches(root, value.toLowerCase(Locale.ROOT), descOnly, idOnly, nodes);
        for (AccessibilityNodeInfo n : nodes) {
            AccessibilityNodeInfo p = n;
            while (p != null) {
                if (p.isVisibleToUser() && ((longClick && p.isLongClickable()) || (!longClick && p.isClickable()))) {
                    return p.performAction(longClick ? AccessibilityNodeInfo.ACTION_LONG_CLICK : AccessibilityNodeInfo.ACTION_CLICK);
                }
                p = p.getParent();
            }
        }
        return false;
    }

    private void collectMatches(AccessibilityNodeInfo n, String needle, boolean descOnly, boolean idOnly, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        CharSequence a = n.getText(), b = n.getContentDescription(), c = n.getViewIdResourceName();
        String s;
        if (idOnly) s = c == null ? "" : c.toString();
        else if (descOnly) s = b == null ? "" : b.toString();
        else s = (a == null ? "" : a.toString()) + " " + (b == null ? "" : b.toString());
        if (s.toLowerCase(Locale.ROOT).contains(needle)) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collectMatches(n.getChild(i), needle, descOnly, idOnly, out);
    }

    public boolean clickPoint(float x, float y) {
        if (stopped || Build.VERSION.SDK_INT < 24) return false;
        return tap(x, y, 80);
    }
    public boolean longClickPoint(float x, float y) {
        if (stopped || Build.VERSION.SDK_INT < 24) return false;
        return tap(x, y, 650);
    }
    private boolean tap(float x, float y, long duration) {
        Path path = new Path(); path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build();
        return dispatchGesture(gesture, null, null);
    }

    public boolean typeText(String text) {
        if (stopped) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow(); if (root == null) return false;
        AccessibilityNodeInfo target = findFocusedEditable(root); if (target == null) target = findEditable(root);
        if (target == null) return false;
        Bundle b = new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text == null ? "" : text);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }
    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && n.isFocused() && n.isVisibleToUser()) return n;
        for (int i=0;i<n.getChildCount();i++){ AccessibilityNodeInfo x=findFocusedEditable(n.getChild(i)); if(x!=null)return x; }
        return null;
    }
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && n.isVisibleToUser()) return n;
        for (int i=0;i<n.getChildCount();i++){ AccessibilityNodeInfo x=findEditable(n.getChild(i)); if(x!=null)return x; }
        return null;
    }

    public boolean scroll(String direction) {
        if (stopped) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow(); if (root == null) return false;
        return scrollNode(root, direction == null ? "DOWN" : direction.toUpperCase(Locale.ROOT));
    }
    private boolean scrollNode(AccessibilityNodeInfo n, String dir) {
        if (n == null) return false;
        if (n.isScrollable() && n.isVisibleToUser()) {
            int action = "UP".equals(dir) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            if (n.performAction(action)) return true;
        }
        for(int i=0;i<n.getChildCount();i++) if(scrollNode(n.getChild(i),dir)) return true;
        return false;
    }

    public boolean swipe(String direction, int distance, int duration) {
        if (stopped || Build.VERSION.SDK_INT < 24) return false;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = dm.widthPixels / 2f, cy = dm.heightPixels / 2f;
        float d = Math.max(200, Math.min(distance, Math.max(dm.widthPixels, dm.heightPixels)));
        float sx=cx, sy=cy, ex=cx, ey=cy;
        String dir = direction == null ? "UP" : direction.toUpperCase(Locale.ROOT);
        if ("UP".equals(dir)) { sy=cy+d/2; ey=cy-d/2; }
        else if ("DOWN".equals(dir)) { sy=cy-d/2; ey=cy+d/2; }
        else if ("LEFT".equals(dir)) { sx=cx+d/2; ex=cx-d/2; }
        else { sx=cx-d/2; ex=cx+d/2; }
        Path p=new Path(); p.moveTo(sx,sy); p.lineTo(ex,ey);
        GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,Math.max(150,Math.min(duration,1200)))).build();
        return dispatchGesture(g,null,null);
    }

    public boolean globalBack() { return !stopped && performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean globalHome() { return !stopped && performGlobalAction(GLOBAL_ACTION_HOME); }
    public boolean globalRecents() { return !stopped && performGlobalAction(GLOBAL_ACTION_RECENTS); }
}
