package com.imo.operator;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityNodeInfo;
import android.content.Intent;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import java.util.Locale;

/** Device perception/execution layer for IMO. */
public class IMOAccessibilityService extends AccessibilityService {
    public static IMOAccessibilityService instance;

    @Override public void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }

    public boolean openApp(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
    }

    public boolean goBack() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean goHome() { return performGlobalAction(GLOBAL_ACTION_HOME); }

    public String readScreen() {
        IMOUISnapshot snapshot = IMOUISnapshot.capture(this);
        String compact = snapshot.compact(3500);
        if (compact.isEmpty()) return "Tidak ada elemen UI yang terbaca.";
        return "Saya membaca layar:\n" + compact;
    }

    /** Exposes a structured UI state for IMO's planner/Brain. */
    public IMOUISnapshot snapshot() { return IMOUISnapshot.capture(this); }

    public boolean clickText(String query) {
        AccessibilityNodeInfo node = find(getRootInActiveWindow(), query);
        if (node == null) node = findContains(getRootInActiveWindow(), query);
        if (node == null) {
            IMOUISnapshot.Node candidate = snapshot().bestMatch(query);
            if (candidate != null) node = find(getRootInActiveWindow(), candidate.label());
            if (node == null && candidate != null) node = findContains(getRootInActiveWindow(), candidate.label());
        }
        return clickNode(node);
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        for (AccessibilityNodeInfo p = node; p != null; p = p.getParent()) {
            if (p.isClickable() && p.isEnabled()) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String query) {
        if (node == null) return null;
        String q = normalize(query);
        CharSequence text = node.getText(), desc = node.getContentDescription();
        if ((text != null && normalize(text.toString()).equals(q)) ||
            (desc != null && normalize(desc.toString()).equals(q))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), query);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findContains(AccessibilityNodeInfo node, String query) {
        if (node == null) return null;
        String q = normalize(query);
        CharSequence text = node.getText(), desc = node.getContentDescription();
        if ((text != null && normalize(text.toString()).contains(q)) ||
            (desc != null && normalize(desc.toString()).contains(q))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findContains(node.getChild(i), query);
            if (found != null) return found;
        }
        return null;
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public boolean typeText(String text) {
        AccessibilityNodeInfo node = findEditable(getRootInActiveWindow());
        if (node == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    public boolean scrollDown() { return scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); }
    public boolean scrollUp() { return scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); }

    private boolean scroll(int action) {
        AccessibilityNodeInfo target = findScrollable(getRootInActiveWindow());
        return target != null && target.performAction(action);
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable() && node.isEnabled()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findScrollable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    public boolean longClickText(String query) {
        AccessibilityNodeInfo node = find(getRootInActiveWindow(), query);
        if (node == null) node = findContains(getRootInActiveWindow(), query);
        if (node == null) return false;
        for (AccessibilityNodeInfo p = node; p != null; p = p.getParent()) {
            if (p.isClickable() && p.isEnabled()) return p.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        }
        return false;
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isEnabled()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }
}
