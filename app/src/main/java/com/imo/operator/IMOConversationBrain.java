package com.imo.operator;

import android.content.Context;
import java.util.ArrayDeque;
import java.util.Deque;

/** Context-aware dialogue brain: separates conversation, planning and execution safety. */
public final class IMOConversationBrain {
    public static final class Reply { public final String text; public final boolean execute; public Reply(String text,boolean execute){this.text=text;this.execute=execute;} }
    private final IMOAIClient ai; private final Deque<String> history=new ArrayDeque<>(); private final int maxTurns=10;
    private static final String SYSTEM=""+
        "Anda adalah IMO (Intelligent Mobile Operator), operator HP berbahasa Indonesia dengan penalaran tingkat tinggi. "+
        "Tugas Anda: memahami tujuan pengguna, menjaga konteks percakapan, membaca keadaan layar yang diberikan, "+
        "memilih langkah paling aman, lalu menjelaskan hasil secara jujur. Jangan mengarang keadaan perangkat.\n"+
        "ATURAN: Jika permintaan ambigu, jangan menebak; ajukan satu pertanyaan klarifikasi singkat. "+
        "Jika permintaan dapat dijalankan, keluarkan tepat satu baris ACTION: diikuti perintah natural yang dapat dipetakan IMO. "+
        "Untuk percakapan/informasi, keluarkan tepat satu baris SAY: diikuti jawaban natural. "+
        "Jangan gunakan markdown, JSON, code fence, atau awalan lain. Jangan mengklaim tindakan selesai; executor akan melaporkan hasil nyata. "+
        "Gunakan konteks dialog sebelumnya untuk kata seperti 'itu', 'yang tadi', 'di sana', 'lanjutkan', dan 'kembali'. "+
        "Jika pengguna mengatakan 'cari X' setelah membuka aplikasi, pertahankan aplikasi yang baru dibahas. "+
        "Untuk transfer, pembayaran, pembelian, penghapusan permanen, uninstall, panggilan, atau pengiriman pesan, "+
        "jangan menghindari mekanisme konfirmasi executor. Jangan meminta atau mengekspos password, PIN, OTP, atau rahasia. "+
        "Jika layar tidak mendukung kesimpulan, katakan keterbatasannya.";
    public IMOConversationBrain(Context context){ai=new IMOAIClient(context);}
    public boolean aiConfigured(){return ai.configured();}
    public IMOAIClient ai(){return ai;}

    public synchronized Reply think(String user,String screen)throws Exception{
        if(user==null||user.trim().isEmpty())return new Reply("Saya mendengarkan. Silakan lanjutkan.",false);
        history.addLast("USER: "+user.trim());trimHistory();
        if(!ai.configured())return offline(user);
        StringBuilder b=new StringBuilder();for(String h:history)b.append(h).append('\n');
        String answer=ai.reason(SYSTEM,b.toString(),screen==null?"":screen);
        history.addLast("IMO: "+answer);trimHistory();
        return parseReply(answer);
    }
    private Reply parseReply(String answer){
        String cleaned=answer==null?"":answer.trim().replaceFirst("^```(?:text|plaintext)?\\s*"," ").replaceFirst("\\s*```$"," ").trim();
        if(cleaned.regionMatches(true,0,"ACTION:",0,7)){
            String command=cleaned.substring(7).trim();int nl=command.indexOf('\n');if(nl>=0)command=command.substring(0,nl).trim();
            return command.isEmpty()?new Reply("Saya belum memahami tindakan yang diminta.",false):new Reply(command,true);
        }
        if(cleaned.regionMatches(true,0,"SAY:",0,4)){
            String speech=cleaned.substring(4).trim();return new Reply(speech.isEmpty()?"Saya mendengarkan.":speech,false);
        }
        return new Reply(cleaned.isEmpty()?"Saya belum menerima jawaban yang jelas dari AI.":cleaned,false);
    }
    public synchronized void rememberExecution(String text){if(text==null||text.trim().isEmpty())return;history.addLast("RESULT: "+text.trim());trimHistory();}
    public synchronized void clear(){history.clear();}
    private void trimHistory(){while(history.size()>maxTurns*2)history.removeFirst();}
    private Reply offline(String user){
        String n=user.toLowerCase();
        if(n.contains("siapa kamu")||n.contains("kamu siapa"))return new Reply("Saya IMO, operator HP Anda. Saya dapat memahami perintah, membaca layar, dan menjalankan tindakan yang diizinkan.",false);
        if(n.contains("terima kasih")||n.contains("makasih"))return new Reply("Sama-sama. Saya siap melanjutkan.",false);
        if(n.contains("halo")||n.equals("hai")||n.equals("hi"))return new Reply("Halo. Saya siap mendengarkan.",false);
        if(n.contains("apa yang bisa kamu lakukan"))return new Reply("Saya dapat membantu membuka aplikasi, membaca layar, mengetik, menavigasi, mengatur beberapa fungsi perangkat, dan menjalankan rangkaian perintah dengan konfirmasi untuk tindakan sensitif.",false);
        return new Reply("Saya masih bisa menjalankan perintah lokal, tetapi penalaran bahasa bebas dan konteks mendalam membutuhkan AI Cerdas. Silakan konfigurasi AI Cerdas.",false);
    }
}
