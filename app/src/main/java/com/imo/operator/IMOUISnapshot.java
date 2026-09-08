package com.imo.operator;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/** Structured, bounded representation of the active Android UI for semantic planning. */
public final class IMOUISnapshot {
    public static final class Node {
        public final String text, description, className, resourceId;
        public final boolean clickable, editable, scrollable, enabled;
        public final Rect bounds;
        Node(AccessibilityNodeInfo n) {
            text=value(n.getText()); description=value(n.getContentDescription()); className=value(n.getClassName()); resourceId=value(n.getViewIdResourceName());
            clickable=n.isClickable(); editable=n.isEditable(); scrollable=n.isScrollable(); enabled=n.isEnabled();
            Rect r=new Rect(); n.getBoundsInScreen(r); bounds=r;
        }
        public String label(){return !text.isEmpty()?text:description;}
        private static String value(CharSequence s){return s==null?"":s.toString().trim();}
    }
    private final List<Node> nodes; private final String packageName;
    private IMOUISnapshot(String packageName,List<Node> nodes){this.packageName=packageName==null?"":packageName;this.nodes=nodes;}
    public String getPackageName(){return packageName;} public List<Node> getNodes(){return nodes;}
    public static IMOUISnapshot capture(AccessibilityService service){
        List<Node> result=new ArrayList<>();if(service==null)return new IMOUISnapshot("",result);
        AccessibilityNodeInfo root=service.getRootInActiveWindow();if(root==null)return new IMOUISnapshot("",result);
        walk(root,result,0,500);String pkg=root.getPackageName()==null?"":root.getPackageName().toString();return new IMOUISnapshot(pkg,result);
    }
    private static void walk(AccessibilityNodeInfo n,List<Node> out,int depth,int max){
        if(n==null||out.size()>=max||depth>80)return;if(hasUsefulData(n))out.add(new Node(n));
        for(int i=0;i<n.getChildCount()&&out.size()<max;i++)walk(n.getChild(i),out,depth+1,max);
    }
    private static boolean hasUsefulData(AccessibilityNodeInfo n){return n.getText()!=null||n.getContentDescription()!=null||n.isClickable()||n.isEditable()||n.isScrollable();}
    public Node bestMatch(String query){if(query==null||query.trim().isEmpty())return null;Node best=null;int bestScore=0;for(Node n:nodes){int score=IMOSemanticMatcher.score(query,n);if(score>bestScore){bestScore=score;best=n;}}return best;}
    public String compact(int maxChars){StringBuilder b=new StringBuilder();b.append("package=").append(packageName).append("\n");for(Node n:nodes){String label=n.label();if(label.isEmpty())continue;b.append("- ").append(label);if(n.clickable)b.append(" [click]");if(n.editable)b.append(" [edit]");if(n.scrollable)b.append(" [scroll]");b.append("\n");if(b.length()>=maxChars)break;}return b.length()>maxChars?b.substring(0,maxChars):b.toString();}
}
