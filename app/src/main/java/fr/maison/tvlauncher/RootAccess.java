package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Optional, explicit, read-only root probe. Never a root installer or command console. */
final class RootAccess {
    static final String ENABLED="root_tools_enabled";
    enum Result { DISABLED, GRANTED, DENIED, UNAVAILABLE, TIMEOUT }
    interface Probe { Result run() throws Exception; }
    static Result check(SharedPreferences prefs, Probe probe) {
        if(!prefs.getBoolean(ENABLED,false))return Result.DISABLED;
        try { return probe.run(); } catch(Exception unavailable) { return Result.UNAVAILABLE; }
    }
    static Result probe() throws Exception {
        Process process=null;
        try {
            process=new ProcessBuilder("su","-c","id -u").redirectErrorStream(true).start();
            if(!process.waitFor(20,TimeUnit.SECONDS))return Result.TIMEOUT;
            byte[] output=new byte[64];int length=process.getInputStream().read(output);
            String uid=length<0?"":new String(output,0,length,StandardCharsets.US_ASCII).trim();
            return process.exitValue()==0&&uid.equals("0")?Result.GRANTED:Result.DENIED;
        } finally { if(process!=null){process.destroy();if(process.isAlive())process.destroyForcibly();} }
    }
}
