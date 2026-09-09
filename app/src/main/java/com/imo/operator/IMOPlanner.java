package com.imo.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Natural-language planner: maps Indonesian commands to deterministic, observable device actions. */
public final class IMOPlanner {
    private IMOPlanner() {}
    public static List<IMOAction> plan(String input) {
        List<IMOAction> actions = new ArrayList<>();
        if (input == null) return actions;
        String q=input.trim(); String n=normalize(q); if(n.isEmpty())return actions;

        if(containsAny(n,"kunci layar","kunci hp","kunci ponsel","lock screen","lock hp")){actions.add(IMOAction.of(IMOAction.Type.LOCK_SCREEN,null));return actions;}
        if(containsAny(n,"bangunkan layar","nyalakan layar","hidupkan layar","wake screen")){actions.add(IMOAction.of(IMOAction.Type.WAKE_SCREEN,null));return actions;}
        if(containsAny(n,"nyalakan senter","hidupkan senter","senter nyala","flashlight on")){actions.add(IMOAction.of(IMOAction.Type.TORCH_ON,null));return actions;}
        if(containsAny(n,"matikan senter","senter mati","flashlight off")){actions.add(IMOAction.of(IMOAction.Type.TORCH_OFF,null));return actions;}
        if(containsAny(n,"buka wifi","buka wi fi","pengaturan wifi","pengaturan wi fi","aktifkan wifi","nyalakan wifi","matikan wifi","nonaktifkan wifi")){actions.add(IMOAction.of(IMOAction.Type.OPEN_WIFI_SETTINGS,null));return actions;}
        if(containsAny(n,"buka bluetooth","pengaturan bluetooth","aktifkan bluetooth","nyalakan bluetooth","matikan bluetooth","nonaktifkan bluetooth")){actions.add(IMOAction.of(IMOAction.Type.OPEN_BLUETOOTH_SETTINGS,null));return actions;}
        if(containsAny(n,"besarkan volume","volume naik","naikkan volume","keraskan suara")){actions.add(IMOAction.of(IMOAction.Type.VOLUME_UP,null));return actions;}
        if(containsAny(n,"kecilkan volume","volume turun","turunkan volume","pelankan suara")){actions.add(IMOAction.of(IMOAction.Type.VOLUME_DOWN,null));return actions;}
        if(containsAny(n,"mute","senyapkan suara","diamkan suara")){actions.add(IMOAction.of(IMOAction.Type.MUTE,null));return actions;}
        if(containsAny(n,"buka pengaturan","buka settings","pengaturan sistem","settings sistem")){actions.add(IMOAction.of(IMOAction.Type.OPEN_SYSTEM_SETTINGS,null));return actions;}
        if(containsAny(n,"notifikasi","buka notifikasi","lihat notifikasi","notification")){actions.add(IMOAction.of(IMOAction.Type.NOTIFICATIONS,null));return actions;}
        if(containsAny(n,"quick settings","panel cepat","panel pengaturan cepat","pengaturan cepat")){actions.add(IMOAction.of(IMOAction.Type.QUICK_SETTINGS,null));return actions;}
        if(containsAny(n,"aplikasi terbaru","recent apps","recent app","aplikasi terakhir")){actions.add(IMOAction.of(IMOAction.Type.RECENTS,null));return actions;}
        if(containsAny(n,"menu daya","power menu","menu power","opsi daya")){actions.add(IMOAction.sensitive(IMOAction.Type.POWER_DIALOG,"menu daya"));return actions;}
        if(containsAny(n,"split screen","layar terbagi","layar split")){actions.add(IMOAction.of(IMOAction.Type.SPLIT_SCREEN,null));return actions;}

        String url=extractUrl(q); if(!url.isEmpty()){actions.add(IMOAction.of(IMOAction.Type.OPEN_URL,url));return actions;}
        String number=extractCallNumber(n,q); if(!number.isEmpty()){actions.add(IMOAction.sensitive(IMOAction.Type.CALL,number));return actions;}

        String app=extractAppTarget(n,q);
        if(app!=null&&!app.isEmpty()&&isOpenIntent(n)){actions.add(IMOAction.of(IMOAction.Type.OPEN_APP,app));if(hasTrailingTask(n)){actions.add(IMOAction.waitFor(900));actions.addAll(trailingUiActions(n,q));}return actions;}
        if(isBack(n)){actions.add(IMOAction.of(IMOAction.Type.BACK,null));return actions;}
        if(isHome(n)){actions.add(IMOAction.of(IMOAction.Type.HOME,null));return actions;}
        if(isRead(n)){actions.add(IMOAction.of(IMOAction.Type.READ,null));return actions;}
        if(isScrollDown(n)){actions.add(IMOAction.of(IMOAction.Type.SCROLL_DOWN,null));return actions;}
        if(isScrollUp(n)){actions.add(IMOAction.of(IMOAction.Type.SCROLL_UP,null));return actions;}
        String click=extractAfter(n,q,"klik","tekan","tap","pilih","buka tombol"); if(!click.isEmpty()){actions.add(actionForClick(click));return actions;}
        String longClick=extractAfter(n,q,"tekan lama","tekan dan tahan","long press"); if(!longClick.isEmpty()){actions.add(IMOAction.of(IMOAction.Type.LONG_CLICK,longClick));return actions;}
        String type=extractAfter(n,q,"ketik","tuliskan","tulis","isi","masukkan"); if(!type.isEmpty()){actions.add(IMOAction.of(IMOAction.Type.TYPE,type));return actions;}
        String target=extractSearchTarget(n,q); if(!target.isEmpty()){
            if(app!=null&&isOpenIntent(n)){actions.add(IMOAction.of(IMOAction.Type.OPEN_APP,app));actions.add(IMOAction.waitFor(900));}
            actions.add(IMOAction.of(IMOAction.Type.CLICK,"cari"));actions.add(IMOAction.waitFor(350));actions.add(IMOAction.of(IMOAction.Type.TYPE,target));actions.add(IMOAction.waitFor(700));actions.add(IMOAction.of(IMOAction.Type.READ,null));return actions;
        }
        if(containsAny(n,"kirim","send","hapus","delete","bayar","transfer","beli","uninstall")){actions.add(IMOAction.sensitive(IMOAction.Type.CLICK,sensitiveButton(n)));return actions;}
        if(app!=null)actions.add(IMOAction.of(IMOAction.Type.OPEN_APP,app));
        return actions;
    }
    private static boolean hasTrailingTask(String n){return n.contains(" lalu ")||n.contains(" kemudian ")||n.contains(" setelah itu ");}
    private static List<IMOAction> trailingUiActions(String n,String q){List<IMOAction>a=new ArrayList<>();String after=q;int i=n.indexOf(" lalu ");if(i<0)i=n.indexOf(" kemudian ");if(i>=0)after=q.substring(Math.min(q.length(),i+6)).trim();String click=extractAfter(normalize(after),after,"klik","tekan","pilih");if(!click.isEmpty())a.add(actionForClick(click));return a;}
    private static IMOAction actionForClick(String t){String x=normalize(t);boolean s=containsAny(x,"kirim","hapus","bayar","transfer","beli","delete","send","uninstall","posting");return s?IMOAction.sensitive(IMOAction.Type.CLICK,t):IMOAction.of(IMOAction.Type.CLICK,t);}
    private static String extractCallNumber(String n,String q){String[]p={"panggil ","telepon ","teleponi ","call "};for(String x:p)if(n.startsWith(x)){String number=q.substring(x.length()).replaceAll("[^0-9+]","");return number.length()>=3?number:"";}return"";}
    private static String extractAppTarget(String n,String q){String[]prefix={"buka ","bukakan ","jalankan ","luncurkan ","open ","launch ","masuk ke "};for(String p:prefix)if(n.startsWith(p)){String v=q.substring(p.length()).trim();v=v.replaceFirst("(?i)\\s+(lalu|kemudian|dan)\\s+.*$","").trim();if(v.isEmpty())return null;return knownPackageOrLabel(v);}if(hasWord(n,"whatsapp"))return"whatsapp";if(hasWord(n,"chrome"))return"chrome";if(hasWord(n,"youtube"))return"youtube";if(hasWord(n,"telegram"))return"telegram";if(hasWord(n,"instagram"))return"instagram";if(hasWord(n,"facebook"))return"facebook";return null;}
    private static String knownPackageOrLabel(String v){String n=normalize(v);if(n.equals("wa")||n.equals("whatsapp"))return"com.whatsapp";if(n.equals("chrome")||n.equals("browser"))return"com.android.chrome";if(n.equals("youtube")||n.equals("you tube"))return"com.google.android.youtube";if(n.equals("telegram"))return"org.telegram.messenger";if(n.equals("instagram")||n.equals("ig"))return"com.instagram.android";if(n.equals("facebook")||n.equals("fb"))return"com.facebook.katana";return v;}
    private static boolean isOpenIntent(String n){return containsAny(n,"buka","bukakan","jalankan","luncurkan","masuk ke","open","launch")||hasWord(n,"whatsapp")||hasWord(n,"chrome")||hasWord(n,"youtube")||hasWord(n,"telegram")||hasWord(n,"instagram")||hasWord(n,"facebook");}
    private static boolean isBack(String n){return containsAny(n,"kembali","back","mundur");} private static boolean isHome(String n){return containsAny(n,"home","layar utama","halaman utama","beranda");}
    private static boolean isRead(String n){return containsAny(n,"baca layar","lihat layar","apa yang ada di layar","bacakan layar","baca apa yang tampil","jelaskan layar");}
    private static boolean isScrollDown(String n){return containsAny(n,"scroll bawah","scroll ke bawah","gulir bawah","gulir ke bawah","geser ke bawah");}
    private static boolean isScrollUp(String n){return containsAny(n,"scroll atas","scroll ke atas","gulir atas","gulir ke atas","geser ke atas");}
    private static String extractAfter(String n,String o,String...p){for(String x:p)if(n.startsWith(x+" "))return o.substring(x.length()).trim();return"";}
    private static String extractSearchTarget(String n,String o){String[]p={"cari ","carikan ","temukan ","temuin ","search ","tolong cari ","tolong carikan "};for(String x:p)if(n.startsWith(x)){String v=o.substring(x.length()).trim();return v.replaceAll("(?i)\\s+(di|dalam|pada)\\s+(whatsapp|wa|chrome|youtube|telegram)$","").trim();}return"";}
    private static String extractUrl(String q){Matcher m=Pattern.compile("(?i)\\bhttps?://[^\\s]+|\\b(?:www\\.)[^\\s]+\\.[a-z]{2,}[^\\s]*").matcher(q);if(!m.find())return"";String u=m.group();return u.startsWith("http")?u:"https://"+u;}
    private static String sensitiveButton(String n){if(n.contains("hapus")||n.contains("delete"))return"hapus";if(n.contains("bayar"))return"bayar";if(n.contains("transfer"))return"transfer";if(n.contains("beli"))return"beli";if(n.contains("uninstall"))return"uninstall";return"kirim";}
    private static String normalize(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}\\s:/._-]"," ").replaceAll("\\s+"," ").trim();}
    private static boolean containsAny(String s,String...v){for(String x:v)if(s.equals(x)||s.contains(" "+x+" ")||s.startsWith(x+" ")||s.endsWith(" "+x))return true;return false;}
    private static boolean hasWord(String s,String word){return s.equals(word)||s.startsWith(word+" ")||s.endsWith(" "+word)||s.contains(" "+word+" ");}
}
