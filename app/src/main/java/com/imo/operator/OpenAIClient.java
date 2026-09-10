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
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
    private final String apiKey;
    private final String model;

    public OpenAIClient(String apiKey, String model) { this.apiKey = apiKey; this.model = model; }

    public String plan(String command, String screen) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalStateException("API key belum diatur.");
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("store", false);
        body.put("instructions", "Kamu adalah LUNA, AI assistant pengendali Android. Jangan bertindak seperti chatbot biasa. Tujuanmu adalah memahami perintah pengguna, mengamati snapshot layar, lalu menghasilkan rencana tindakan perangkat yang aman. Gunakan bahasa Indonesia. Jangan pernah meminta pengguna melakukan langkah yang sebenarnya dapat dilakukan operator. Tindakan sensitif seperti mengirim pesan, menghapus, membeli, transfer, atau mengubah keamanan harus diberi confirm=true. Untuk percakapan biasa tanpa aksi, actions harus kosong.");
        body.put("input", "PERINTAH PENGGUNA:\n" + command + "\n\nSNAPSHOT LAYAR:\n" + screen);
        JSONObject text = new JSONObject();
        JSONObject format = new JSONObject();
        format.put("type", "json_schema"); format.put("name", "device_plan"); format.put("strict", true);
        JSONObject schema = new JSONObject(); schema.put("type", "object");
        JSONObject props = new JSONObject();
        props.put("speak", new JSONObject().put("type", "string"));
        props.put("confirm", new JSONObject().put("type", "boolean"));
        JSONObject action = new JSONObject().put("type", "object");
        JSONObject ap = new JSONObject();
        ap.put("type", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"OPEN_APP","CLICK_TEXT","CLICK_DESC","TYPE","SCROLL","BACK","HOME","RECENTS","WAIT","OPEN_URL"})));
        ap.put("value", new JSONObject().put("type", "string"));
        ap.put("package", new JSONObject().put("type", "string"));
        ap.put("label", new JSONObject().put("type", "string"));
        ap.put("direction", new JSONObject().put("type", "string"));
        ap.put("delayMs", new JSONObject().put("type", "integer"));
        action.put("properties", ap).put("required", new JSONArray(new String[]{"type","value","package","label","direction","delayMs"})).put("additionalProperties", false);
        props.put("actions", new JSONObject().put("type", "array").put("items", action));
        schema.put("properties", props).put("required", new JSONArray(new String[]{"speak","confirm","actions"})).put("additionalProperties", false);
        format.put("schema", schema); text.put("format", format); body.put("text", text);

        Request request = new Request.Builder().url("https://api.openai.com/v1/responses")
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON)).build();
        try (Response r = http.newCall(request).execute()) {
            if (!r.isSuccessful()) throw new IOException("OpenAI HTTP " + r.code() + ": " + (r.body() == null ? "" : r.body().string()));
            String raw = r.body() == null ? "" : r.body().string();
            JSONObject root = new JSONObject(raw);
            return findOutputText(root);
        }
    }

    private String findOutputText(Object o) {
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject)o;
            if ("output_text".equals(j.optString("type")) && j.has("text")) return j.optString("text");
            JSONArray names = j.names(); if (names != null) for (int i=0;i<names.length();i++) { String n=names.optString(i); String s=findOutputText(j.opt(n)); if(s!=null) return s; }
        } else if (o instanceof JSONArray) {
            JSONArray a=(JSONArray)o; for(int i=0;i<a.length();i++){ String s=findOutputText(a.opt(i)); if(s!=null) return s; }
        }
        return null;
    }
}
