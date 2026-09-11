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

/** Reliable Gemini REST client for LUNA. */
public final class GeminiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String MODEL = "gemini-2.5-flash";
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
    private final String apiKey;
    public GeminiClient(String apiKey) { this.apiKey = apiKey; }

    public String plan(String command, String screen, String imageBase64Jpeg, String conversation) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalStateException("API key Gemini belum diatur. Tekan GEMINI API KEY lalu simpan key Gemini.");
        JSONObject root = new JSONObject();
        root.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text",
                "Kamu adalah LUNA, operator Android yang aman, teliti, natural, dan berbahasa Indonesia. " +
                "Untuk percakapan biasa, done=true dan actions kosong. Untuk tugas Android, kerjakan GOAL langkah demi langkah. " +
                "Pilih tepat SATU tindakan per putaran. Gunakan bukti dari accessibility tree dan screenshot. Jangan mengarang target. " +
                "Setelah tindakan, putaran berikutnya harus mengamati layar lagi. Jika target belum terlihat, cari dengan scroll/swipe atau strategi lain. " +
                "Hindari pengulangan tindakan yang gagal. Tindakan sensitif seperti mengirim, menghapus, membeli, transfer, mengubah keamanan, atau perubahan permanen wajib confirm=true. " +
                "done=true hanya jika bukti layar saat ini menunjukkan tujuan benar-benar tercapai. Jika belum selesai, done=false. Selalu jawab singkat dalam bahasa Indonesia."))));

        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject().put("role", "user");
        JSONArray parts = new JSONArray();
        StringBuilder prompt = new StringBuilder();
        prompt.append("TUJUAN/PERINTAH PENGGUNA:\n").append(command == null ? "" : command)
                .append("\n\nRIWAYAT:\n").append(conversation == null || conversation.isEmpty() ? "Tidak ada" : conversation)
                .append("\n\nACCESSIBILITY TREE TERKINI:\n").append(screen == null ? "" : screen)
                .append("\n\nTINDAKAN YANG DIDUKUNG:\nOPEN_APP, CLICK_TEXT, CLICK_DESC, CLICK_ID, CLICK_POINT, LONG_CLICK_TEXT, LONG_CLICK_DESC, LONG_CLICK_ID, LONG_CLICK_POINT, TYPE, SCROLL, SWIPE, BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, WAIT, OPEN_URL, OPEN_SETTINGS, SET_VOLUME, MUTE.\n")
                .append("Jika tujuan sudah tercapai berdasarkan layar saat ini, done=true dan actions=[]; jika belum, done=false dan pilih satu action. Jangan menyatakan selesai hanya karena action berhasil dieksekusi.");
        parts.put(new JSONObject().put("text", prompt.toString()));
        if (imageBase64Jpeg != null && !imageBase64Jpeg.isEmpty()) parts.put(new JSONObject().put("inline_data", new JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64Jpeg)));
        content.put("parts", parts); contents.put(content); root.put("contents", contents);
        JSONObject generationConfig = new JSONObject().put("temperature", 0.15).put("responseMimeType", "application/json").put("responseSchema", responseSchema());
        root.put("generationConfig", generationConfig);
        String raw = callWithRetry(root); String text = extractText(new JSONObject(raw));
        if (text == null || text.trim().isEmpty()) throw new IOException("Gemini tidak mengembalikan respons.");
        return stripMarkdownJson(text);
    }

    private String callWithRetry(JSONObject root) throws Exception {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            Request request = new Request.Builder().url(ENDPOINT).header("x-goog-api-key", apiKey.trim()).header("Content-Type", "application/json").header("Accept", "application/json").post(RequestBody.create(root.toString(), JSON)).build();
            try (Response response = http.newCall(request).execute()) {
                String raw = response.body() == null ? "" : response.body().string();
                if (response.isSuccessful()) return raw;
                last = new IOException(readableApiError(response.code(), raw));
                if (!retryable(response.code()) || attempt == 2) throw last;
            } catch (java.net.SocketTimeoutException | java.net.ConnectException e) {
                last = new IOException("Koneksi ke Gemini terputus. Periksa internet lalu coba lagi.", e);
                if (attempt == 2) throw last;
            }
            Thread.sleep(700L * (attempt + 1));
        }
        throw last == null ? new IOException("Gemini gagal dihubungi.") : last;
    }
    private boolean retryable(int code) { return code == 408 || code == 429 || code >= 500; }

    private JSONObject responseSchema() throws Exception {
        JSONObject action = new JSONObject();
        JSONObject props = new JSONObject();
        props.put("type", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"OPEN_APP","CLICK_TEXT","CLICK_DESC","CLICK_ID","CLICK_POINT","LONG_CLICK_TEXT","LONG_CLICK_DESC","LONG_CLICK_ID","LONG_CLICK_POINT","TYPE","SCROLL","SWIPE","BACK","HOME","RECENTS","NOTIFICATIONS","QUICK_SETTINGS","WAIT","OPEN_URL","OPEN_SETTINGS","SET_VOLUME","MUTE"})));
        props.put("value", new JSONObject().put("type", "string")); props.put("package", new JSONObject().put("type", "string")); props.put("label", new JSONObject().put("type", "string")); props.put("direction", new JSONObject().put("type", "string")); props.put("delayMs", new JSONObject().put("type", "integer")); props.put("distance", new JSONObject().put("type", "integer")); props.put("durationMs", new JSONObject().put("type", "integer")); props.put("x", new JSONObject().put("type", "number")); props.put("y", new JSONObject().put("type", "number")); props.put("level", new JSONObject().put("type", "integer"));
        action.put("type", "object").put("properties", props).put("required", new JSONArray(new String[]{"type"})).put("additionalProperties", false);
        JSONObject schema = new JSONObject().put("type", "object");
        JSONObject rootProps = new JSONObject(); rootProps.put("speak", new JSONObject().put("type", "string")); rootProps.put("confirm", new JSONObject().put("type", "boolean")); rootProps.put("done", new JSONObject().put("type", "boolean")); rootProps.put("actions", new JSONObject().put("type", "array").put("items", action));
        schema.put("properties", rootProps).put("required", new JSONArray(new String[]{"speak","confirm","done","actions"})).put("additionalProperties", false);
        return schema;
    }
    private String extractText(JSONObject root) { JSONArray candidates = root.optJSONArray("candidates"); if (candidates == null) return null; for (int i=0;i<candidates.length();i++){JSONObject c=candidates.optJSONObject(i);if(c==null)continue;JSONObject content=c.optJSONObject("content");if(content==null)continue;JSONArray parts=content.optJSONArray("parts");if(parts==null)continue;StringBuilder out=new StringBuilder();for(int j=0;j<parts.length();j++){JSONObject p=parts.optJSONObject(j);if(p!=null){String text=p.optString("text","");if(!text.isEmpty())out.append(text);}}if(out.length()>0)return out.toString();}return null; }
    private String stripMarkdownJson(String text) { String s=text.trim();if(s.startsWith("```")&&s.endsWith("```")){int first=s.indexOf('\n');if(first>=0)s=s.substring(first+1,s.length()-3).trim();}return s; }
    private String readableApiError(int code,String raw){try{JSONObject root=new JSONObject(raw);JSONObject error=root.optJSONObject("error");String message=error==null?"":error.optString("message","");String status=error==null?"":error.optString("status","");if(code==400)return "Permintaan Gemini tidak valid"+(message.isEmpty()?".":": "+message);if(code==401||code==403)return "API key Gemini ditolak. Pastikan key aktif dan memiliki akses Gemini API.";if(code==429)return "Batas penggunaan Gemini tercapai. Tunggu sebentar lalu coba lagi.";if(code==404)return "Model Gemini tidak ditemukan: "+MODEL;if(code>=500)return "Server Gemini sedang bermasalah. Coba lagi beberapa saat.";return "Gemini HTTP "+code+(status.isEmpty()?"":" ["+status+"]")+(message.isEmpty()?"":": "+message);}catch(Exception ignored){return "Gemini HTTP "+code+": "+raw;}}
}