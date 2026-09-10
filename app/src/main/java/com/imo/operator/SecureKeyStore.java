package com.imo.operator;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;

public final class SecureKeyStore {
    private static final String KS = "AndroidKeyStore";
    private static final String ALIAS = "luna_api_key_v1";
    private final SharedPreferences prefs;
    public SecureKeyStore(Context c) { prefs = c.getSharedPreferences("luna_secure", Context.MODE_PRIVATE); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS); ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }
    public void save(String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
        byte[] ct=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("key", Base64.encodeToString(ct, Base64.NO_WRAP)).putString("iv", Base64.encodeToString(c.getIV(), Base64.NO_WRAP)).apply();
    }
    public String load() throws Exception {
        String ct=prefs.getString("key", null), iv=prefs.getString("iv", null); if(ct==null||iv==null) return "";
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(ct,Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    public void clear(){prefs.edit().clear().apply();}
}
