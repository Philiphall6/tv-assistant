package fr.maison.tvlauncher;

import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;
import java.net.URI;

/** Explicit user-selected destination; never adds launcher credentials or parses intent URIs. */
final class CustomVoiceService {
    static void packageName(String value){
        if(!value.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")||value.equals("fr.maison.tvlauncher"))
            throw new IllegalArgumentException("Renseignez le paquet d’une autre application, par exemple com.exemple.assistant.");
    }
    static URI template(String value){
        try{
            if(value.length()>2048||value.chars().anyMatch(c->c<=32||c==127)||value.contains("\\")||!value.contains("{query}"))throw new Exception();
            URI uri=new URI(value.replace("{query}","hatvquery"));
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||uri.getRawAuthority().contains("hatvquery")||uri.getPort()>65535)throw new Exception();
            return uri;
        }catch(Exception e){throw new IllegalArgumentException("Adresse HTTPS sans identifiant ni fragment, avec {query} dans le chemin ou la recherche.");}
    }
    static Intent search(String template,String sentence){
        template(template);
        if(sentence.trim().isEmpty()||sentence.length()>4096)throw new IllegalArgumentException("Demande vide ou trop longue.");
        return new Intent(Intent.ACTION_VIEW,Uri.parse(template.replace("{query}",Uri.encode(sentence)))).addCategory(Intent.CATEGORY_BROWSABLE);
    }
    static Intent application(String pkg,int action,String language){
        packageName(pkg);
        String[] actions={RecognizerIntent.ACTION_WEB_SEARCH,Intent.ACTION_VOICE_COMMAND,Intent.ACTION_ASSIST};
        if(action<0||action>=actions.length)throw new IllegalArgumentException("Action vocale invalide.");
        return new Intent(actions[action]).setPackage(pkg).putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);
    }
}
