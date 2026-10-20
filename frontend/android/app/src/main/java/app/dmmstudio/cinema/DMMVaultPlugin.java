package app.dmmstudio.cinema;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
@CapacitorPlugin(name = "DMMVault")
public class DMMVaultPlugin extends Plugin {
    private static final String ALIAS = "dmm.studio.vault.v1";
    private SharedPreferences store() { return getContext().getSharedPreferences("dmm_encrypted_v1", Context.MODE_PRIVATE); }
    private synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private String checkedKey(PluginCall call) {
        String id = call.getString("key");
        if (id == null || id.length() > 256 || id.isEmpty()) throw new IllegalArgumentException("Invalid storage key");
        return id;
    }
    @PluginMethod public void get(PluginCall call) {
        try {
            String id = checkedKey(call), text = store().getString(id, null);
            JSObject out = new JSObject();
            if (text == null) { out.put("value", org.json.JSONObject.NULL); call.resolve(out); return; }
            String[] parts = text.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            out.put("value", new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)); call.resolve(out);
        } catch (Exception e) { call.reject("Encrypted storage could not be read. Saved data was preserved."); }
    }
    @PluginMethod public void set(PluginCall call) {
        try {
            String id = checkedKey(call), value = call.getString("value");
            if (value == null) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            if (!store().edit().putString(id, encrypted).commit()) throw new IllegalStateException();
            call.resolve();
        } catch (Exception e) { call.reject("Encrypted storage could not save the change."); }
    }
    @PluginMethod public void remove(PluginCall call) {
        try { if (!store().edit().remove(checkedKey(call)).commit()) throw new IllegalStateException(); call.resolve(); }
        catch (Exception e) { call.reject("Encrypted storage could not remove the setting."); }
    }
}
