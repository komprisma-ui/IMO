package com.imo.operator;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local voice identity. Only the embedding is stored; raw microphone audio is never persisted. */
public final class IMOVoiceIdentity {
    private static final String PREF = "imo_voice_identity";
    private static final String KEY_EMBEDDING = "embedding_v2";
    private static final String KEY_LEGACY = "embedding";
    private static final String KEY_ALIAS = "IMO_VOICE_IDENTITY_AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final SharedPreferences prefs;

    public IMOVoiceIdentity(Context context) { prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public synchronized void enroll(float[] embedding) {
        if (!valid(embedding)) throw new IllegalArgumentException("Invalid voice embedding");
        try {
            byte[] plain = toBytes(embedding);
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain);
            ByteBuffer out = ByteBuffer.allocate(4 + iv.length + encrypted.length);
            out.putInt(iv.length).put(iv).put(encrypted);
            prefs.edit().putString(KEY_EMBEDDING, Base64.encodeToString(out.array(), Base64.NO_WRAP)).remove(KEY_LEGACY).apply();
        } catch (Exception e) { throw new IllegalStateException("Unable to protect voice identity", e); }
    }

    public synchronized boolean isEnrolled() { return load() != null; }

    public synchronized float[] load() {
        String encoded = prefs.getString(KEY_EMBEDDING, "");
        if (!encoded.isEmpty()) {
            try {
                byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
                ByteBuffer in = ByteBuffer.wrap(packed);
                int ivLen = in.getInt();
                if (ivLen < 12 || ivLen > 16 || packed.length <= 4 + ivLen) return null;
                byte[] iv = new byte[ivLen]; in.get(iv);
                byte[] encrypted = new byte[in.remaining()]; in.get(encrypted);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
                return fromBytes(cipher.doFinal(encrypted));
            } catch (Exception ignored) { return null; }
        }
        return migrateLegacy();
    }

    public synchronized void clear() { prefs.edit().remove(KEY_EMBEDDING).remove(KEY_LEGACY).apply(); }

    public static float cosineSimilarity(float[] a, float[] b) {
        if (!valid(a) || !valid(b) || a.length != b.length) return -1f;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i]; }
        if (aa <= 0 || bb <= 0) return -1f;
        return (float)(dot / (Math.sqrt(aa) * Math.sqrt(bb)));
    }

    private float[] migrateLegacy() {
        String raw = prefs.getString(KEY_LEGACY, "");
        if (raw.isEmpty()) return null;
        String[] parts = raw.split(","); float[] result = new float[parts.length];
        try { for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i]);
            if (!valid(result)) return null; enroll(result); return result;
        } catch (Exception e) { return null; }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }

    private static byte[] toBytes(float[] v) { ByteBuffer b = ByteBuffer.allocate(4 * v.length); for (float x : v) b.putFloat(x); return b.array(); }
    private static float[] fromBytes(byte[] bytes) { if (bytes == null || bytes.length % 4 != 0) return null; float[] v = new float[bytes.length / 4]; ByteBuffer b = ByteBuffer.wrap(bytes); for (int i = 0; i < v.length; i++) v[i] = b.getFloat(); return valid(v) ? v : null; }
    private static boolean valid(float[] v) { if (v == null || v.length < 8) return false; for (float x : v) if (Float.isNaN(x) || Float.isInfinite(x)) return false; return true; }
}
