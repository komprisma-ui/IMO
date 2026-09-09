package com.imo.operator;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Device perception/execution layer for IMO. Uses Android public Accessibility APIs. */
public class IMOAccessibilityService extends AccessibilityService {
    public static IMOAccessibilityService instance;
    @Override public void onServiceConnected(){instance=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    public boolean openApp(String packageOrLabel){String pkg=resolvePackage(packageOrLabel);if(pkg==null)return false;Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null)return false;intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);startActivity(intent);return true;}
    private String resolvePackage(String requested){if(requested==null||requested.trim().isEmpty())return null;String q=normalize(requested);if(q.contains(".")){try{if(getPackageManager().getLaunchIntentForPackage(q)!=null)return q;}catch(Exception ignored){}}for(android.content.pm.ApplicationInfo ai:getPackageManager().getInstalledApplications(0)){CharSequence label=getPackageManager().getApplicationLabel(ai);if(label!=null&&normalize(label.toString()).equals(q))return ai.packageName;}for(android.content.pm.ApplicationInfo ai:getPackageManager().getInstalledApplications(0)){CharSequence label=getPackageManager().getApplicationLabel(ai);if(label!=null&&normalize(label.toString()).contains(q))return ai.packageName;}return null;}
    public boolean goBack(){return performGlobalAction(GLOBAL_ACTION_BACK);}public boolean goHome(){return performGlobalAction(GLOBAL_ACTION_HOME);}public boolean openRecents(){return performGlobalAction(GLOBAL_ACTION_RECENTS);}public boolean openNotifications(){return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);}public boolean openQuickSettings(){return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);}public boolean powerDialog(){return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);}public boolean splitScreen(){return performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);}
    public String readScreen(){IMOUISnapshot snapshot=IMOUISnapshot.capture(this);String compact=snapshot.compact(5000);return compact.isEmpty()?"Tidak ada elemen UI yang terbaca.":"Saya membaca layar:\n"+compact;}public IMOUISnapshot snapshot(){return IMOUISnapshot.capture(this);}
    public boolean clickText(String query){AccessibilityNodeInfo root=getRootInActiveWindow();AccessibilityNodeInfo node=find(root,query);if(node==null)node=findContains(root,query);if(node==null){IMOUISnapshot.Node candidate=snapshot().bestMatch(query);if(candidate!=null&&!candidate.label().isEmpty()){node=find(root,candidate.label());if(node==null)node=findContains(root,candidate.label());}}return clickNode(node);}
    private boolean clickNode(AccessibilityNodeInfo node){if(node==null)return false;for(AccessibilityNodeInfo p=node;p!=null;p=p.getParent())if(p.isClickable()&&p.isEnabled())return p.performAction(AccessibilityNodeInfo.ACTION_CLICK);return false;}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String query){if(node==null)return null;String q=normalize(query);CharSequence text=node.getText(),desc=node.getContentDescription();if((text!=null&&normalize(text.toString()).equals(q))||(desc!=null&&normalize(desc.toString()).equals(q)))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=find(node.getChild(i),query);if(found!=null)return found;}return null;}
    private AccessibilityNodeInfo findContains(AccessibilityNodeInfo node,String query){if(node==null)return null;String q=normalize(query);CharSequence text=node.getText(),desc=node.getContentDescription();if((text!=null&&normalize(text.toString()).contains(q))||(desc!=null&&normalize(desc.toString()).contains(q)))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findContains(node.getChild(i),query);if(found!=null)return found;}return null;}
    private String normalize(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    public boolean typeText(String text){AccessibilityNodeInfo node=findEditable(getRootInActiveWindow());if(node==null)return false;Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);}
    public boolean scrollDown(){return scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);}public boolean scrollUp(){return scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}private boolean scroll(int action){AccessibilityNodeInfo target=findScrollable(getRootInActiveWindow());return target!=null&&target.performAction(action);}
    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node){if(node==null)return null;if(node.isScrollable()&&node.isEnabled())return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findScrollable(node.getChild(i));if(found!=null)return found;}return null;}
    public boolean longClickText(String query){AccessibilityNodeInfo node=find(getRootInActiveWindow(),query);if(node==null)node=findContains(getRootInActiveWindow(),query);if(node==null)return false;for(AccessibilityNodeInfo p=node;p!=null;p=p.getParent())if(p.isClickable()&&p.isEnabled())return p.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);return false;}
    public boolean swipe(String direction){if(Build.VERSION.SDK_INT<24)return false;Display d=getDisplay();int w=d==null?1080:d.getWidth(),h=d==null?1920:d.getHeight();float cx=w/2f,cy=h/2f,sx=cx,sy=cy,ex=cx,ey=cy;if("up".equals(direction)){sy=h*.78f;ey=h*.22f;}else if("down".equals(direction)){sy=h*.22f;ey=h*.78f;}else if("left".equals(direction)){sx=w*.82f;ex=w*.18f;}else if("right".equals(direction)){sx=w*.18f;ex=w*.82f;}else return false;Path path=new Path();path.moveTo(sx,sy);path.lineTo(ex,ey);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,450)).build();CountDownLatch latch=new CountDownLatch(1);AtomicBoolean ok=new AtomicBoolean(false);boolean dispatched=dispatchGesture(gesture,new GestureResult(latch,ok),null);if(!dispatched)return false;try{latch.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return ok.get();}
    private static final class GestureResult extends AccessibilityService.GestureResultCallback{private final CountDownLatch latch;private final AtomicBoolean ok;GestureResult(CountDownLatch l,AtomicBoolean o){latch=l;ok=o;}@Override public void onCompleted(GestureDescription g){ok.set(true);latch.countDown();}@Override public void onCancelled(GestureDescription g){latch.countDown();}}
    public boolean openUrl(String url){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);return true;}catch(Exception e){return false;}}
    public boolean openSettingsPage(String action){try{Intent i=new Intent(action);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);return true;}catch(Exception e){return false;}}
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node){if(node==null)return null;if(node.isEditable()&&node.isEnabled())return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findEditable(node.getChild(i));if(found!=null)return found;}return null;}
}
