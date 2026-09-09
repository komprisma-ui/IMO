package com.imo.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Semantic planner: turns natural Indonesian requests into safe, deterministic action plans. */
public final class IMOPlanner {
    private IMOPlanner() {}
    public static List<IMOAction> plan(String input) {
        List<IMOAction> actions = new ArrayList<>();
        if (input == null) return actions;
        String q = input.trim(); String n = normalize(q); if (n.isEmpty()) return actions;

        if (containsAny(n,"kunci layar","kunci hp","kunci ponsel","lock screen","lock hp")) { actions.add(IMOAction.of(IMOAction.Type.LOCK_SCREEN,null)); return actions; }
        if (containsAny(n,"bangunkan layar","nyalakan layar","hidupkan layar","wake screen")) { actions.add(IMOAction.of(IMOAction.Type.WAKE_SCREEN,null)); return actions; }
        if (containsAny(n,"nyalakan senter","hidupkan senter","senter nyala","flashlight on")) { actions.add(IMOAction.of(IMOAction.Type.TORCH_ON,null)); return actions; }
        if (containsAny(n,"matikan senter","senter mati","flashlight off")) { actions.add(IMOAction.of(IMOAction.Type.TORCH_OFF,null)); return actions; }
        if (containsAny(n,"buka wifi","buka wi fi","pengaturan wifi","pengaturan wi fi","aktifkan wifi")) { actions.add(IMOAction.of(IMOAction.Type.OPEN_WIFI_SETTINGS,null)); return actions; }
        if (containsAny(n,"buka bluetooth","pengaturan bluetooth","aktifkan bluetooth")) { actions.add(IMOAction.of(IMOAction.Type.OPEN_BLUETOOTH_SETTINGS,null)); return actions; }
        if (containsAny(n,"besarkan volume","volume naik","naikkan volume","keraskan suara")) { actions.add(IMOAction.of(IMOAction.Type.VOLUME_UP,null)); return actions; }
        if (containsAny(n,"kecilkan volume","volume turun","turunkan volume","pelankan suara")) { actions.add(IMOAction.of(IMOAction.Type.VOLUME_DOWN,null)); return actions; }
        if (containsAny(n,"mute","senyapkan suara","diamkan suara")) { actions.add(IMOAction.of(IMOAction.Type.MUTE,null)); return actions; }
        if (containsAny(n,"buka pengaturan","buka settings","pengaturan sistem")) { actions.add(IMOAction.of(IMOAction.Type.OPEN_SYSTEM_SETTINGS,null)); return actions; }

        String number = extractCallNumber(n,q);
        if (!number.isEmpty()) { actions.add(IMOAction.sensitive(IMOAction.Type.CALL,number)); return actions; }

        String app = appPackage(n);
        if (app != null && isOpenIntent(n)) { actions.add(IMOAction.of(IMOAction.Type.OPEN_APP,app)); return actions; }
        if (isBack(n)) { actions.add(IMOAction.of(IMOAction.Type.BACK,null)); return actions; }
        if (isHome(n)) { actions.add(IMOAction.of(IMOAction.Type.HOME,null)); return actions; }
        if (isRead(n)) { actions.add(IMOAction.of(IMOAction.Type.READ,null)); return actions; }
        if (isScrollDown(n)) { actions.add(IMOAction.of(IMOAction.Type.SCROLL_DOWN,null)); return actions; }
        if (isScrollUp(n)) { actions.add(IMOAction.of(IMOAction.Type.SCROLL_UP,null)); return actions; }
        String click=extractAfter(n,q,"klik","tekan","pilih"); if(!click.isEmpty()){actions.add(actionForClick(click));return actions;}
        String type=extractAfter(n,q,"ketik","tulis","isi","masukkan"); if(!type.isEmpty()){actions.add(IMOAction.of(IMOAction.Type.TYPE,type));return actions;}
        String target=extractSearchTarget(n,q); if(!target.isEmpty()){
            if(app!=null&&isOpenIntent(n)){actions.add(IMOAction.of(IMOAction.Type.OPEN_APP,app));actions.add(IMOAction.waitFor(900));}
            actions.add(IMOAction.of(IMOAction.Type.CLICK,"cari"));actions.add(IMOAction.waitFor(400));actions.add(IMOAction.of(IMOAction.Type.TYPE,target));actions.add(IMOAction.waitFor(900));actions.add(IMOAction.of(IMOAction.Type.READ,null));return actions;
        }
        if(containsAny(n,"kirim","send","hapus","delete","bayar","transfer","beli")){actions.add(IMOAction.sensitive(IMOAction.Type.CLICK,sensitiveButton(n)));return actions;}
        if(app!=null) actions.add(IMOAction.of(IMOAction.Type.OPEN_APP,app));
        return actions;
    }
    private static IMOAction actionForClick(String t){String x=normalize(t);boolean s=containsAny(x,"kirim","hapus","bayar","transfer","beli","delete","send");return s?IMOAction.sensitive(IMOAction.Type.CLICK,t):IMOAction.of(IMOAction.Type.CLICK,t);}
    private static String extractCallNumber(String n,String q){if(!containsAny(n,"panggil","telepon","teleponi","call"))return "";String[] p={"panggil ","telepon ","teleponi ","call "};for(String x:p)if(n.startsWith(x))return q.substring(x.length()).replaceAll("[^0-9+]+","");return "";}
    private static String appPackage(String n){if(containsAny(n,"whatsapp","wa"))return"com.whatsapp";if(containsAny(n,"chrome","google chrome","browser"))return"com.android.chrome";if(containsAny(n,"youtube","you tube"))return"com.google.android.youtube";if(containsAny(n,"telegram"))return"org.telegram.messenger";if(containsAny(n,"instagram","ig"))return"com.instagram.android";if(containsAny(n,"facebook","fb"))return"com.facebook.katana";return null;}
    private static boolean isOpenIntent(String n){return containsAny(n,"buka","bukakan","jalankan","masuk","open","jalani")||n.equals("whatsapp")||n.equals("wa")||n.equals("chrome")||n.equals("youtube")||n.equals("telegram");}
    private static boolean isBack(String n){return containsAny(n,"kembali","back","mundur");} private static boolean isHome(String n){return containsAny(n,"home","layar utama","halaman utama","beranda");}
    private static boolean isRead(String n){return containsAny(n,"baca layar","lihat layar","apa yang ada di layar","bacakan layar","baca apa yang tampil");}
    private static boolean isScrollDown(String n){return containsAny(n,"scroll bawah","scroll ke bawah","gulir bawah","gulir ke bawah","geser ke bawah");}
    private static boolean isScrollUp(String n){return containsAny(n,"scroll atas","scroll ke atas","gulir atas","gulir ke atas","geser ke atas");}
    private static String normalize(String s){return s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static boolean containsAny(String s,String...v){for(String x:v)if(s.contains(x))return true;return false;}
    private static String extractAfter(String n,String o,String...p){for(String x:p)if(n.startsWith(x+" "))return o.substring(x.length()).trim();return"";}
    private static String extractSearchTarget(String n,String o){String[]p={"cari ","carikan ","temukan ","temuin ","search ","tolong cari ","tolong carikan "};for(String x:p)if(n.startsWith(x)){String v=o.substring(x.length()).trim();return v.replaceAll("(?i)\\s+(di|dalam|pada)\\s+(whatsapp|wa|chrome|youtube|telegram)$","").trim();}return"";}
    private static String sensitiveButton(String n){if(n.contains("hapus")||n.contains("delete"))return"hapus";if(n.contains("bayar"))return"bayar";if(n.contains("transfer"))return"transfer";if(n.contains("beli"))return"beli";return"kirim";}
}
