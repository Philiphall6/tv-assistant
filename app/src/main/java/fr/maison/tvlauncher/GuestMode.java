package fr.maison.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local convenience lock, not Android parental control. Never synchronized or exported. */
final class GuestMode {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("guest_access",Context.MODE_PRIVATE);}
    static boolean active(Context c){return prefs(c).getBoolean("enabled",false);}
    static boolean valid(String pin){return pin!=null&&pin.matches("[0-9]{4,8}");}
    static byte[] derive(String pin,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,210000,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    static void configure(SharedPreferences p,String pin)throws Exception{
        if(!valid(pin)||p.getBoolean("enabled",false))throw new IllegalArgumentException();
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        byte[] hash=derive(pin,salt);if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(!p.edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("hash",Base64.encodeToString(hash,Base64.NO_WRAP)).putInt("failures",0).remove("until").putBoolean("enabled",true).commit())throw new java.io.IOException();
    }
    static boolean unlock(SharedPreferences p,String pin,long now)throws Exception{
        if(now<p.getLong("until",0)||!valid(pin))return false;
        boolean ok=false;
        try{byte[] salt=Base64.decode(p.getString("salt",""),Base64.NO_WRAP),expected=Base64.decode(p.getString("hash",""),Base64.NO_WRAP);ok=salt.length==16&&expected.length==32&&MessageDigest.isEqual(expected,derive(pin,salt));}catch(IllegalArgumentException malformed){}
        if(Thread.currentThread().isInterrupted())return false;
        if(ok)ok=p.edit().putBoolean("enabled",false).putInt("failures",0).remove("until").commit();
        else{int failures=p.getInt("failures",0)+1;SharedPreferences.Editor e=p.edit().putInt("failures",failures);if(failures>=5)e.putLong("until",now+30000);e.commit();}
        return ok;
    }
    static boolean protectedIntent(Context c,Intent intent){
        if(!active(c)||intent.getComponent()==null||!c.getPackageName().equals(intent.getComponent().getPackageName()))return false;
        String name=intent.getComponent().getClassName();
        return !name.equals(MainActivity.class.getName())&&!name.equals(GuestModeActivity.class.getName())&&!name.equals(HaDashboardActivity.class.getName())&&!name.equals(VoiceSearchActivity.class.getName());
    }
}
