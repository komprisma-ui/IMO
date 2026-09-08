package com.imo.operator;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityNodeInfo;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Structured, bounded representation of the active Android UI for semantic planning. */
public final class IMOUISnapshot {
    public static final class Node {
        public final String text;
        public final String description;
        public final String className;
        public final String resourceId;
        public final boolean clickable;
        public final boolean editable;
        public final boolean scrollable;
        public final boolean enabled;
        public final Rect bounds;

        Node(AccessibilityNodeInfo n) {
            text = value(n.getText());
            description = value(n.getContentDescription());
            className = value(n.getClassName());
            resourceId = value(n.getViewIdResourceName());
            clickable = n.isClickable();
            editable = n.isEditable();
            scrollable = n.isScrollable();
            enabled = n.isEnabled();
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            bounds = r;
        }

        String label() {
            return !text.isEmpty() ? text : description;
        }

        private static String value(CharSequence s) {
            return s == null ? "" : s.toString().trim();
        }
    }

    private final List<Node> nodes;
    private final String packageName;

    private IMOUISnapshot(String packageName, List<Node> nodes) {
        this.packageName = packageName == null ? "" : packageName;
        this.nodes = nodes;
    }

    public String getPackageName() { return packageName; }
    public List<Node> getNodes() { return nodes; }

    public static IMOUISnapshot capture(AccessibilityService service) {
        List<Node> result = new ArrayList<>();
        if (service == null) return new IMOUISnapshot("", result);
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return new IMOUISnapshot("", result);
        walk(root, result, 0, 500);
        String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
        return new IMOUISnapshot(pkg, result);
    }

    private static void walk(AccessibilityNodeInfo n, List<Node> out, int depth, int max) {
        if (n == null || out.size() >= max || depth > 80) return;
        if (hasUsefulData(n)) out.add(new Node(n));
        for (int i = 0; i < n.getChildCount() && out.size() < max; i++) {
            walk(n.getChild(i), out, depth + 1, max);
        }
    }

    private static boolean hasUsefulData(AccessibilityNodeInfo n) {
        return n.getText() != null || n.getContentDescription() != null ||
                n.isClickable() || n.isEditable() || n.isScrollable();
    }

    /** Finds the most plausible interactive node using label similarity and role hints. */
    public Node bestMatch(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = normalize(query);
        Node best = null;
        int bestScore = 0;
        for (Node n : nodes) {
            String label = normalize(n.label());
            if (label.isEmpty()) continue;
            int score = score(q, label, n);
            if (score > bestScore) { bestScore = score; best = n; }
        }
        return best;
    }

    private static int score(String q, String label, Node n) {
        int score = 0;
        if (label.equals(q)) score += 100;
        if (label.contains(q)) score += 60;
        if (q.contains(label) && label.length() > 2) score += 35;
        for (String token : q.split(" ")) if (token.length() > 1 && label.contains(token)) score += 12;
        if (n.clickable) score += 15;
        if (n.enabled) score += 5;
        if (n.editable && (q.contains("ketik") || q.contains("cari"))) score += 10;
        return score;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u00c0-\\u024f\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    /** Compact observation useful for debugging or an external Brain, with a strict size bound. */
    public String compact(int maxChars) {
        StringBuilder b = new StringBuilder();
        b.append("package=").append(packageName).append("\n");
        for (Node n : nodes) {
            String label = n.label();
            if (label.isEmpty()) continue;
            b.append("- ").append(label);
            if (n.clickable) b.append(" [click]");
            if (n.editable) b.append(" [edit]");
            if (n.scrollable) b.append(" [scroll]");
            b.append("\n");
            if (b.length() >= maxChars) break;
        }
        return b.length() > maxChars ? b.substring(0, maxChars) : b.toString();
    }
}
