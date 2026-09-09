package com.imo.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Semantic planner: turns natural Indonesian requests into safe deterministic device/UI plans. */
public final class IMOPlanner {
    private IMOPlanner() {}
    public static List<IMOAction> plan(String input) {
        List<IMOAction> a=new ArrayList<>(); if(input==null)return a; String q=input.trim(), n=normalize(q); if(n.isEmpty())return a;
        if(has(n,"kunci layar","kunci hp","kunci ponsel","lock screen","lock hp")){a.add(IMOAction.of(IMOAction.Type.LOCK_SCREEN,null));return a;}
        if(has(n,"bangunkan layar","nyalakan layar","hidupkan layar","wake screen")){a.add(IMOAction.of(IMOAction.Type.WAKE_SCREEN,null));return a;}
        if(has(n,"nyalakan senter","hidupkan senter","senter nyala","torch on")){a.add(IMOAction.of(IMOAction.Type.TORCH_ON,null));return a;}
        if(has(n,"matikan senter","senter mati","torch off")){a.add(IMOAction.of(IMOAction.Type.TORCH_OFF,null));return a;}
        if(has(n,"buka wifi","buka wi fi","pengaturan wifi","pengaturan wi fi")){a.add(IMOAction.of(IMOAction.Type.OPEN_WIFI_SETTINGS,null));return a;}
        if(has(n,"buka bluetooth","pengaturan bluetooth")){a.add(IMOAction.of(IMOAction.Type.OPEN_BLUETOOTH_SETTINGS,null));return a;}
        if(has(n,"buka pengaturan","buka settings","pengaturan sistem")){a.add(IMOAction.of(IMOAction.Type.OPEN_SYSTEM_SETTINGS,null));return a;}
        if(has(n,"besarkan volume","naikkan volume","volume naik","volume up")){a.add(IMOAction.of(IMOAction.Type.VOLUME_UP,null));return a;}
        if(has(n,"kecilkan volume","turunkan volume","volume turun","volume down")){a.add(IMOAction.of(IMOAction.Type.VOLUME_DOWN,null));return a;}
        if(has(n,"matikan suara","mute","bisukan")){a.add(IMOAction.of(IMOAction.Type.MUTE,null));return a;}
        if(has(n,"kembali","back","mundur")){a.add(IMOAction.of(IMOAction.Type.BACK,null));return a;}
        if(has(n,"home","layar utama","halaman utama","beranda")){a.add(IMOAction.of(IMOAction.Type.HOME,null));return a;}
        if(has(n,"baca layar","lihat layar","apa yang ada di layar","bacakan layar")){a.add(IMOAction.of(IMOAction.Type.READ,null));return a;}
        if(has(n,"scroll bawah","scroll ke bawah","gulir bawah","gulir ke bawah","geser ke bawah")){a.add(IMOAction.of(IMOAction.Type.SCROLL_DOWN,null));return a;}
        if(has(n,"scroll atas","scroll ke atas","gulir atas","gulir ke atas","geser ke atas")){a.add(IMOAction.of(IMOAction.Type.SCROLL_UP,null));return a;}
        String app=appPackage(n); if(app!=null&&isOpenIntent(n)){a.add(IMOAction.of(IMOAction.Type.OPEN_APP,app));return a;}
        String click=extractAfter(n,q,"klik","tekan","pilih"); if(!click.isEmpty()){a.add(actionForClick(click));return a;}
        String type=extractAfter(n,q,"ketik","tulis","isi","masukkan"); if(!type.isEmpty()){a.add(IMOAction.of(IMOAction.Type.TYPE,type));return a;}
        if(has(n,"telepon ","panggil ","hubungi ")){String number=extractAfter(n,q,"telepon","panggil","hubungi"); if(!number.isEmpty())a.add(IMOAction.sensitive(IMOAction.Type.CALL,number)); return a;}
        String target=extractSearchTarget(n,q); if(!target.isEmpty()){a.add(IMOAction.of(IMOAction.Type.CLICK,"cari"));a.add(IMOAction.waitFor(400));a.add(IMOAction.of(IMOAction.Type.TYPE,target));a.add(IMOAction.waitFor(900));a.add(IMOAction.of(IMOAction.Type.READ,null));return a;}
        if(has(n,"kirim","send","hapus","delete","bayar","transfer","beli")){String t=extractSensitiveTarget(q,n);a.add(IMOAction.sensitive(IMOAction.Type.CLICK,t.isEmpty()?sensitiveButton(n):t));return a;}
        if(app!=null)a.add(IMOAction.of(IMOAction.Type.OPEN_APP,app)); return a;
    }
    private static IMOAction actionForClick(String t){String x=normalize(t);return has(x,"kirim","hapus","bayar","transfer","beli","delete","send")?IMOAction.sensitive(IMOAction.Type.CLICK,t):IMOAction.of(IMOAction.Type.CLICK,t);}
    private static String appPackage(String n){if(has(n,"whatsapp","wa"))return "com.whatsapp";if(has(n,"chrome","google chrome","browser"))return "com.android.chrome";if(has(n,"youtube","you tube"))return "com.google.android.youtube";if(has(n,"telegram"))return "org.telegram.messenger";if(has(n,"instagram","ig"))return "com.instagram.android";if(has(n,"facebook","fb"))return "com.facebook.katana";return null;}
    private static boolean isOpenIntent(String n){return has(n,"buka","bukakan","jalankan","masuk","open","jalani")||n.equals("whatsapp")||n.equals("wa")||n.equals("chrome")||n.equals("youtube")||n.equals("telegram");}
    private static String normalize(String s){return s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static boolean has(String s,String...v){for(String x:v)if(s.contains(x))return true;return false;}
    private static String extractAfter(String n,String original,String...p){for(String x:p)if(n.startsWith(x+" "))return original.substring(x.length()).trim();return "";}
    private static String extractSearchTarget(String n,String o){String[]p={"cari ","carikan ","temukan ","temuin ","search ","tolong cari ","tolong carikan "};for(String x:p)if(n.startsWith(x))return o.substring(x.length()).trim().replaceAll("(?i)\\s+(di|dalam|pada)\\s+(whatsapp|wa|chrome|youtube|telegram)$","").trim();return "";}
    private static String extractSensitiveTarget(String o,String n){return extractAfter(n,o,"klik","tekan","pilih");}
    private static String sensitiveButton(String n){if(n.contains("hapus")||n.contains("delete"))return "hapus";if(n.contains("bayar"))return "bayar";if(n.contains("transfer"))return "transfer";if(n.contains("beli"))return "beli";return "kirim";}
}
