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

/**
 * Optional cloud reasoning layer. It is intentionally provider-neutral at the UI level,
 * but ships with OpenAI Responses API defaults. Credentials live in Keystore-backed storage.
 * The app always retains a deterministic offline planner if no provider is configured.
 */
public final class IMOAIClient {
    private static final String ENDPOINT_KEY="ai_endpoint";
    private static final String MODEL_KEY="ai_model";
    private static final String API_KEY="ai_api_key";
    private final IMOSecureStore store;
    public IMOAIClient(Context context){store=new IMOSecureStore(context);}
    public boolean configured(){return !store.get(API_KEY).trim().isEmpty();}
    public String model(){String m=store.get(MODEL_KEY);return m.isEmpty()?"gpt-5.6-luna":m;}
    public String endpoint(){String e=store.get(ENDPOINT_KEY);return e.isEmpty()?"https://api.openai.com/v1/responses":e;}
    public void configure(String endpoint,String model,String apiKey)throws Exception{store.put(ENDPOINT_KEY,endpoint==null?"":endpoint.trim());store.put(MODEL_KEY,model==null?"":model.trim());store.put(API_KEY,apiKey==null?"":apiKey.trim());}
    public void clear(){store.clear(API_KEY);store.clear(ENDPOINT_KEY);store.clear(MODEL_KEY);}

    public String reason(String system,String conversation,String screenObservation)throws Exception{
        String key=store.get(API_KEY);if(key.isEmpty())throw new IllegalStateException("AI belum dikonfigurasi");
        String prompt=(system==null?"":system)+"\n\nKONTEKS LAYAR:\n"+(screenObservation==null?"":screenObservation)+"\n\nDIALOG:\n"+(conversation==null?"":conversation);
        String body="{\"model\":"+json(model())+",\"input\":"+json(prompt)+",\"reasoning\":{\"effort\":\"medium\"}}";
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint()).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(9000);c.setReadTimeout(20000);c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}
        int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();String response=read(stream);c.disconnect();if(code<200||code>=300)throw new IllegalStateException("AI HTTP "+code+": "+compactError(response));
        String text=extractOutputText(response);if(text.isEmpty())throw new IllegalStateException("AI mengembalikan jawaban kosong");return text;
    }
    private static String extractOutputText(String json){Matcher m=Pattern.compile("\\\"output_text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);if(m.find())return unescape(m.group(1));Matcher t=Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);if(t.find())return unescape(t.group(1));return"";}
    private static String compactError(String s){return s==null?"":s.replaceAll("\\s+"," ").trim().substring(0,Math.min(400,s.replaceAll("\\s+"," ").trim().length()));}
    private static String read(InputStream in)throws Exception{if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String x;while((x=r.readLine())!=null)b.append(x);}return b.toString();}
    private static String json(String s){if(s==null)s="";return"\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")+"\"";}
    private static String unescape(String s){return s.replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"").replace("\\\\","\\");}
}
