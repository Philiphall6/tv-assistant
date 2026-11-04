package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.Signature;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONObject;

/** Synthetic metadata and isolated cache only. No internet, installation or HA action. */
final class ApkUpdateTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    interface Checked { void run() throws Exception; }
    static void rejects(Checked action, String message) throws Exception {
        boolean failed = false; try { action.run(); } catch (Exception expected) { failed = true; }
        check(failed, message);
    }
    static PackageInfo pkg(String name, String version, int code) {
        PackageInfo info = new PackageInfo(); info.packageName=name; info.versionName=version; info.setLongVersionCode(code);
        info.applicationInfo=new ApplicationInfo(); info.applicationInfo.minSdkVersion=30; info.applicationInfo.targetSdkVersion=36;
        return info;
    }
    static void run(Instrumentation test) throws Exception {
        Context c = test.getTargetContext(); check(c.getPackageName().endsWith(".audit"), "isolated update test");
        String origin=AppUpdates.RELEASES+"/download/v0.7.5/TVAssistant-2.13.8.apk";
        String cdn="https://release-assets.githubusercontent.com/github-production-release-asset/1444452521/public-id?signature=public";
        check(ApkUpdates.allowedRedirect(origin,origin),"same official URL allowed");
        check(ApkUpdates.allowedRedirect(cdn,origin),"GitHub signed asset redirect allowed");
        for(String url:new String[]{null,"http://release-assets.githubusercontent.com/github-production-release-asset/1444452521/a",
            "https://evil.invalid/a",cdn.replace(".com/",".com.evil.invalid/"),cdn.replace("1444452521","123"),
            cdn.replace("https://","https://private@"),cdn.replace(".com/",".com:444/"),cdn+"#fragment",
            cdn.replace("public-id","../other"),cdn.replace("public-id","%2e%2e/other"),origin+"?token=private","file:///private.apk"})
            check(!ApkUpdates.allowedRedirect(url,origin),"unsafe redirect rejected");
        String digest="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", name="TVAssistant-2.13.8.apk";
        check(ApkUpdates.checksum(digest+"  "+name+"\n",name).equals(digest),"GNU checksum accepted");
        check(ApkUpdates.checksum(digest.toUpperCase()+" *"+name+"\r\n",name).equals(digest),"binary checksum accepted");
        for(String body:new String[]{"",digest+"  other.apk",digest+"  ../"+name,digest+"  "+name+"\n"+digest+"  "+name,"bad  "+name})
            rejects(()->ApkUpdates.checksum(body,name),"missing ambiguous or invalid checksum rejected");
        JSONObject release=MaintenanceTest.release("2.13.8");
        release.getJSONArray("assets").put(new JSONObject().put("name","SHA256SUMS").put("state","uploaded").put("size",89)
            .put("browser_download_url",AppUpdates.RELEASES+"/download/v0.7.3/SHA256SUMS"));
        AppUpdates.Release parsed=AppUpdates.parse(release);
        check(parsed.size==3000000 && parsed.checksumsSize==89,"metadata retains exact size and checksum asset");
        release.getJSONArray("assets").put(release.getJSONArray("assets").get(0));
        rejects(()->AppUpdates.parse(release),"duplicate APK rejected");
        Signature one=new Signature(new byte[]{1,2,3}), two=new Signature(new byte[]{3,2,1});
        check(ApkUpdates.sameSigners(new Signature[]{one,two},new Signature[]{two,one}),"signer sets match independent of order");
        check(!ApkUpdates.sameSigners(new Signature[]{one},new Signature[]{two}),"foreign key rejected");
        check(!ApkUpdates.sameSigners(null,new Signature[]{one}) && !ApkUpdates.sameSigners(new Signature[0],new Signature[0]),"unsigned APK rejected");
        PackageInfo current=pkg(c.getPackageName(),"2.13.7",62), candidate=pkg(c.getPackageName(),"2.13.8",63);
        ApkUpdates.packagePolicy(current,candidate,c.getPackageName(),"2.13.8",30); checks++;
        for(int bad=0;bad<8;bad++) {
            PackageInfo p=pkg(c.getPackageName(),"2.13.8",63);
            switch(bad) {
                case 0:p.packageName="org.example.other";break;
                case 1:p.versionName="2.13.7";break;
                case 2:p.setLongVersionCode(62);break;
                case 3:p.setLongVersionCode(1);break;
                case 4:p.applicationInfo.minSdkVersion=31;break;
                case 5:p.applicationInfo.targetSdkVersion=29;break;
                case 6:p.applicationInfo.flags|=ApplicationInfo.FLAG_DEBUGGABLE;break;
                case 7:p.applicationInfo=null;break;
            }
            rejects(()->ApkUpdates.packagePolicy(current,p,c.getPackageName(),"2.13.8",30),"incompatible or downgraded APK rejected");
        }
        ProviderInfo provider=c.getPackageManager().resolveContentProvider(c.getPackageName()+".updates",0);
        check(provider!=null && !provider.exported && provider.grantUriPermissions,"private URI-grant-only provider");
        check(UpdateApkProvider.allowed(c,UpdateApkProvider.uri(c)),"exact cache URI accepted");
        for(String suffix:new String[]{"/../private","?file=private","#fragment","/extra"})
            check(!UpdateApkProvider.allowed(c,Uri.parse(UpdateApkProvider.uri(c)+suffix)),"path and query escape rejected");
        Map<String,?> before=AppUpdates.prefs(c).getAll();
        try {
            ApkUpdates.clear(c);
            byte[] content="synthetic APK test, never install".getBytes(StandardCharsets.UTF_8);
            try(FileOutputStream out=new FileOutputStream(ApkUpdates.file(c))) {out.write(content);}
            String actual=ApkUpdates.hash(ApkUpdates.file(c),content.length);
            check(ApkUpdates.digest(actual),"bounded SHA256 generated");
            rejects(()->ApkUpdates.hash(ApkUpdates.file(c),content.length+1),"truncated download rejected");
            AppUpdates.prefs(c).edit().putString("ready_version","999.0.0").putString("ready_tag","v0.7.5")
                .putLong("ready_size",content.length).putString("ready_sha",actual).commit();
            check(ApkUpdates.ready(c)!=null,"verified cache metadata survives screen recreation");
            try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(UpdateApkProvider.uri(c),"r")) {
                check(fd!=null && fd.getStatSize()==content.length,"installer can read only APK cache");
            }
            rejects(()->c.getContentResolver().openFileDescriptor(UpdateApkProvider.uri(c),"rw"),"write grant refused");
            rejects(()->ApkUpdates.verify(c,ApkUpdates.file(c),ApkUpdates.ready(c),actual),"non-APK content rejected despite matching hash");
            rejects(()->ApkUpdates.verify(c,ApkUpdates.file(c),ApkUpdates.ready(c),digest),"modified APK rejected");
            Intent intent=ApkUpdates.installer(c);
            check(intent.getComponent()!=null && (intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,"explicit system installer with temporary read grant");
            check((intent.getFlags()&(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))==0,"no write or persistent URI permission");
            check(intent.getExtras().size()==1 && intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT,false),"no credentials sent to installer");
            ApkUpdates.clear(c);
            check(ApkUpdates.ready(c)==null && !ApkUpdates.file(c).exists(),"cancel cleanup scoped to cache");
            ApkUpdates.Task task=new ApkUpdates.Task(); task.cancel();
            rejects(task::check,"cancellation observed before any request");
        } finally { ApkUpdates.clear(c); MaintenanceTest.restore(AppUpdates.prefs(c),before); }
    }
}
