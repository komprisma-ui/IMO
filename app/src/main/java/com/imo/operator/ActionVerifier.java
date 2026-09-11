package com.imo.operator;

import java.util.Locale;

/** Lightweight verification and loop detection between operator steps. */
public final class ActionVerifier {
    public static String signature(org.json.JSONObject a){
        if(a==null)return "";
        return a.optString("type","").toUpperCase(Locale.ROOT)+"|"+a.optString("value","")+"|"+a.optString("package","")+"|"+a.optString("label","")+"|"+a.optString("direction","")+"|"+a.optString("x","")+"|"+a.optString("y","");
    }
    public static boolean sameScreen(String a,String b){return a!=null&&a.equals(b);}
    public static boolean looksSuccessful(String before,String after){
        if(before==null||after==null)return false;
        if(!before.equals(after))return true;
        return false;
    }
    private ActionVerifier(){}
}
