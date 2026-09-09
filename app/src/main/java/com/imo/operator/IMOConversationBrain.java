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
            "Pahami maksud pengguna, konteks percakapan, layar saat ini, dan risiko sebelum menjawab. "+
            "Untuk pertanyaan biasa, jawab natural dan faktual. Untuk perintah HP, keluarkan tepat SATU baris " +
            "ACTION: diikuti perintah natural yang benar-benar dapat dipetakan ke operator perangkat. "+
            "Untuk percakapan tanpa tindakan, keluarkan tepat SATU baris SAY: diikuti jawaban. "+
            "Jangan gunakan markdown, JSON, code fence, atau awalan lain. Jangan mengarang hasil tindakan. "+
            "Jika informasi layar tidak cukup, katakan tidak tahu dan minta detail yang relevan. "+
            "Untuk tindakan sensitif (transfer, pembayaran, pembelian, penghapusan permanen, panggilan), "+
            "biarkan executor meminta konfirmasi eksplisit; jangan mengklaim tindakan sudah berhasil.";
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
        String cleaned=answer==null?"":answer.trim();
        // Be tolerant of common model formatting mistakes without letting arbitrary prose become a device action.
        cleaned=cleaned.replaceFirst("^```(?:text|plaintext)?\\s*"," ").replaceFirst("\\s*```$"," ").trim();
        if(cleaned.regionMatches(true,0,"ACTION:",0,7)){
            String command=cleaned.substring(7).trim();
            int newline=command.indexOf('\n');
            if(newline>=0)command=command.substring(0,newline).trim();
            return command.isEmpty()?new Reply("Saya belum memahami tindakan yang diminta.",false):new Reply(command,true);
        }
        if(cleaned.regionMatches(true,0,"SAY:",0,4)){
            String speech=cleaned.substring(4).trim();
            return new Reply(speech.isEmpty()?"Saya mendengarkan.":speech,false);
        }
        // If the provider ignored the required prefix, never execute it. Treat it as dialogue.
        return new Reply(cleaned.isEmpty()?"Saya belum menerima jawaban yang jelas dari AI.":cleaned,false);
    }

    public synchronized void rememberExecution(String text){if(text==null||text.trim().isEmpty())return;history.addLast("RESULT: "+text.trim());trimHistory();}
    public synchronized void clear(){history.clear();}
    private void trimHistory(){while(history.size()>maxTurns*2)history.removeFirst();}
    private Reply offline(String user){
        String n=user.toLowerCase();
        if(n.contains("siapa kamu")||n.contains("kamu siapa"))return new Reply("Saya IMO. Saya siap membantu mengoperasikan HP, membaca layar, dan menjalankan perintah dengan aman.",false);
        if(n.contains("terima kasih")||n.contains("makasih"))return new Reply("Sama-sama. Saya siap membantu lagi.",false);
        if(n.contains("halo")||n.equals("hai")||n.equals("hi"))return new Reply("Halo. Saya siap mendengarkan.",false);
        return new Reply("Perintah ini membutuhkan AI reasoning. Silakan konfigurasi AI Cerdas agar saya dapat memahami bahasa bebas dan konteks dengan lebih baik.",false);
    }
}
