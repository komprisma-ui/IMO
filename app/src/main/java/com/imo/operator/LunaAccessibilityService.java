package com.imo.operator;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LunaAccessibilityService extends AccessibilityService {
    private static volatile LunaAccessibilityService instance;
    private volatile boolean stopped;

    @Override public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_CLICKED |
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
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
        StringBuilder out = new StringBuilder();
        appendNode(root, out, 0);
        return out.length() > 9000 ? out.substring(0, 9000) : out.toString();
    }

    private void appendNode(AccessibilityNodeInfo n, StringBuilder out, int depth) {
        if (n == null || depth > 20 || out.length() > 9000) return;
        CharSequence text = n.getText();
        CharSequence desc = n.getContentDescription();
        if ((text != null && text.length() > 0) || (desc != null && desc.length() > 0) || n.isClickable() || n.isEditable()) {
            Rect r = new Rect(); n.getBoundsInScreen(r);
            out.append("[class=").append(n.getClassName()).append("]")
               .append("[text=").append(text == null ? "" : text).append("]")
               .append("[desc=").append(desc == null ? "" : desc).append("]")
               .append("[click=").append(n.isClickable()).append("]")
               .append("[edit=").append(n.isEditable()).append("]")
               .append("[bounds=").append(r.left).append(',').append(r.top).append(',').append(r.right).append(',').append(r.bottom).append("]\n");
        }
        for (int i = 0; i < n.getChildCount(); i++) appendNode(n.getChild(i), out, depth + 1);
    }

    public boolean clickText(String value) { return clickMatch(value, false); }
    public boolean clickDescription(String value) { return clickMatch(value, true); }
    private boolean clickMatch(String value, boolean descOnly) {
        if (stopped || value == null) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collectMatches(root, value.toLowerCase(Locale.ROOT), descOnly, nodes);
        for (AccessibilityNodeInfo n : nodes) {
            AccessibilityNodeInfo p = n;
            while (p != null) {
                if (p.isClickable()) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                p = p.getParent();
            }
        }
        return false;
    }

    private void collectMatches(AccessibilityNodeInfo n, String needle, boolean descOnly, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        CharSequence a = n.getText(), b = n.getContentDescription();
        String s = descOnly ? (b == null ? "" : b.toString()) : ((a == null ? "" : a.toString()) + " " + (b == null ? "" : b.toString()));
        if (s.toLowerCase(Locale.ROOT).contains(needle)) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collectMatches(n.getChild(i), needle, descOnly, out);
    }

    public boolean typeText(String text) {
        if (stopped) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo target = findEditable(root);
        if (target == null) return false;
        Bundle b = new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && n.isVisibleToUser()) return n;
        for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo x = findEditable(n.getChild(i)); if (x != null) return x; }
        return null;
    }

    public boolean scroll(String direction) {
        if (stopped) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow(); if (root == null) return false;
        return scrollNode(root, direction == null ? "DOWN" : direction.toUpperCase(Locale.ROOT));
    }
    private boolean scrollNode(AccessibilityNodeInfo n, String dir) {
        if (n == null) return false;
        if (n.isScrollable()) {
            int action = "UP".equals(dir) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            if (n.performAction(action)) return true;
        }
        for (int i = 0; i < n.getChildCount(); i++) if (scrollNode(n.getChild(i), dir)) return true;
        return false;
    }

    public boolean globalBack() { return !stopped && performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean globalHome() { return !stopped && performGlobalAction(GLOBAL_ACTION_HOME); }
    public boolean globalRecents() { return !stopped && performGlobalAction(GLOBAL_ACTION_RECENTS); }
}
