package com.imo.operator;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityNodeInfo;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
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
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "Accessibility belum bisa membaca layar.";
        StringBuilder b = new StringBuilder();
        walk(root, b);
        String s = b.toString().trim();
        if (s.isEmpty()) return "Tidak ada teks yang terbaca.";
        return "Saya membaca layar: " + (s.length() > 3500 ? s.substring(0, 3500) : s);
    }

    private void walk(AccessibilityNodeInfo node, StringBuilder b) {
        if (node == null) return;
        CharSequence text = node.getText(), desc = node.getContentDescription();
        if ((text != null && text.length() > 0) || (desc != null && desc.length() > 0)) {
            if (b.length() > 0) b.append(". ");
            b.append(text != null && text.length() > 0 ? text : desc);
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i), b);
    }

    public boolean clickText(String query) {
        AccessibilityNodeInfo node = find(getRootInActiveWindow(), query);
        if (node == null) node = findContains(getRootInActiveWindow(), query);
        if (node == null) return false;
        for (AccessibilityNodeInfo p = node; p != null; p = p.getParent()) {
            if (p.isClickable() && p.isEnabled()) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String query) {
        if (node == null) return null;
        CharSequence text = node.getText(), desc = node.getContentDescription();
        if ((text != null && text.toString().equalsIgnoreCase(query)) ||
            (desc != null && desc.toString().equalsIgnoreCase(query))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), query);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findContains(AccessibilityNodeInfo node, String query) {
        if (node == null) return null;
        String q = query.toLowerCase(Locale.ROOT);
        CharSequence text = node.getText(), desc = node.getContentDescription();
        if ((text != null && text.toString().toLowerCase(Locale.ROOT).contains(q)) ||
            (desc != null && desc.toString().toLowerCase(Locale.ROOT).contains(q))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findContains(node.getChild(i), query);
            if (found != null) return found;
        }
        return null;
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
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo target = findScrollable(root);
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
