package com.imo.operator;

/** Small bridge that keeps visual reasoning optional and preserves the strict ACTION/SAY protocol. */
public final class IMOVisionReasoner {
    public static final class Reply { public final String text; public final boolean execute; public Reply(String text,boolean execute){this.text=text;this.execute=execute;} }
    private IMOVisionReasoner(){}
    public static Reply think(IMOAIClient ai,String user,String screen,byte[] jpeg)throws Exception {
        String answer=ai.reasonWithVision("Anda adalah IMO, operator HP berbahasa Indonesia. Gunakan kamera depan hanya sebagai konteks visual; jangan mengidentifikasi orang atau melakukan pengenalan biometrik. Amati layar dan visual, pahami tujuan, lalu rencanakan tindakan. Jangan mengarang keadaan. Untuk tindakan keluarkan tepat satu baris ACTION: diikuti perintah natural. Untuk percakapan gunakan SAY:. Tindakan sensitif harus melalui konfirmasi executor. Jangan meminta PIN, OTP, password atau rahasia.",user,screen,jpeg);
        return parse(answer);
    }
    private static Reply parse(String answer){
        String x=answer==null?"":answer.trim();
        if(x.regionMatches(true,0,"ACTION:",0,7)){String body=x.substring(7).trim();if(body.isEmpty())return new Reply("Saya belum memahami tindakannya.",false);return new Reply(body.replace("\n"," lalu "),true);}
        if(x.regionMatches(true,0,"SAY:",0,4))x=x.substring(4).trim();
        return new Reply(x.isEmpty()?"Saya belum menerima jawaban yang jelas.":x,false);
    }
}
