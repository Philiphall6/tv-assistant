package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-local encryption, origin-bound authenticated data and fail-closed legacy migration. */
final class TokenStore {
    static final String KEY_ALIAS="tv-assistant.ha-token.v1", ENCRYPTED="ha_token_encrypted", ORIGIN="ha_token_origin";
    static SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY_ALIAS))return (SecretKey)store.getKey(KEY_ALIAS,null);
        if(!create)throw new IllegalStateException("Credential key unavailable");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
    static String seal(SecretKey key,String token,String origin)throws Exception{
        if(token.isEmpty()||token.length()>16384)throw new IllegalArgumentException("Invalid credential");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(origin.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    static String unseal(SecretKey key,String encoded,String origin)throws Exception{
        if(encoded.length()>24000)throw new IllegalArgumentException("Invalid credential");
        String[] parts=encoded.split(":",-1);if(parts.length!=2)throw new IllegalArgumentException("Invalid credential");
        byte[] iv=Base64.decode(parts[0],Base64.NO_WRAP);if(iv.length!=12)throw new IllegalArgumentException("Invalid credential");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));cipher.updateAAD(origin.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    static synchronized String get(Context context){return get(CardStore.prefs(context));}
    static synchronized String get(SharedPreferences prefs){
        try{
            String origin=ServerAddress.normalize(prefs.getString("ha_url",""));
            if(prefs.contains(ENCRYPTED)){
                if(!origin.equals(prefs.getString(ORIGIN,"")))return "";
                return unseal(key(false),prefs.getString(ENCRYPTED,""),origin);
            }
            String legacy=prefs.getString("ha_token","").trim();if(legacy.isEmpty())return "";
            save(prefs,origin,legacy);return legacy;
        }catch(Exception e){return "";}
    }
    static synchronized void save(SharedPreferences prefs,String address,String token)throws Exception{
        String origin=ServerAddress.normalize(address),sealed=seal(key(true),token.trim(),origin);
        if(!prefs.edit().putString("ha_url",origin).putString(ENCRYPTED,sealed).putString(ORIGIN,origin).remove("ha_token")
                .remove("ha_authenticated_token_fingerprint").remove("ha_authenticated_user").remove("ha_authenticated_user_id").remove("ha_authenticated_admin").commit())
            throw new IllegalStateException(I18n.t("Enregistrement sécurisé impossible. Réessayez."));
    }
    static synchronized boolean clear(Context context){
        return CardStore.prefs(context).edit().remove(ENCRYPTED).remove(ORIGIN).remove("ha_token").remove("ha_authenticated_token_fingerprint")
            .remove("ha_authenticated_user").remove("ha_authenticated_user_id").remove("ha_authenticated_admin").commit();
    }
}
