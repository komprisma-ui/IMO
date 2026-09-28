package com.imo.operator;

import android.content.Context;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Secure, provider-neutral reasoning client using the OpenAI Responses API. */
public final class IMOAIClient {
    private static final String ENDPOINT_KEY="ai_endpoint", MODEL_KEY="ai_model", API_KEY="ai_api_key";
    private final IMOSecureStore store;
    public IMOAIClient(Context context){store=new IMOSecureStore(context);}
    public boolean configured(){return !store.get(API_KEY).trim().isEmpty();}
    /** Default to the flagship reasoning model; the user can override this in IMO settings. */
    public String model(){String m=store.get(MODEL_KEY);return m.isEmpty()?"gpt-5.6-sol":m;}
    public String endpoint(){String e=store.get(ENDPOINT_KEY);return e.isEmpty()?"https://api.openai.com/v1/responses":e;}
    public void configure(String endpoint,String model,String apiKey)throws Exception{
        String ep=endpoint==null?"":endpoint.trim();
        String mo=model==null?"":model.trim();
        String key=apiKey==null?"":apiKey.trim();
        if(ep.isEmpty())ep="https://api.openai.com/v1/responses";
        if(mo.isEmpty())mo="gpt-5.6-sol";
        if(key.isEmpty())throw new IllegalArgumentException("API key belum diisi");
        store.put(ENDPOINT_KEY,ep);store.put(MODEL_KEY,mo);store.put(API_KEY,key);
    }
    public void clear(){store.clear(API_KEY);store.clear(ENDPOINT_KEY);store.clear(MODEL_KEY);}

    /** High-quality path for conversation and difficult reasoning. */
    public String reason(String system,String conversation,String screenObservation)throws Exception{
        return reasonWithEffort(system,conversation,screenObservation,"high");
    }

    /** Low-latency path for routine autonomous planning. */
    public String reasonFast(String system,String conversation,String screenObservation)throws Exception{
        return reasonWithEffort(system,conversation,screenObservation,"low");
    }

    private String reasonWithEffort(String system,String conversation,String screenObservation,String effort)throws Exception{
        String key=store.get(API_KEY);if(key.isEmpty())throw new IllegalStateException("AI belum dikonfigurasi");
        String prompt=(system==null?"":system)+"\n\nKONTEKS LAYAR:\n"+(screenObservation==null?"":screenObservation)+"\n\nDIALOG:\n"+(conversation==null?"":conversation);
        String body="{\"model\":"+json(model())+",\"input\":"+json(prompt)+",\"reasoning\":{\"effort\":"+json(effort)+"}}";
        return request(body,effort);
    }

    /** Sends one recent front-camera frame as visual context; the frame is never persisted by IMO. */
    public String reasonWithVision(String system,String conversation,String screenObservation,byte[] jpeg)throws Exception{
        if(!configured())throw new IllegalStateException("AI belum dikonfigurasi");
        if(jpeg==null||jpeg.length==0)return reason(system,conversation,screenObservation);
        String prompt=(system==null?"":system)+"\n\nKONTEKS LAYAR:\n"+(screenObservation==null?"":screenObservation)+"\n\nDIALOG:\n"+(conversation==null?"":conversation)+"\n\nMATA IMO: Gambar berasal dari kamera depan. Gunakan sebagai konteks visual. Jangan mengklaim identitas seseorang dari wajah dan jangan melakukan pengenalan biometrik.";
        String image="data:image/jpeg;base64,"+Base64.encodeToString(jpeg,Base64.NO_WRAP);
        String content="[\"{\"type\":\"input_text\",\"text\":"+json(prompt)+"},{\"type\":\"input_image\",\"image_url\":"+json(image)+"}]";
        String input="[\"{\"role\":\"user\",\"content\":"+content+"}]";
        return request("{\"model\":"+json(model())+",\"input\":"+input+",\"reasoning\":{\"effort\":\"low\"}}","low");
    }

    private String request(String body,String effort)throws Exception{
        String key=store.get(API_KEY);Exception last=null;
        int readTimeout="high".equals(effort)?30000:14000;
        for(int attempt=0;attempt<2;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(endpoint()).openConnection();
                c.setRequestMethod("POST");c.setConnectTimeout(7000);c.setReadTimeout(readTimeout);c.setDoOutput(true);
                c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()){out.write(bytes);}
                int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();String response=read(stream);
                if(code>=200&&code<300){
                    String text=extractOutputText(response);
                    if(text.isEmpty())throw new IllegalStateException("AI mengembalikan jawaban kosong");
                    return text;
                }
                String msg="AI HTTP "+code+": "+compactError(response);
                if(code==408||code==409||code==429||code>=500){last=new IllegalStateException(msg);if(attempt==0){Thread.sleep(250);continue;}}
                throw new IllegalStateException(msg);
            }catch(Exception e){
                last=e;
                if(attempt==0&&isTransient(e)){try{Thread.sleep(250);}catch(InterruptedException x){Thread.currentThread().interrupt();throw x;}continue;}
                throw e;
            }finally{if(c!=null)c.disconnect();}
        }
        throw last==null?new IllegalStateException("AI gagal dipanggil"):last;
    }
    private static boolean isTransient(Exception e){return e instanceof java.net.SocketTimeoutException||e instanceof java.net.ConnectException||e instanceof java.io.IOException;}
    private static String extractOutputText(String json){
        Matcher m=Pattern.compile("\\\"output_text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        if(m.find())return unescape(m.group(1));
        Matcher t=Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        if(t.find())return unescape(t.group(1));
        return "";
    }
    private static String compactError(String s){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.substring(0,Math.min(500,x.length()));}
    private static String read(InputStream in)throws Exception{if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String x;while((x=r.readLine())!=null)b.append(x);}return b.toString();}
    private static String json(String s){if(s==null)s="";return"\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")+"\"";}
    private static String unescape(String s){return s.replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"").replace("\\\\","\\");}
}
