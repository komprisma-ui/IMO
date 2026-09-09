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
    private static final String KEY_EMBEDDING = "embedding_v3";
    private static final String KEY_LEGACY = "embedding";
    private static final String KEY_SLOT = "crypto_slot";
    private static final String ALIAS_256 = "IMO_VOICE_IDENTITY_AES_V3_256";
    private static final String ALIAS_128 = "IMO_VOICE_IDENTITY_AES_V3_128";
    private static final String OLD_ALIAS = "IMO_VOICE_IDENTITY_AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    public IMOVoiceIdentity(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized void enroll(float[] embedding) {
        if (!valid(embedding)) throw new IllegalArgumentException("Invalid voice embedding");
        byte[] plain = toBytes(embedding);
        Exception first = null;
        try {
            byte[] packed = encrypt(plain, ALIAS_256, 256, true);
            save(packed, "256");
            return;
        } catch (Exception e) { first = e; }
        try {
            // Compatibility path for OEM Android Keystore implementations that reject AES-256.
            byte[] packed = encrypt(plain, ALIAS_128, 128, true);
            save(packed, "128");
            return;
        } catch (Exception second) {
            String a = first == null ? "" : first.getClass().getSimpleName();
            String b = second.getClass().getSimpleName();
            throw new IllegalStateException("Voice identity encryption unavailable (AES-256=" + a + ", AES-128=" + b + ")", second);
        }
    }

    public synchronized boolean isEnrolled() { return load() != null; }

    public synchronized float[] load() {
        String encoded = prefs.getString(KEY_EMBEDDING, "");
        if (!encoded.isEmpty()) {
            try {
                byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
                String slot = prefs.getString(KEY_SLOT, "");
                float[] result = tryDecrypt(packed, slot.equals("128") ? ALIAS_128 : ALIAS_256);
                if (result != null) return result;
                if (!slot.equals("128")) {
                    result = tryDecrypt(packed, ALIAS_128);
                    if (result != null) return result;
                }
                // Read profiles created by the previous implementation.
                result = tryDecrypt(packed, OLD_ALIAS);
                if (result != null) return result;
            } catch (Exception ignored) { }
            return null;
        }
        return migrateLegacy();
    }

    public synchronized void clear() {
        prefs.edit().remove(KEY_EMBEDDING).remove(KEY_LEGACY).remove(KEY_SLOT).apply();
        deleteAlias(ALIAS_256);
        deleteAlias(ALIAS_128);
        // Keep the legacy alias unless it is clearly broken; old profiles may still need it.
    }

    public static float cosineSimilarity(float[] a, float[] b) {
        if (!valid(a) || !valid(b) || a.length != b.length) return -1f;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i]; }
        if (aa <= 0 || bb <= 0) return -1f;
        return (float)(dot / (Math.sqrt(aa) * Math.sqrt(bb)));
    }

    private byte[] encrypt(byte[] plain, String alias, int keySize, boolean repairBrokenKey) throws Exception {
        Exception failure = null;
        for (int attempt = 0; attempt < (repairBrokenKey ? 2 : 1); attempt++) {
            try {
                SecretKey key = getOrCreateKey(alias, keySize);
                byte[] iv = new byte[12]; random.nextBytes(iv);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
                byte[] encrypted = cipher.doFinal(plain);
                ByteBuffer out = ByteBuffer.allocate(4 + iv.length + encrypted.length);
                out.putInt(iv.length).put(iv).put(encrypted);
                return out.array();
            } catch (Exception e) {
                failure = e;
                if (attempt == 0) deleteAlias(alias);
            }
        }
        throw failure == null ? new IllegalStateException("encryption failed") : failure;
    }

    private float[] tryDecrypt(byte[] packed, String alias) {
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
            return fromBytes(cipher.doFinal(encrypted));
        } catch (Exception ignored) { return null; }
    }

    private void save(byte[] packed, String slot) {
        boolean committed = prefs.edit()
                .putString(KEY_EMBEDDING, Base64.encodeToString(packed, Base64.NO_WRAP))
                .putString(KEY_SLOT, slot)
                .remove(KEY_LEGACY)
                .commit();
        if (!committed) throw new IllegalStateException("Voice identity could not be committed to local storage");
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

    private SecretKey getOrCreateKey(String alias, int keySize) throws Exception {
        SecretKey existing = getExistingKey(alias);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(keySize)
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
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
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
        float[] v = new float[bytes.length / 4];
        ByteBuffer b = ByteBuffer.wrap(bytes);
        for (int i = 0; i < v.length; i++) v[i] = b.getFloat();
        return valid(v) ? v : null;
    }

    private static boolean valid(float[] v) {
        if (v == null || v.length < 8) return false;
        for (float x : v) if (Float.isNaN(x) || Float.isInfinite(x)) return false;
        return true;
    }
}
