package com.imo.operator;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Local voice identity. Raw microphone audio is never persisted. */
public final class IMOVoiceIdentity {
    private static final String PREF = "imo_voice_identity";
    private static final String KEY_EMBEDDING = "embedding_v5";
    private static final String KEY_LEGACY_V4 = "embedding_v4";
    private static final String KEY_LEGACY = "embedding";
    private static final String KEY_SLOT = "crypto_slot";
    private static final String AES_ALIAS = "IMO_VOICE_IDENTITY_AES_GCM_V5";
    private static final String RSA_ALIAS = "IMO_VOICE_IDENTITY_RSA_V5";
    private static final String[] COMPAT_ALIASES = {
            "IMO_VOICE_IDENTITY_AES_GCM_V4",
            "IMO_VOICE_IDENTITY_AES_V3_128",
            "IMO_VOICE_IDENTITY_AES_V3_256",
            "IMO_VOICE_IDENTITY_AES"
    };
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    public IMOVoiceIdentity(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized void enroll(float[] embedding) {
        if (!valid(embedding)) throw new IllegalArgumentException("Voice embedding tidak valid");
        byte[] plain = toBytes(embedding);
        Exception aesFailure = null;
        try {
            byte[] packed = encrypt(plain, getOrCreateAesKey());
            save(packed, "aes-gcm-v5");
            float[] check = decryptAes(packed);
            if (check == null || cosineSimilarity(embedding, check) < 0.999f)
                throw new IllegalStateException("AES read-back voiceprint tidak cocok");
            return;
        } catch (Exception e) {
            aesFailure = e;
            deleteAlias(AES_ALIAS);
        }

        // OEM-safe fallback: a random AES data key is wrapped by an RSA-2048 Android Keystore key.
        // This avoids depending on the problematic OEM AES-GCM Keystore implementation.
        try {
            byte[] packed = hybridEncrypt(plain, getOrCreateRsaPair());
            save(packed, "rsa2048-oaep-hybrid-v5");
            float[] check = decryptHybrid(packed);
            if (check == null || cosineSimilarity(embedding, check) < 0.999f)
                throw new IllegalStateException("RSA hybrid read-back voiceprint tidak cocok");
            return;
        } catch (Exception rsaFailure) {
            String a = rootMessage(aesFailure);
            String r = rootMessage(rsaFailure);
            throw new IllegalStateException("Penyimpanan voiceprint gagal. AES Keystore: " + a + ". Fallback RSA: " + r, rsaFailure);
        }
    }

    public synchronized boolean isEnrolled() { return load() != null; }

    public synchronized float[] load() {
        String encoded = prefs.getString(KEY_EMBEDDING, "");
        if (!encoded.isEmpty()) {
            try {
                byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
                float[] result = decryptHybrid(packed);
                if (result != null) return result;
                result = decryptAes(packed);
                if (result != null) return result;
                for (String oldAlias : COMPAT_ALIASES) {
                    result = decryptWithAlias(packed, oldAlias);
                    if (result != null) {
                        try { enroll(result); } catch (Exception ignored) { }
                        return result;
                    }
                }
            } catch (Exception ignored) { }
            return null;
        }
        String v4 = prefs.getString(KEY_LEGACY_V4, "");
        if (!v4.isEmpty()) {
            try {
                float[] result = decryptCompatV4(Base64.decode(v4, Base64.NO_WRAP));
                if (result != null) { try { enroll(result); } catch (Exception ignored) { } return result; }
            } catch (Exception ignored) { }
        }
        return migrateLegacy();
    }

    public synchronized void clear() {
        prefs.edit().remove(KEY_EMBEDDING).remove(KEY_LEGACY_V4).remove(KEY_LEGACY).remove(KEY_SLOT).commit();
        deleteAlias(AES_ALIAS);
        deleteAlias(RSA_ALIAS);
        for (String alias : COMPAT_ALIASES) deleteAlias(alias);
    }

    /** Storage self-test. Does not store a real voice sample. */
    public synchronized String selfTest() {
        try {
            byte[] probe = new byte[]{73,77,79,1,7,9,11,13};
            try {
                byte[] packed = encrypt(probe, getOrCreateAesKey());
                byte[] back = decryptBytes(packed, AES_ALIAS);
                if (same(probe, back)) return "AES-GCM OK";
            } catch (Exception ignored) { deleteAlias(AES_ALIAS); }
            byte[] packed = hybridEncrypt(probe, getOrCreateRsaPair());
            byte[] back = decryptHybridBytes(packed);
            return same(probe, back) ? "RSA-HYBRID OK" : "Keystore read-back gagal";
        } catch (Exception e) { return "AES/RSA Keystore tidak tersedia: " + rootMessage(e); }
    }

    public static float cosineSimilarity(float[] a, float[] b) {
        if (!valid(a) || !valid(b) || a.length != b.length) return -1f;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i]; }
        if (aa <= 0 || bb <= 0) return -1f;
        return (float)(dot / (Math.sqrt(aa) * Math.sqrt(bb)));
    }

    private void save(byte[] packed, String slot) {
        boolean committed = prefs.edit().putString(KEY_EMBEDDING, Base64.encodeToString(packed, Base64.NO_WRAP))
                .putString(KEY_SLOT, slot).remove(KEY_LEGACY_V4).remove(KEY_LEGACY).commit();
        if (!committed) throw new IllegalStateException("SharedPreferences commit gagal");
    }

    private SecretKey getOrCreateAesKey() throws Exception {
        SecretKey existing = getExistingAes(AES_ALIAS);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(AES_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(128).setUserAuthenticationRequired(false).build());
        return generator.generateKey();
    }

    private SecretKey getExistingAes(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(alias)) return null;
        java.security.Key key = ks.getKey(alias, null);
        return key instanceof SecretKey ? (SecretKey) key : null;
    }

    private KeyPair getOrCreateRsaPair() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(RSA_ALIAS)) {
            PrivateKey privateKey = (PrivateKey) ks.getKey(RSA_ALIAS, null);
            PublicKey publicKey = ks.getCertificate(RSA_ALIAS).getPublicKey();
            return new KeyPair(publicKey, privateKey);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(RSA_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setKeySize(2048).setUserAuthenticationRequired(false).build());
        return generator.generateKeyPair();
    }

    private byte[] encrypt(byte[] plain, SecretKey key) throws Exception {
        byte[] iv = new byte[12]; random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain);
        ByteBuffer out = ByteBuffer.allocate(1 + iv.length + encrypted.length); out.put((byte)iv.length).put(iv).put(encrypted); return out.array();
    }

    private byte[] decryptBytes(byte[] packed, String alias) {
        try {
            if (packed == null || packed.length < 20) return null;
            ByteBuffer in = ByteBuffer.wrap(packed); int ivLen = in.get() & 0xff;
            if (ivLen < 12 || ivLen > 16 || packed.length <= 1 + ivLen) return null;
            byte[] iv = new byte[ivLen]; in.get(iv); byte[] encrypted = new byte[in.remaining()]; in.get(encrypted);
            SecretKey key = getExistingAes(alias); if (key == null) return null;
            Cipher cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception ignored) { return null; }
    }

    private float[] decryptAes(byte[] packed) { return fromBytes(decryptBytes(packed, AES_ALIAS)); }
    private float[] decryptWithAlias(byte[] packed, String alias) { return fromBytes(decryptBytes(packed, alias)); }

    private byte[] hybridEncrypt(byte[] plain, KeyPair pair) throws Exception {
        byte[] dataKeyBytes = new byte[16]; random.nextBytes(dataKeyBytes); SecretKey dataKey = new SecretKeySpec(dataKeyBytes, "AES");
        byte[] iv = new byte[12]; random.nextBytes(iv);
        Cipher aes = Cipher.getInstance(TRANSFORMATION); aes.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(128, iv));
        byte[] ciphertext = aes.doFinal(plain);
        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION); rsa.init(Cipher.WRAP_MODE, pair.getPublic());
        byte[] wrapped = rsa.wrap(dataKey);
        ByteBuffer out = ByteBuffer.allocate(1 + 1 + iv.length + 2 + wrapped.length + ciphertext.length);
        out.put((byte)1).put((byte)iv.length).put(iv).putShort((short)wrapped.length).put(wrapped).put(ciphertext); return out.array();
    }

    private byte[] decryptHybridBytes(byte[] packed) throws Exception {
        if (packed == null || packed.length < 40) return null;
        ByteBuffer in = ByteBuffer.wrap(packed); if ((in.get() & 0xff) != 1) return null; int ivLen = in.get() & 0xff;
        if (ivLen < 12 || ivLen > 16) return null; byte[] iv = new byte[ivLen]; in.get(iv); int wrappedLen = in.getShort() & 0xffff;
        if (wrappedLen <= 0 || wrappedLen > in.remaining()) return null; byte[] wrapped = new byte[wrappedLen]; in.get(wrapped); byte[] ciphertext = new byte[in.remaining()]; in.get(ciphertext);
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); PrivateKey privateKey = (PrivateKey) ks.getKey(RSA_ALIAS, null); if (privateKey == null) return null;
        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION); rsa.init(Cipher.UNWRAP_MODE, privateKey); SecretKey dataKey = (SecretKey) rsa.unwrap(wrapped, "AES", Cipher.SECRET_KEY);
        Cipher aes = Cipher.getInstance(TRANSFORMATION); aes.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(128, iv)); return aes.doFinal(ciphertext);
    }

    private float[] decryptHybrid(byte[] packed) {
        try { return fromBytes(decryptHybridBytes(packed)); } catch (Exception ignored) { return null; }
    }

    private float[] decryptCompatV4(byte[] packed) {
        for (String alias : COMPAT_ALIASES) { float[] r = fromBytes(decryptBytes(packed, alias)); if (r != null) return r; }
        return null;
    }

    private float[] migrateLegacy() {
        String raw = prefs.getString(KEY_LEGACY, ""); if (raw.isEmpty()) return null;
        String[] parts = raw.split(","); float[] result = new float[parts.length];
        try { for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i]); if (!valid(result)) return null; enroll(result); return result; }
        catch (Exception ignored) { return null; }
    }

    private void deleteAlias(String alias) { try { KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); if (ks.containsAlias(alias)) ks.deleteEntry(alias); } catch (Exception ignored) { } }
    private static byte[] toBytes(float[] v) { ByteBuffer b = ByteBuffer.allocate(4 * v.length); for (float x : v) b.putFloat(x); return b.array(); }
    private static float[] fromBytes(byte[] bytes) { if (bytes == null || bytes.length % 4 != 0) return null; float[] v = new float[bytes.length / 4]; ByteBuffer b = ByteBuffer.wrap(bytes); for (int i = 0; i < v.length; i++) v[i] = b.getFloat(); return valid(v) ? v : null; }
    private static boolean valid(float[] v) { if (v == null || v.length < 8) return false; for (float x : v) if (Float.isNaN(x) || Float.isInfinite(x)) return false; return true; }
    private static boolean same(byte[] a, byte[] b) { if (a == null || b == null || a.length != b.length) return false; for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false; return true; }
    private static String rootMessage(Throwable t) { Throwable cur = t; String last = null; for (int i = 0; i < 8 && cur != null; i++, cur = cur.getCause()) { if (cur.getMessage() != null && !cur.getMessage().trim().isEmpty()) last = cur.getMessage(); } return last == null ? (t == null ? "kesalahan tidak diketahui" : t.getClass().getSimpleName()) : last; }
}
