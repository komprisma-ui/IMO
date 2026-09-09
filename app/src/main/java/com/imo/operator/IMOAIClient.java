package com.imo.operator;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Secure, provider-neutral reasoning client using the OpenAI-compatible Responses API. */
public final class IMOAIClient {
    private static final String ENDPOINT_KEY="ai_endpoint", MODEL_KEY="ai_model", API_KEY="ai_api_key";
    private final IMOSecureStore store;
    public IMOAIClient(Context context){store=new IMOSecureStore(context);}
    public boolean configured(){return !store.get(API_KEY).trim().isEmpty();}
    /** High-intelligence default; the user can override it in AI Cerdas. */
    public String model(){String m=store.get(MODEL_KEY);return m.isEmpty()?"gpt-5.6":m;}
    public String endpoint(){String e=store.get(ENDPOINT_KEY);return e.isEmpty()?"https://api.openai.com/v1/responses":e;}
    public void configure(String endpoint,String model,String apiKey)throws Exception{store.put(ENDPOINT_KEY,endpoint==null?"":endpoint.trim());store.put(MODEL_KEY,model==null?"":model.trim());store.put(API_KEY,apiKey==null?"":apiKey.trim());}
    public void clear(){store.clear(API_KEY);store.clear(ENDPOINT_KEY);store.clear(MODEL_KEY);}

    public String reason(String system,String conversation,String screenObservation)throws Exception{
        String key=store.get(API_KEY);if(key.isEmpty())throw new IllegalStateException("AI belum dikonfigurasi");
        String prompt=(system==null?"":system)+"\n\nKONTEKS LAYAR:\n"+(screenObservation==null?"":screenObservation)+"\n\nDIALOG:\n"+(conversation==null?"":conversation);
        String body="{\"model\":"+json(model())+",\"input\":"+json(prompt)+",\"reasoning\":{\"effort\":\"high\"}}";
        Exception last=null;
        for(int attempt=0;attempt<2;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(endpoint()).openConnection();
                c.setRequestMethod("POST");c.setConnectTimeout(9000);c.setReadTimeout(30000);c.setDoOutput(true);
                c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()){out.write(bytes);}
                int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();String response=read(stream);
                if(code>=200&&code<300){String text=extractOutputText(response);if(text.isEmpty())throw new IllegalStateException("AI mengembalikan jawaban kosong");return text;}
                String msg="AI HTTP "+code+": "+compactError(response);
                if(code==408||code==409||code==429||code>=500){last=new IllegalStateException(msg);if(attempt==0){Thread.sleep(350);continue;}}
                throw new IllegalStateException(msg);
            }catch(Exception e){last=e;if(attempt==0&&isTransient(e)){try{Thread.sleep(350);}catch(InterruptedException x){Thread.currentThread().interrupt();throw x;}continue;}throw e;
            }finally{if(c!=null)c.disconnect();}
        }
        throw last==null?new IllegalStateException("AI gagal dipanggil"):last;
    }
    private static boolean isTransient(Exception e){return e instanceof java.net.SocketTimeoutException||e instanceof java.net.ConnectException||e instanceof java.io.IOException;}
    private static String extractOutputText(String json){Matcher m=Pattern.compile("\\\"output_text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);if(m.find())return unescape(m.group(1));Matcher t=Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);if(t.find())return unescape(t.group(1));return"";}
    private static String compactError(String s){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.substring(0,Math.min(400,x.length()));}
    private static String read(InputStream in)throws Exception{if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String x;while((x=r.readLine())!=null)b.append(x);}return b.toString();}
    private static String json(String s){if(s==null)s="";return"\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")+"\"";}
    private static String unescape(String s){return s.replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"").replace("\\\\","\\");}
}
