package com.imo.operator;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local voice identity. Only the embedding is stored; raw microphone audio is never persisted. */
public final class IMOVoiceIdentity {
    private static final String PREF = "imo_voice_identity";
    private static final String KEY_EMBEDDING = "embedding_v4";
    private static final String KEY_LEGACY = "embedding";
    private static final String KEY_SLOT = "crypto_slot";
    private static final String ALIAS = "IMO_VOICE_IDENTITY_AES_GCM_V4";
    private static final String[] COMPAT_ALIASES = {
            "IMO_VOICE_IDENTITY_AES_V3_128",
            "IMO_VOICE_IDENTITY_AES_V3_256",
            "IMO_VOICE_IDENTITY_AES"
    };
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    public IMOVoiceIdentity(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized void enroll(float[] embedding) {
        if (!valid(embedding)) throw new IllegalArgumentException("Voice embedding tidak valid");
        byte[] plain = toBytes(embedding);
        Exception failure = null;
        // Android 11/OEM compatibility: use AES-128-GCM as the primary Keystore format.
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (attempt == 1) deleteAlias(ALIAS);
                byte[] packed = encrypt(plain, getOrCreateKey(ALIAS));
                save(packed);
                float[] check = decrypt(packed, ALIAS);
                if (check == null || cosineSimilarity(embedding, check) < 0.999f)
                    throw new IllegalStateException("read-back voiceprint tidak cocok");
                return;
            } catch (Exception e) { failure = e; }
        }
        String detail = failure == null ? "kesalahan tidak diketahui" : rootMessage(failure);
        throw new IllegalStateException("Tidak dapat mengamankan voice identity dengan Android Keystore: " + detail, failure);
    }

    public synchronized boolean isEnrolled() { return load() != null; }

    public synchronized float[] load() {
        String encoded = prefs.getString(KEY_EMBEDDING, "");
        if (!encoded.isEmpty()) {
            try {
                byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
                float[] result = decrypt(packed, ALIAS);
                if (result != null) return result;
                for (String oldAlias : COMPAT_ALIASES) {
                    result = decrypt(packed, oldAlias);
                    if (result != null) {
                        // Re-protect old profiles using the current stable format.
                        try { enroll(result); } catch (Exception ignored) { }
                        return result;
                    }
                }
            } catch (Exception ignored) { }
            return null;
        }
        return migrateLegacy();
    }

    public synchronized void clear() {
        prefs.edit().remove(KEY_EMBEDDING).remove(KEY_LEGACY).remove(KEY_SLOT).commit();
        deleteAlias(ALIAS);
        for (String alias : COMPAT_ALIASES) deleteAlias(alias);
    }

    /** Self-test used by diagnostics; does not store a real voice sample. */
    public synchronized String selfTest() {
        try {
            SecretKey key = getOrCreateKey(ALIAS);
            byte[] probe = new byte[]{73,77,79,1,7,9,11,13};
            byte[] packed = encrypt(probe, key);
            byte[] roundTrip = decryptBytes(packed, ALIAS);
            if (roundTrip == null || roundTrip.length != probe.length) return "Keystore read-back gagal";
            for (int i = 0; i < probe.length; i++) if (probe[i] != roundTrip[i]) return "Keystore integrity check gagal";
            return "OK";
        } catch (Exception e) {
            return rootMessage(e);
        }
    }

    public static float cosineSimilarity(float[] a, float[] b) {
        if (!valid(a) || !valid(b) || a.length != b.length) return -1f;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i]; }
        if (aa <= 0 || bb <= 0) return -1f;
        return (float)(dot / (Math.sqrt(aa) * Math.sqrt(bb)));
    }

    private byte[] encrypt(byte[] plain, SecretKey key) throws Exception {
        byte[] iv = new byte[12]; random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain);
        ByteBuffer out = ByteBuffer.allocate(4 + iv.length + encrypted.length);
        out.putInt(iv.length).put(iv).put(encrypted);
        return out.array();
    }

    private float[] decrypt(byte[] packed, String alias) {
        byte[] bytes = decryptBytes(packed, alias);
        return fromBytes(bytes);
    }

    private byte[] decryptBytes(byte[] packed, String alias) {
        try {
            if (packed == null || packed.length < 20) return null;
            ByteBuffer in = ByteBuffer.wrap(packed);
            int ivLen = in.getInt();
            if (ivLen < 12 || ivLen > 16 || packed.length <= 4 + ivLen) return null;
            byte[] iv = new byte[ivLen]; in.get(iv);
            byte[] encrypted = new byte[in.remaining()]; in.get(encrypted);
            SecretKey key = getExistingKey(alias);
            if (key == null) return null;
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception ignored) { return null; }
    }

    private void save(byte[] packed) {
        boolean committed = prefs.edit()
                .putString(KEY_EMBEDDING, Base64.encodeToString(packed, Base64.NO_WRAP))
                .putString(KEY_SLOT, "aes128-gcm-v4")
                .remove(KEY_LEGACY)
                .commit();
        if (!committed) throw new IllegalStateException("SharedPreferences commit gagal");
    }

    private float[] migrateLegacy() {
        String raw = prefs.getString(KEY_LEGACY, "");
        if (raw.isEmpty()) return null;
        String[] parts = raw.split(","); float[] result = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i]);
            if (!valid(result)) return null;
            enroll(result);
            return result;
        } catch (Exception e) { return null; }
    }

    private SecretKey getOrCreateKey(String alias) throws Exception {
        SecretKey existing = getExistingKey(alias);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(128)
                .build());
        return generator.generateKey();
    }

    private SecretKey getExistingKey(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(alias)) return null;
        java.security.Key key = ks.getKey(alias, null);
        return key instanceof SecretKey ? (SecretKey) key : null;
    }

    private void deleteAlias(String alias) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
            if (ks.containsAlias(alias)) ks.deleteEntry(alias);
        } catch (Exception ignored) { }
    }

    private static byte[] toBytes(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(4 * v.length);
        for (float x : v) b.putFloat(x);
        return b.array();
    }

    private static float[] fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length % 4 != 0) return null;
        float[] v = new float[bytes.length / 4]; ByteBuffer b = ByteBuffer.wrap(bytes);
        for (int i = 0; i < v.length; i++) v[i] = b.getFloat();
        return valid(v) ? v : null;
    }

    private static boolean valid(float[] v) {
        if (v == null || v.length < 8) return false;
        for (float x : v) if (Float.isNaN(x) || Float.isInfinite(x)) return false;
        return true;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t; String last = null;
        for (int i = 0; i < 6 && cur != null; i++, cur = cur.getCause()) {
            if (cur.getMessage() != null && !cur.getMessage().trim().isEmpty()) last = cur.getMessage();
        }
        return last == null ? t.getClass().getSimpleName() : last;
    }
}
