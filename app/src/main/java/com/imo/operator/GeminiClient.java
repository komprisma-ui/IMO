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

/** Gemini REST client for LUNA. Uses the stable Gemini 2.5 Flash model. */
public final class GeminiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String MODEL = "gemini-2.5-flash";
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();
    private final String apiKey;

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public String plan(String command, String screen, String imageBase64Jpeg, String conversation) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API key Gemini belum diatur. Tekan API KEY lalu simpan key Gemini.");
        }

        JSONObject root = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        content.put("role", "user");
        JSONArray parts = new JSONArray();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Kamu adalah LUNA, operator Android dan teman percakapan berbahasa Indonesia.\n")
                .append("MODE: jika pengguna hanya bertanya/mengobrol, actions harus array kosong dan jawab natural. Jika pengguna meminta tindakan Android, pilih SATU tindakan paling tepat untuk putaran ini. Setelah tindakan dijalankan, layar akan dibaca lagi pada putaran berikutnya. Jangan mengaku berhasil sebelum verifikasi. Jangan mengarang tombol, teks, ID, koordinat, atau aplikasi. Gunakan accessibility tree dan screenshot bila tersedia. Jika target belum terlihat, gunakan scroll/swipe/back yang masuk akal. Tindakan sensitif (kirim pesan, hapus, beli, transfer, keamanan, atau perubahan permanen) wajib confirm=true.\n\n")
                .append("TINDAKAN YANG DIDUKUNG: OPEN_APP, CLICK_TEXT, CLICK_DESC, CLICK_ID, CLICK_POINT, LONG_CLICK_TEXT, LONG_CLICK_DESC, LONG_CLICK_ID, LONG_CLICK_POINT, TYPE, SCROLL, SWIPE, BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, WAIT, OPEN_URL, OPEN_SETTINGS, SET_VOLUME, MUTE.\n\n")
                .append("PERTANYAAN/PERINTAH TERKINI:\n").append(command == null ? "" : command)
                .append("\n\nRIWAYAT PERCAKAPAN:\n").append(conversation == null || conversation.isEmpty() ? "Tidak ada" : conversation)
                .append("\n\nACCESSIBILITY TREE TERKINI:\n").append(screen == null ? "" : screen);

        parts.put(new JSONObject().put("text", prompt.toString()));
        if (imageBase64Jpeg != null && !imageBase64Jpeg.isEmpty()) {
            parts.put(new JSONObject().put("inline_data", new JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", imageBase64Jpeg)));
        }
        content.put("parts", parts);
        contents.put(content);
        root.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", responseSchema());
        root.put("generationConfig", generationConfig);

        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent")
                .header("x-goog-api-key", apiKey.trim())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(root.toString(), JSON))
                .build();

        try (Response response = http.newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(readableApiError(response.code(), raw));
            }
            String text = extractText(new JSONObject(raw));
            if (text == null || text.trim().isEmpty()) {
                throw new IOException("Gemini tidak mengembalikan respons.");
            }
            return stripMarkdownJson(text);
        }
    }

    private JSONObject responseSchema() throws Exception {
        JSONObject action = new JSONObject();
        JSONObject actionProperties = new JSONObject();
        actionProperties.put("type", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{
                "OPEN_APP","CLICK_TEXT","CLICK_DESC","CLICK_ID","CLICK_POINT",
                "LONG_CLICK_TEXT","LONG_CLICK_DESC","LONG_CLICK_ID","LONG_CLICK_POINT",
                "TYPE","SCROLL","SWIPE","BACK","HOME","RECENTS","NOTIFICATIONS",
                "QUICK_SETTINGS","WAIT","OPEN_URL","OPEN_SETTINGS","SET_VOLUME","MUTE"
        })));
        actionProperties.put("value", new JSONObject().put("type", "string"));
        actionProperties.put("package", new JSONObject().put("type", "string"));
        actionProperties.put("label", new JSONObject().put("type", "string"));
        actionProperties.put("direction", new JSONObject().put("type", "string"));
        actionProperties.put("delayMs", new JSONObject().put("type", "integer"));
        actionProperties.put("distance", new JSONObject().put("type", "integer"));
        actionProperties.put("durationMs", new JSONObject().put("type", "integer"));
        actionProperties.put("x", new JSONObject().put("type", "number"));
        actionProperties.put("y", new JSONObject().put("type", "number"));
        actionProperties.put("level", new JSONObject().put("type", "integer"));
        action.put("type", "object");
        action.put("properties", actionProperties);
        action.put("required", new JSONArray(new String[]{"type","value","package","label","direction","delayMs","distance","durationMs","x","y","level"}));
        action.put("additionalProperties", false);

        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject props = new JSONObject();
        props.put("speak", new JSONObject().put("type", "string"));
        props.put("confirm", new JSONObject().put("type", "boolean"));
        props.put("actions", new JSONObject().put("type", "array").put("items", action));
        schema.put("properties", props);
        schema.put("required", new JSONArray(new String[]{"speak","confirm","actions"}));
        schema.put("additionalProperties", false);
        return schema;
    }

    private String extractText(JSONObject root) {
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null) return null;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) continue;
            JSONObject content = candidate.optJSONObject("content");
            if (content == null) continue;
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null) continue;
            StringBuilder out = new StringBuilder();
            for (int j = 0; j < parts.length(); j++) {
                JSONObject part = parts.optJSONObject(j);
                if (part != null) {
                    String text = part.optString("text", "");
                    if (!text.isEmpty()) out.append(text);
                }
            }
            if (out.length() > 0) return out.toString();
        }
        return null;
    }

    private String stripMarkdownJson(String text) {
        String s = text.trim();
        if (s.startsWith("```") && s.endsWith("```")) {
            int first = s.indexOf('\n');
            if (first >= 0) s = s.substring(first + 1, s.length() - 3).trim();
        }
        return s;
    }

    private String readableApiError(int code, String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject error = root.optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "");
            String status = error == null ? "" : error.optString("status", "");
            if (code == 400) return "Permintaan Gemini tidak valid" + (message.isEmpty() ? "." : ": " + message);
            if (code == 401 || code == 403) return "API key Gemini ditolak. Pastikan key aktif dan memiliki akses Gemini API.";
            if (code == 429) return "Batas penggunaan Gemini tercapai. Tunggu sebentar lalu coba lagi.";
            if (code == 404) return "Model Gemini tidak ditemukan: " + MODEL;
            if (code >= 500) return "Server Gemini sedang bermasalah. Coba lagi beberapa saat.";
            return "Gemini HTTP " + code + (status.isEmpty() ? "" : " [" + status + "]") + (message.isEmpty() ? "" : ": " + message);
        } catch (Exception ignored) {
            return "Gemini HTTP " + code + ": " + raw;
        }
    }
}
