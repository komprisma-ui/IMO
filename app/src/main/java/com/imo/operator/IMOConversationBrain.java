package com.imo.operator;

import android.content.Context;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** High-level cognitive layer for natural, multi-turn phone operation. */
public final class IMOConversationBrain {
    public static final class Reply { public final String text; public final boolean execute; public Reply(String text, boolean execute){this.text=text;this.execute=execute;} }
    public static final class IMOEmotionalState { public final int energy, confidence; public final String mood; IMOEmotionalState(int e,int c,String m){energy=e;confidence=c;mood=m;} @Override public String toString(){return "energy="+energy+",confidence="+confidence+",mood="+mood;} }
    private static final class Event { final String kind, summary; Event(String k,String s){kind=k;summary=s;} }
    private final IMOAIClient ai;
    private final Deque<String> history=new ArrayDeque<>();
    private final Deque<Event> memory=new ArrayDeque<>();
    private final int maxTurns=14;
    private int energy=70, confidence=70;
    private static final String SYSTEM="Anda adalah IMO (Intelligent Mobile Operator), otak operator HP berbahasa Indonesia. Pahami tujuan pengguna, amati layar, rencanakan, jalankan hanya melalui executor IMO, lalu verifikasi.\nKONTEKS: gunakan riwayat dan hasil eksekusi untuk memahami itu, yang tadi, di sana, lanjutkan, kembali, dan rujukan lain. Pertahankan aplikasi/objek yang sedang dibahas kecuali tujuan berubah.\nLAYAR: gunakan teks/UI yang diberikan. Jika target ambigu, minta satu klarifikasi singkat; jangan menebak akun, kontak, penerima, nominal, atau target.\nOUTPUT: tindakan tepat satu baris ACTION: diikuti perintah natural bahasa Indonesia; beberapa langkah boleh memakai lalu/kemudian. Percakapan gunakan SAY:. Jangan JSON/markdown.\nSAFETY: pembayaran, transfer, pembelian, pesan, panggilan, penghapusan permanen, uninstall, dan tindakan sensitif harus melewati konfirmasi executor. Jangan meminta/membaca PIN, OTP, password, token, atau rahasia. Jangan melewati lock/security Android.";
    public IMOConversationBrain(Context context){ai=new IMOAIClient(context);}
    public boolean aiConfigured(){return ai.configured();}
    public IMOAIClient ai(){return ai;}
    public synchronized Reply think(String user,String screen)throws Exception{return thinkInternal(user,screen,false);}
    public synchronized Reply thinkFast(String user,String screen)throws Exception{return thinkInternal(user,screen,true);}
    private Reply thinkInternal(String user,String screen,boolean fast)throws Exception{
        if(user==null||user.trim().isEmpty())return new Reply("Saya mendengarkan. Silakan lanjutkan.",false);
        String clean=user.trim();
        boolean urgent=clean.matches("(?i).*\\b(segera|darurat|urgent|cepat|sekarang juga)\\b.*");
        boolean ambiguous=clean.matches("(?i).*\\b(itu|yang tadi|di sana|yang ini)\\b.*")&&(screen==null||screen.trim().isEmpty());
        if(urgent)energy=Math.min(100,energy+8);
        if(ambiguous)confidence=Math.max(25,confidence-10);
        addMemory("USER",clean); history.addLast("USER: "+clean); trim();
        if(!ai.configured())return offline(clean);
        StringBuilder context=new StringBuilder();
        for(String h:history)context.append(h).append('\n');
        context.append("STATE: energy=").append(energy).append(",confidence=").append(confidence).append('\n').append("RECENT MEMORY: ");
        for(Event e:recent(8))context.append(e.kind).append('=').append(e.summary).append("; ");
        String answer=fast?ai.reasonFast(SYSTEM,context.toString(),screen==null?"":screen):ai.reason(SYSTEM,context.toString(),screen==null?"":screen);
        addMemory("IMO",answer); history.addLast("IMO: "+answer); trim();
        return parseReply(answer);
    }
    private Reply parseReply(String answer){
        String c=answer==null?"":answer.trim().replaceFirst("^```(?:text|plaintext)?\\s*","").replaceFirst("\\s*```$","").trim();
        if(c.regionMatches(true,0,"ACTION:",0,7)){
            String[] lines=c.substring(7).trim().split("\\r?\\n"); StringBuilder cmd=new StringBuilder();
            for(String line:lines){String x=line.trim(); if(x.isEmpty())continue; if(x.regionMatches(true,0,"ACTION:",0,7))x=x.substring(7).trim(); if(x.isEmpty())continue; if(cmd.length()>0)cmd.append(" lalu "); cmd.append(x);}
            return cmd.length()==0?new Reply("Saya belum memahami tindakan yang diminta.",false):new Reply(cmd.toString(),true);
        }
        if(c.regionMatches(true,0,"SAY:",0,4)){String s=c.substring(4).trim();return new Reply(s.isEmpty()?"Saya mendengarkan.":s,false);}
        return new Reply(c.isEmpty()?"Saya belum menerima jawaban yang jelas dari AI.":c,false);
    }
    public synchronized void rememberExecution(String text){
        if(text==null||text.trim().isEmpty())return; String c=text.trim();
        boolean success=c.matches("(?i).*(berhasil|selesai|terverifikasi|sukses).*")&&!c.matches("(?i).*(gagal|tidak berhasil|belum berhasil).*");
        if(success){energy=Math.min(100,energy+5);confidence=Math.min(100,confidence+8);}else{energy=Math.max(20,energy-6);confidence=Math.max(20,confidence-10);}
        addMemory("RESULT",c); history.addLast("RESULT: "+c); trim();
    }
    public synchronized IMOEmotionalState emotionalState(){return new IMOEmotionalState(energy,confidence,energy>75?"aktif":confidence<45?"hati-hati":"tenang");}
    public synchronized String personalityPrefix(){return energy>80?"Tenang, sigap, dan ringkas.":confidence<45?"Tenang dan jelaskan jika ada ketidakpastian.":"Ramah, natural, dan langsung membantu.";}
    public synchronized void clear(){history.clear();memory.clear();energy=70;confidence=70;}
    private void addMemory(String k,String s){if(s==null||s.trim().isEmpty())return;memory.addLast(new Event(k,s.trim()));while(memory.size()>32)memory.removeFirst();}
    private List<Event> recent(int n){List<Event>o=new ArrayList<>();int skip=Math.max(0,memory.size()-n),i=0;for(Event e:memory)if(i++>=skip)o.add(e);return o;}
    private void trim(){while(history.size()>maxTurns*2)history.removeFirst();}
    private Reply offline(String u){String n=u.toLowerCase();if(n.contains("siapa kamu")||n.contains("kamu siapa"))return new Reply("Saya IMO, operator HP Anda. Saya siap memahami perintah dan menjalankan tindakan yang diizinkan.",false);if(n.contains("terima kasih")||n.contains("makasih"))return new Reply("Sama-sama. Saya siap melanjutkan.",false);if(n.contains("halo")||n.equals("hai")||n.equals("hi"))return new Reply("Halo. Saya siap mendengarkan.",false);if(n.contains("apa yang bisa kamu lakukan"))return new Reply("Saya dapat membuka aplikasi, membaca layar, mengetik, menavigasi, mengatur fungsi perangkat, dan menjalankan tugas bertahap dengan verifikasi.",false);return new Reply("Perintah lokal tetap tersedia. Untuk percakapan bebas dan tugas kompleks, aktifkan AI Cerdas.",false);}
}