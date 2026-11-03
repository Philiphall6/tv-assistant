package fr.maison.tvlauncher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONObject;

/** On-demand speech only. No hotword, background microphone or privileged account. */
public final class VoiceSearchActivity extends CardScreen {
    SharedPreferences prefs; EditText query; Button send, speak; boolean ha, custom, busy;
    String conversationId=""; TextToSpeech tts; boolean ttsReady;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=CardStore.prefs(this);int provider=getIntent().getIntExtra("provider",prefs.getInt("voice_provider",0));
        if(provider==2){
            screen("Recherche vocale","Choisissez le service pour cette demande. Aucune écoute n’a commencé.");
            button(box,"Recherche Android / Google TV",()->chooseProvider(0));
            button(box,"Home Assistant Assist",()->chooseProvider(1));
            button(box,customName(),()->chooseProvider(3));
            button(footer,"Retour",this::finish);TvFocus.focusFirst(root);return;
        }
        ha=provider==1;custom=provider==3;
        if(custom&&prefs.getInt("voice_custom_mode",0)==0){
            screen(customName(),"Ouvre uniquement l’application choisie. Le service gère son microphone et ses autorisations ; aucun jeton HA ne lui est transmis.");
            button(box,"Ouvrir le service vocal",this::launchCustomApp);
            button(footer,"Retour",this::finish);TvFocus.focusFirst(root);return;
        }
        screen(custom?customName():ha?"Home Assistant Assist":"Recherche Android / Google TV",custom?"Transcription Android, puis envoi du texte à votre adresse de recherche HTTPS. Aucun jeton HA transmis.":ha?"La voix est transcrite par le service Android, puis le texte est envoyé à votre agent HA avec le compte de cette TV.":"Utilise le service de recherche vocale installé sur cette TV.");
        speak=button(box,"Parler",this::listen);
        query=field("Votre demande","",false);
        send=button(box,custom?"Envoyer au service":ha?"Envoyer à Assist":"Rechercher",()->submit(query.getText().toString()));
        button(footer,"Retour",this::finish);
        if(ha&&prefs.getBoolean("voice_speak_response",true))tts=new TextToSpeech(this,result->{ttsReady=result==TextToSpeech.SUCCESS;if(ttsReady&&tts!=null)tts.setLanguage(Locale.forLanguageTag(prefs.getString("voice_language","fr")));});
        TvFocus.focusFirst(root);
        if(state==null&&!getIntent().getBooleanExtra("manual",false))speak.post(this::listen);
    }
    String customName(){String name=prefs.getString("voice_custom_name","").trim();return name.isEmpty()?"Service personnalisé":name;}
    void launchCustomApp(){
        try{
            String pkg=prefs.getString("voice_custom_package","").trim();CustomVoiceService.packageName(pkg);
            int action=prefs.getInt("voice_custom_action",0);
            Intent intent=action==3?getPackageManager().getLeanbackLaunchIntentForPackage(pkg):CustomVoiceService.application(pkg,action,prefs.getString("voice_language","fr"));
            if(action==3&&intent==null)intent=getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent==null)throw new IllegalArgumentException("Application absente ou sans écran accessible.");
            startActivity(intent);
        }catch(IllegalArgumentException e){message(e.getMessage()+" Réglages : Barre → Recherche vocale.");}
        catch(Exception e){message("Cette application ne prend pas en charge l’action choisie. Essayez « Ouvrir simplement l’application » dans les réglages.");}
    }
    void chooseProvider(int provider){startActivity(new Intent(this,VoiceSearchActivity.class).putExtra("provider",provider));finish();}
    static Intent recognitionIntent(String language){return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,language).putExtra(RecognizerIntent.EXTRA_PROMPT,"TV Assistant").putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);}
    void listen(){
        if(busy||isFinishing())return;
        if(!ha&&!custom){
            try{startActivity(new Intent(RecognizerIntent.ACTION_WEB_SEARCH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,prefs.getString("voice_language","fr")));return;}catch(Exception unavailable){}
        }
        try{startActivityForResult(recognitionIntent(prefs.getString("voice_language","fr")),71);}
        catch(Exception unavailable){message("Reconnaissance vocale indisponible. Activez un service vocal Android compatible ou saisissez votre demande.");query.requestFocus();}
    }
    @Override public void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=71)return;
        if(result!=RESULT_OK||data==null){message("Écoute annulée. Aucune demande envoyée.");return;}
        ArrayList<String> results=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if(results==null||results.isEmpty()){message("Aucune phrase reconnue.");return;}
        query.setText(results.get(0));submit(results.get(0));
    }
    void submit(String text){
        if(busy)return;text=text.trim();if(text.isEmpty()){message("Saisissez ou prononcez une demande.");return;}
        final String sentence=text;
        if(custom){
            try{
                final Intent intent=CustomVoiceService.search(prefs.getString("voice_custom_url",""),sentence);
                Runnable open=()->{try{startActivity(intent);}catch(Exception e){message("Aucune application ne peut ouvrir cette adresse sur la TV.");}};
                if(prefs.getBoolean("voice_custom_confirm",true))confirm("Envoyer à "+customName()+" ?",sentence+"\n\nDestination : "+intent.getData().getHost()+"\nLe texte sera transmis à ce service et pourra rester dans l’historique de son navigateur.",open);else open.run();
            }catch(IllegalArgumentException e){message(e.getMessage()+" Configurez le troisième service dans Barre → Recherche vocale.");}
            return;
        }
        if(!ha){try{startActivity(new Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY,sentence));}catch(Exception unavailable){message("Aucune application de recherche installée sur cette TV.");}return;}
        if(!HaClient.isConfigured(this)){message("Configurez d’abord la connexion Home Assistant.");return;}
        if(prefs.getBoolean("voice_confirm",true))confirm("Envoyer à Home Assistant ?",sentence+"\n\nL’agent peut commander les appareils auxquels le compte TV a accès.",()->process(sentence));
        else process(sentence);
    }
    void process(String text){
        if(busy||isFinishing())return;busy=true;send.setEnabled(false);speak.setEnabled(false);message("Assist réfléchit…");
        HaConversation.process(this,text,conversationId,(ok,answer,id)->runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;busy=false;send.setEnabled(true);speak.setEnabled(true);
            if(!id.isEmpty())conversationId=id;message(answer);
            if(ok&&ttsReady&&tts!=null)tts.speak(answer,TextToSpeech.QUEUE_FLUSH,null,"ha-answer");
        }));
    }
    @Override public void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
