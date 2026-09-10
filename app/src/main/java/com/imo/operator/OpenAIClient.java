package com.imo.operator;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class OpenAIClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build();
    private final String apiKey, model;
    public OpenAIClient(String apiKey, String model) { this.apiKey=apiKey; this.model=model; }

    public String plan(String command, String screen, String imageBase64Jpeg) throws Exception {
        if(apiKey==null||apiKey.trim().isEmpty()) throw new IllegalStateException("API key belum diatur.");
        JSONObject body=new JSONObject(); body.put("model",model); body.put("store",false);
        body.put("instructions","Kamu adalah LUNA, operator Android yang benar-benar mengoperasikan aplikasi di HP. Bukan chatbot. Pahami TUJUAN pengguna, lihat ACCESSIBILITY TREE dan SCREENSHOT, lalu pilih SATU tindakan berikutnya. Setelah tindakan, siklus berikutnya akan melihat layar lagi untuk memastikan hasilnya. Kamu boleh membuka aplikasi, mencari tombol, menekan tombol, tekan lama, mengetik, menggulir, swipe, kembali, home, recent apps, dan membuka URL. Utamakan id resource, teks, content description, lalu koordinat visual jika elemen hanya terlihat di screenshot. Koordinat harus berasal dari screenshot terkini dan berada di dalam layar. Jangan menebak. Untuk membuka aplikasi gunakan OPEN_APP dengan package bila diketahui atau label bila tidak. Untuk input gunakan TYPE hanya setelah field aktif. Untuk tindakan sensitif seperti kirim pesan, hapus, beli, transfer uang, mengubah keamanan, atau tindakan permanen, set confirm=true. Jangan melakukan tindakan sensitif tanpa konfirmasi. Jika tujuan sudah tercapai, actions kosong dan speak menjelaskan hasil. Jika layar belum siap, gunakan WAIT. Maksimal satu action per respons.");
        JSONArray input=new JSONArray(); JSONObject msg=new JSONObject().put("role","user"); JSONArray content=new JSONArray();
        content.put(new JSONObject().put("type","input_text").put("text","PERINTAH PENGGUNA:\n"+command+"\n\nACCESSIBILITY TREE TERKINI:\n"+screen));
        if(imageBase64Jpeg!=null&&!imageBase64Jpeg.isEmpty()) content.put(new JSONObject().put("type","input_image").put("image_url","data:image/jpeg;base64,"+imageBase64Jpeg));
        msg.put("content",content); input.put(msg); body.put("input",input);
        JSONObject text=new JSONObject(), format=new JSONObject(); format.put("type","json_schema").put("name","device_plan").put("strict",true);
        JSONObject schema=new JSONObject().put("type","object"), props=new JSONObject(); props.put("speak",new JSONObject().put("type","string")); props.put("confirm",new JSONObject().put("type","boolean"));
        JSONObject action=new JSONObject().put("type","object"), ap=new JSONObject();
        ap.put("type",new JSONObject().put("type","string").put("enum",new JSONArray(new String[]{"OPEN_APP","CLICK_TEXT","CLICK_DESC","CLICK_ID","CLICK_POINT","LONG_CLICK_TEXT","LONG_CLICK_POINT","TYPE","SCROLL","SWIPE","BACK","HOME","RECENTS","WAIT","OPEN_URL"})));
        ap.put("value",new JSONObject().put("type","string")); ap.put("package",new JSONObject().put("type","string")); ap.put("label",new JSONObject().put("type","string")); ap.put("direction",new JSONObject().put("type","string")); ap.put("delayMs",new JSONObject().put("type","integer")); ap.put("distance",new JSONObject().put("type","integer")); ap.put("durationMs",new JSONObject().put("type","integer")); ap.put("x",new JSONObject().put("type","number")); ap.put("y",new JSONObject().put("type","number"));
        action.put("properties",ap).put("required",new JSONArray(new String[]{"type","value","package","label","direction","delayMs","distance","durationMs","x","y"})).put("additionalProperties",false);
        props.put("actions",new JSONObject().put("type","array").put("items",action)); schema.put("properties",props).put("required",new JSONArray(new String[]{"speak","confirm","actions"})).put("additionalProperties",false);
        format.put("schema",schema); text.put("format",format); body.put("text",text);
        Request request=new Request.Builder().url("https://api.openai.com/v1/responses").header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").post(RequestBody.create(body.toString(),JSON)).build();
        try(Response r=http.newCall(request).execute()){
            if(!r.isSuccessful()) throw new IOException("OpenAI HTTP "+r.code()+": "+(r.body()==null?"":r.body().string()));
            String raw=r.body()==null?"":r.body().string(); String result=findOutputText(new JSONObject(raw));
            if(result==null||result.trim().isEmpty()) throw new IOException("OpenAI tidak mengembalikan rencana tindakan."); return result;
        }
    }
    private String findOutputText(Object o){
        if(o instanceof JSONObject){ JSONObject j=(JSONObject)o; if("output_text".equals(j.optString("type"))&&j.has("text"))return j.optString("text"); JSONArray ns=j.names(); if(ns!=null)for(int i=0;i<ns.length();i++){String s=findOutputText(j.opt(ns.optString(i)));if(s!=null)return s;} }
        else if(o instanceof JSONArray){JSONArray a=(JSONArray)o;for(int i=0;i<a.length();i++){String s=findOutputText(a.opt(i));if(s!=null)return s;}}
        return null;
    }
}
