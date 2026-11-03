package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.WebView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.net.URI;
import java.util.Collections;

/** Official HA external-auth V2, restricted to this TV's renderer and its main frame.
 * No addJavascriptInterface, URL token, localStorage copy, dynamic callback name or TLS bypass.
 * Trusted HA frontend/custom-card code receives the existing token in memory, as an access token.
 */
final class HaWebAuth {
    interface Failure {void fail(String message);}
    static String fingerprint(String token){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){return "";}}
    static String origin(String url){
        URI u=URI.create(url);if(!("https".equals(u.getScheme())||"http".equals(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException("Origine HA invalide");
        String host=u.getHost();if(host.contains(":")&&!host.startsWith("["))host="["+host+"]";
        return u.getScheme()+"://"+host+(u.getPort()<0?"":":"+u.getPort());
    }
    static boolean authorized(String address,String source,String current,boolean main){
        if(!main||current==null||!DashboardUrl.sameOrigin(address,source)||!DashboardUrl.sameOrigin(address,current))return false;
        try{return URI.create(address).getPath().equals(URI.create(current).getPath());}catch(Exception ignored){return false;}
    }
    static boolean attach(Context context,WebView web,String address,Failure failure){
        SharedPreferences prefs=CardStore.prefs(context);
        if(!prefs.getBoolean("ha_cards_native_auth",true)||TokenStore.get(prefs).isEmpty())return false;
        try{
            if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))return false;
            WebViewCompat.addWebMessageListener(web,"externalAppV2",Collections.singleton(origin(address)),(view,message,sourceOrigin,isMainFrame,reply)->{
                if(!authorized(address,sourceOrigin.toString(),view.getUrl(),isMainFrame)||!DashboardUrl.sameOrigin(address,prefs.getString("ha_url","")))return;
                String data=message.getData();if(data==null||data.length()>8192)return;
                try{
                    JSONObject request=new JSONObject(data),payload=request.optJSONObject("payload");if(payload==null)return;
                    String type=request.optString("type");
                    if(type.equals("getExternalAuth")&&"externalAuthSetToken".equals(payload.optString("callback"))){
                        String token=TokenStore.get(prefs);
                        if(token.isEmpty()||!prefs.getBoolean("ha_cards_native_auth",true)){view.evaluateJavascript("window.externalAuthSetToken(false)",null);return;}
                        answer(view,address,prefs,failure,android.os.SystemClock.uptimeMillis()+10000);
                    }else if(type.equals("revokeExternalAuth")&&"externalAuthRevokeToken".equals(payload.optString("callback"))){
                        // This is a user-managed long-lived token, not an app-owned refresh token.
                        // Never revoke the native launcher's credential as a side effect of a web logout.
                        view.evaluateJavascript("window.externalAuthRevokeToken(false)",null);
                    }else if(type.equals("externalBus")&&payload.opt("id") instanceof Number){
                        if(!"config/get".equals(payload.optString("type")))return;
                        JSONObject capabilities=new JSONObject().put("hasSidebar",true).put("hasSettingsScreen",false).put("appVersion","2.12.0");
                        JSONObject response=new JSONObject().put("id",payload.getInt("id")).put("type","result").put("success",true).put("result",capabilities);
                        view.evaluateJavascript("window.externalBus("+response+")",null);
                    }
                }catch(Exception ignored){/* Never log payloads: they may contain authentication data. */}
            });
            return true;
        }catch(RuntimeException|LinkageError ignored){return false;}
    }
    static void answer(WebView view,String address,SharedPreferences prefs,Failure failure,long deadline){
        if(!authorized(address,origin(address),view.getUrl(),true)||!DashboardUrl.sameOrigin(address,prefs.getString("ha_url","")))return;
        if(!prefs.getBoolean("ha_cards_native_auth",true)){view.evaluateJavascript("window.externalAuthSetToken(false)",null);return;}
        String token=TokenStore.get(prefs);
        if(!token.isEmpty()&&fingerprint(token).equals(prefs.getString("ha_authenticated_token_fingerprint",""))){
            if(prefs.getBoolean("ha_authenticated_admin",true)){
                view.evaluateJavascript("window.externalAuthSetToken(false)",null);failure.fail("Utilisez le jeton du compte TV non administrateur");return;
            }
            try{JSONObject auth=new JSONObject().put("access_token",token).put("expires_in",1800);view.evaluateJavascript("window.externalAuthSetToken(true,"+auth+")",null);}catch(Exception ignored){}
        }else if(android.os.SystemClock.uptimeMillis()<deadline){view.postDelayed(()->answer(view,address,prefs,failure,deadline),250);}
        else{view.evaluateJavascript("window.externalAuthSetToken(false)",null);failure.fail("Le compte du jeton HA n’a pas pu être vérifié. Réessayez depuis l’accueil.");}
    }
}
