package com.imo.operator;

import android.content.Context;
import android.content.SharedPreferences;

public final class BridgeStore {
    private final SecureKeyStore secure;
    private final SharedPreferences prefs;
    public BridgeStore(Context c){secure=new SecureKeyStore(c);prefs=c.getSharedPreferences("luna_bridge",Context.MODE_PRIVATE);}
    public void save(String url,String token)throws Exception{prefs.edit().putString("url",url==null?"":url.trim()).apply();secure.saveNamed("bridge_token",token==null?"":token.trim());}
    public String url(){return prefs.getString("url","");}
    public String token(){try{return secure.loadNamed("bridge_token");}catch(Exception e){return "";}}
    public void clear(){prefs.edit().remove("url").apply();try{secure.clearNamed("bridge_token");}catch(Exception ignored){}}
}
