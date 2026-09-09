package com.imo.operator;

import android.content.Context;
import java.util.ArrayDeque;
import java.util.Deque;

/** Two-way dialogue coordinator. Keeps a short context window and delegates reasoning to the configured AI. */
public final class IMOConversationBrain {
    public static final class Reply { public final String text; public final boolean execute; public Reply(String text,boolean execute){this.text=text;this.execute=execute;} }
    private final IMOAIClient ai; private final Deque<String> history=new ArrayDeque<>(); private final int maxTurns=8;
    private static final String SYSTEM=""+
            "Anda adalah IMO (Intelligent Mobile Operator), asisten operator HP berbahasa Indonesia. "+
            "Berpikir sistematis seperti profesor lintas disiplin: pahami tujuan, konteks, ambiguitas, risiko, "+
            "dan langkah paling sederhana. Jawaban luwes, singkat saat tindakan, hangat dan natural saat dialog. "+
            "Jangan mengarang hasil tindakan. Jangan meminta OTP, PIN, password, atau rahasia. "+
            "Jika tindakan sensitif (transfer, pembayaran, pembelian, penghapusan permanen, panggilan) diperlukan, "+
            "jelaskan dan tunggu konfirmasi eksplisit. Untuk tugas HP, kembalikan jawaban dengan awalan ACTION: " +
            "diikuti perintah natural jika memang perlu eksekusi; selain itu awalan SAY:.";
    public IMOConversationBrain(Context context){ai=new IMOAIClient(context);}
    public boolean aiConfigured(){return ai.configured();}
    public IMOAIClient ai(){return ai;}
    public synchronized Reply think(String user,String screen)throws Exception{
        if(user==null||user.trim().isEmpty())return new Reply("Saya mendengarkan. Silakan lanjutkan.",false);
        history.addLast("USER: "+user.trim());while(history.size()>maxTurns*2)history.removeFirst();
        if(!ai.configured())return offline(user);
        StringBuilder b=new StringBuilder();for(String h:history)b.append(h).append('\n');
        String answer=ai.reason(SYSTEM,b.toString(),screen==null?"":screen);
        history.addLast("IMO: "+answer);while(history.size()>maxTurns*2)history.removeFirst();
        String cleaned=answer.trim();boolean exec=cleaned.regionMatches(true,0,"ACTION:",0,7);if(exec)cleaned=cleaned.substring(7).trim();else if(cleaned.regionMatches(true,0,"SAY:",0,4))cleaned=cleaned.substring(4).trim();
        return new Reply(cleaned,exec);
    }
    public synchronized void rememberExecution(String text){if(text==null||text.trim().isEmpty())return;history.addLast("RESULT: "+text.trim());while(history.size()>maxTurns*2)history.removeFirst();}
    public synchronized void clear(){history.clear();}
    private Reply offline(String user){String n=user.toLowerCase();if(n.contains("siapa kamu")||n.contains("kamu siapa"))return new Reply("Saya IMO. Saya siap membantu mengoperasikan HP, membaca layar, dan menjalankan perintah dengan aman.",false);if(n.contains("terima kasih")||n.contains("makasih"))return new Reply("Sama-sama. Saya siap kalau Anda membutuhkan saya lagi.",false);if(n.contains("halo")||n.equals("hai")||n.equals("hi"))return new Reply("Halo. Saya siap mendengarkan.",false);return new Reply("Saya siap menjalankan perintah itu, tetapi AI reasoning belum dikonfigurasi. Perintah perangkat yang sederhana tetap bisa saya jalankan secara lokal.",false);}
}
