package com.imo.operator;

import java.text.Normalizer;
import java.util.Locale;

/** Offline intent layer: broad Indonesian understanding without unsafe guessing. */
public final class IMOJarvisCore {
    public enum Intent { OPEN_APP, CLICK, TYPE, READ, NAVIGATE, SCROLL, SYSTEM, CALL, NONE }
    public static final class Decision {
        public final String original, normalized, explanation;
        public final Intent intent;
        public final float confidence;
        public final boolean sensitive, needsConfirmation;
        Decision(String o,String n,Intent i,float c,boolean s,boolean nc,String e){original=o;normalized=n;intent=i;confidence=c;sensitive=s;needsConfirmation=nc;explanation=e;}
    }
    private IMOJarvisCore(){}

    public static Decision understand(String input){
        String original=input==null?"":input.trim(), s=normalize(original);
        if(s.isEmpty())return new Decision(original,"",Intent.NONE,0f,false,false,"Perintah kosong");
        boolean sensitive=containsAny(s,"hapus","delete","uninstall","kirim","transfer","bayar","beli","pesan","posting","publikasi","telepon","panggil","matikan hp","shutdown");
        Intent intent=Intent.NONE; float confidence=.30f; String normalized=s;
        if(isSystem(s)){intent=Intent.SYSTEM;confidence=.97f;}
        else if(containsAny(s,"panggil","telepon","teleponi","call")){intent=Intent.CALL;confidence=.96f;}
        else if(containsAny(s,"buka","bukakan","jalankan","luncurkan","open","launch","masuk ke")){intent=Intent.OPEN_APP;confidence=.95f;}
        else if(containsAny(s,"klik","tekan","tap","pilih","tekan tombol","buka tombol")){intent=Intent.CLICK;confidence=.92f;}
        else if(containsAny(s,"ketik","tuliskan","tulis","isi","masukkan","masukan")){intent=Intent.TYPE;confidence=.93f;}
        else if(containsAny(s,"baca layar","bacakan layar","lihat layar","apa yang ada di layar","jelaskan layar","apa isi layar","informasi layar")){intent=Intent.READ;confidence=.94f;}
        else if(containsAny(s,"kembali","back","mundur","pulang")){intent=Intent.NAVIGATE;confidence=.98f;normalized="kembali";}
        else if(containsAny(s,"home","beranda","layar utama","halaman utama")){intent=Intent.NAVIGATE;confidence=.98f;normalized="home";}
        else if(containsAny(s,"scroll","gulir","geser","usap")){intent=Intent.SCROLL;confidence=.90f;}
        boolean needsConfirmation=sensitive;
        String explanation=confidence<.55f?"Saya belum cukup yakin; perlu klarifikasi.":needsConfirmation?"Tindakan berisiko; konfirmasi diperlukan.":"Maksud dipahami dengan keyakinan tinggi.";
        return new Decision(original,normalized,intent,confidence,sensitive,needsConfirmation,explanation);
    }

    /** Converts speech variants to one planner-friendly sentence; never duplicates command words. */
    public static String normalizeForPlanner(String input){
        String s=normalize(input); if(s.isEmpty())return s;
        s=s.replaceFirst("^(bukakan|jalankan|luncurkan|open|launch)\\s+","buka ")
         .replaceFirst("^(tap|tekanlah|pilihkan)\\s+","klik ")
         .replaceFirst("^(bacakan|tampilkan|tunjukkan)\\s+","baca ")
         .replaceFirst("^(gulir|usap)\\s+","geser ");
        s=s.replace("wi fi","wifi").replace("you tube","youtube");
        return s.trim();
    }
    private static boolean isSystem(String s){return containsAny(s,"kunci layar","kunci hp","kunci ponsel","lock screen","bangunkan layar","nyalakan layar","hidupkan layar","wake screen","nyalakan senter","hidupkan senter","matikan senter","senter nyala","senter mati","flashlight on","flashlight off","aktifkan wifi","nyalakan wifi","matikan wifi","nonaktifkan wifi","aktifkan bluetooth","nyalakan bluetooth","matikan bluetooth","nonaktifkan bluetooth","besarkan volume","naikkan volume","volume naik","kecilkan volume","turunkan volume","volume turun","keraskan suara","pelankan suara","mute","senyapkan suara","buka wifi","buka bluetooth","buka pengaturan","buka settings","notifikasi","panel pengaturan cepat","quick settings","aplikasi terbaru","recent apps","split screen");}
    private static String normalize(String input){return Normalizer.normalize(input==null?"":input,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}\\s]"," ").replaceAll("\\s+"," ").trim();}
    private static boolean containsAny(String s,String...terms){for(String t:terms)if(s.equals(t)||s.startsWith(t+" ")||s.endsWith(" "+t)||s.contains(" "+t+" "))return true;return false;}
}
