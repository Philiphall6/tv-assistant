package fr.maison.tvlauncher;

import android.app.Instrumentation;
import javax.crypto.SecretKey;

/** Fake credentials only, in the separate audit application's Android Keystore. */
final class SecurityTest {
    static int checks;
    interface Checked { void run() throws Exception; }
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void rejected(Checked action,String label)throws Exception{
        boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,label);
    }
    static void run(Instrumentation test)throws Exception{
        check(test.getTargetContext().getPackageName().endsWith(".audit"),"isolated key namespace");
        check(ServerAddress.normalize("https://EXAMPLE.invalid:443/").equals("https://example.invalid"),"normalized origin");
        for(String bad:new String[]{"http://example.invalid","https://user:pass@example.invalid","https://example.invalid/path","https://example.invalid?token=fake","https://example.invalid#fragment","https://example.invalid:0","https://example.invalid:70000","file:///tmp/test","https://"})rejected(()->ServerAddress.normalize(bad),"unsafe server rejected");
        SecretKey key=TokenStore.key(true);
        check(key.getEncoded()==null,"key non-exportable");
        String origin="https://security-test.invalid",fake="fake-credential-for-tests-only";
        String first=TokenStore.seal(key,fake,origin),second=TokenStore.seal(key,fake,origin);
        check(!first.equals(second),"unique nonce");
        check(TokenStore.unseal(key,first,origin).equals(fake),"authenticated decryption");
        rejected(()->TokenStore.unseal(key,first,"https://other.invalid"),"wrong origin denied");
        int split=first.indexOf(':')+1;String changed=first.substring(0,split)+(first.charAt(split)=='A'?'B':'A')+first.substring(split+1);
        rejected(()->TokenStore.unseal(key,changed,origin),"tampering denied");
        rejected(()->TokenStore.seal(key,"",origin),"empty token denied");
        PanelRuntimeTest.Memory memory=new PanelRuntimeTest.Memory();
        memory.values.put("ha_url",origin);memory.values.put("ha_token",fake);
        check(TokenStore.get(memory.prefs).equals(fake),"legacy migration returns credential");
        check(!memory.prefs.contains("ha_token")&&memory.prefs.contains(TokenStore.ENCRYPTED),"plaintext removed");
        check(!memory.prefs.getString(TokenStore.ENCRYPTED,"").contains(fake),"ciphertext only");
        memory.values.put("ha_url","https://other.invalid");
        check(TokenStore.get(memory.prefs).isEmpty(),"changed server cannot reuse credential");
        memory.values.put("ha_url",origin);memory.values.put(TokenStore.ENCRYPTED,changed);
        check(TokenStore.get(memory.prefs).isEmpty(),"corrupt credential fails closed");
    }
}
