package fr.maison.tvlauncher;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** A single persistent HA frontend above the native canvas, with origin-bound HA external auth. */
final class HaCardSurface extends FrameLayout {
    final MainActivity activity;
    final View launcher;
    final Decoration decoration;
    WebView web;
    JSONArray positions=new JSONArray();
    String geometry="[]", selected="", address="", credentialKey="", failureMessage="", message=I18n.t("Chargement de la carte HA…");
    boolean ready, pointerMode, destroyed, paused, refreshAfterLogin, probing, cinemaMotion;
    int probes;
    float pointerX,pointerY;
    long touchDown;
    boolean touching;
    final Runnable probe=this::probe;

    HaCardSurface(MainActivity a,View launcher){super(a);activity=a;this.launcher=launcher;setClipChildren(false);setFocusable(false);decoration=new Decoration();addView(decoration,new LayoutParams(-1,-1));}
    static boolean isCard(Map<String,Object> c){return c!=null&&"ha_card".equals(c.get("type"));}
    boolean hasReadyCard(){return ready&&web!=null;}
    void update(JSONArray next,String focus){
        if(destroyed)return;
        boolean cinema=CinemaMode.enabled(CardStore.prefs(activity));
        String json=next.toString();boolean changed=!json.equals(geometry)||cinema!=cinemaMotion;cinemaMotion=cinema;positions=next;geometry=json;selected=focus;
        if(next.length()>0&&PanelStore.enabled(activity))ensureWeb();
        else if(!PanelStore.enabled(activity)){if(web!=null)destroyWeb();if(next.length()>0)message=I18n.t("Reliez ce launcher à l’extension HA pour afficher cette carte");}
        if(changed&&web!=null)sendGeometry();
        if(next.length()==0&&pointerMode)exitPointer();
        if(web!=null)web.setAlpha(ready&&next.length()>0?1:0);
        decoration.invalidate();
    }
    void ensureWeb(){
        String next;
        try{next=DashboardUrl.resolve(CardStore.prefs(activity).getString("ha_url",""),"/home-assistant-tv-cards/"+PanelStore.identity(activity));}
        catch(Exception e){message=I18n.t("Configurez la connexion Home Assistant");return;}
        String credentials=HaWebAuth.fingerprint(TokenStore.get(activity))+CardStore.prefs(activity).getBoolean("ha_cards_native_auth",true);
        if(web!=null&&address.equals(next)&&credentialKey.equals(credentials))return;
        destroyWeb();address=next;credentialKey=credentials;ready=false;failureMessage="";message=I18n.t("Chargement de la carte HA…");
        try{web=new WebView(activity);}catch(Exception e){message=I18n.t("Android System WebView indisponible");return;}
        web.setBackgroundColor(Color.TRANSPARENT);web.setAlpha(0);web.setFocusable(false);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false);s.setMediaPlaybackRequiresUserGesture(true);s.setTextZoom(100);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return r.isForMainFrame()&&!DashboardUrl.sameOrigin(address,r.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return !DashboardUrl.sameOrigin(address,url);}
            @Override public void onPageStarted(WebView v,String url,Bitmap icon){ready=false;v.setAlpha(0);launcher.invalidate();}
            @Override public void onPageFinished(WebView v,String url){if(DashboardUrl.sameOrigin(address,url)){sendGeometry();startProbe();}}
            @Override public void doUpdateVisitedHistory(WebView v,String url,boolean reload){
                if(!ready||!DashboardUrl.sameOrigin(address,url))return;
                String path=android.net.Uri.parse(url).getPath();
                if(path==null||path.equals(android.net.Uri.parse(address).getPath()))return;
                // A HA navigation action leaves this surface for the existing full dashboard viewer.
                // Never leave a full opaque HA page covering the launcher's native navigation.
                boolean userNavigation=pointerMode;exitPointer();fail("Navigation HA · retour à la carte");
                if(userNavigation&&!path.startsWith("/auth/")){
                    try{DashboardUrl.resolve(CardStore.prefs(activity).getString("ha_url",""),path);refreshAfterLogin=true;activity.startActivity(new Intent(activity,HaDashboardActivity.class).putExtra("dashboard_path",path));}
                    catch(Exception ignored){message="Chemin HA non pris en charge dans le launcher";}
                }
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail(I18n.t("HA inaccessible · OK pour réessayer"));}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();fail(I18n.t("Certificat HA non valide · vérifiez l’adresse HTTPS"));}
        });
        boolean externalAuth=HaWebAuth.attach(activity,web,address,this::fail);
        addView(web,0,new LayoutParams(-1,-1));web.loadUrl(address+(externalAuth?"?external_auth=1":""));startProbe();
    }
    void fail(String value){ready=false;failureMessage=value;message=value;if(web!=null)web.setAlpha(0);launcher.invalidate();}
    void sendGeometry(){if(web==null)return;web.evaluateJavascript("window.hatvCards&&(window.hatvCards.layout("+geometry+"),window.hatvCards.motion&&window.hatvCards.motion("+cinemaMotion+"))",null);}
    void startProbe(){removeCallbacks(probe);probes=0;probing=true;postDelayed(probe,300);}
    void probe(){
        if(destroyed||paused||web==null){probing=false;return;}
        web.evaluateJavascript("JSON.stringify(window.hatvCards?window.hatvCards.status:{login:location.pathname.startsWith('/auth/')})",value->{
            if(destroyed||paused||web==null)return;
            try{
                Object decoded=new JSONTokener(value).nextValue();JSONObject state=new JSONObject(decoded instanceof String?(String)decoded:"{}");
                if(state.optBoolean("ready")&&state.has("revision")&&(!state.has("rendered")||state.optInt("rendered")>=positions.length())){
                    ready=true;probing=false;failureMessage="";message="";sendGeometry();web.setAlpha(positions.length()>0?1:0);launcher.invalidate();decoration.invalidate();return;
                }
                if(state.has("error"))message="Carte HA : "+state.optString("error");
                else if(state.optBoolean("login"))message=I18n.t("Connexion web HA nécessaire · OK pour se connecter");
                else message=failureMessage.isEmpty()?I18n.t("Chargement de la carte HA…"):failureMessage;
                sendGeometry();
            }catch(Exception ignored){}
            launcher.invalidate();
            if(++probes<40)postDelayed(probe,750);else {probing=false;if(message.startsWith("Chargement"))message=I18n.t("Carte HA non chargée · OK : connexion ou réessayer");launcher.invalidate();}
        });
    }
    void activate(Map<String,Object> card){
        if(!CardConfig.bool(card,"interactive",true))return;
        if(!ready){connectionMenu();return;}
        String id=CardConfig.str(card,"id","");JSONObject g=null;
        for(int i=0;i<positions.length();i++){JSONObject item=positions.optJSONObject(i);if(item!=null&&id.equals(item.optString("id"))){g=item;break;}}
        if(g==null)return;
        pointerX=(float)(g.optDouble("x")+g.optDouble("width")/2)*getWidth()/100f;
        pointerY=(float)(g.optDouble("y")+Math.min(g.optDouble("height")/2,5))*getHeight()/100f;
        pointerMode=true;web.setFocusableInTouchMode(true);web.requestFocus();decoration.invalidate();
        Toast.makeText(activity,I18n.t("Flèches : pointeur · OK : clic / maintenir · CH ± : défiler · Retour : launcher"),Toast.LENGTH_LONG).show();
    }
    void connectionMenu(){
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(I18n.t("Carte Home Assistant"))
            .setMessage(message+"\nLes cartes réutilisent le jeton du launcher si le WebView le permet. La connexion web séparée reste disponible en secours.")
            .setPositiveButton("Connexion HA",(d,w)->{refreshAfterLogin=true;activity.startActivity(new Intent(activity,HaDashboardActivity.class).putExtra("dashboard_path","/home-assistant-tv-cards/"+PanelStore.identity(activity)));})
            .setNeutralButton(I18n.t("Réessayer"),(d,w)->retry()).setNegativeButton(I18n.t("Retour"),null).create();
        dialog.setOnShowListener(d->TvFocus.dialog(dialog));dialog.show();
    }
    void retry(){failureMessage="";if(web!=null){ready=false;web.reload();startProbe();}else ensureWeb();}
    void resume(){paused=false;if(web!=null){web.onResume();if(refreshAfterLogin){refreshAfterLogin=false;retry();}else if(!ready)startProbe();}}
    void pause(){paused=true;exitPointer();removeCallbacks(probe);if(web!=null)web.onPause();}
    void destroy(){destroyed=true;removeCallbacks(probe);destroyWeb();}
    void destroyWeb(){exitPointer();if(web!=null){web.stopLoading();removeView(web);web.destroy();web=null;}ready=false;}
    void exitPointer(){if(touching)touch(MotionEvent.ACTION_CANCEL);pointerMode=false;touching=false;if(web!=null)web.setFocusable(false);decoration.invalidate();launcher.requestFocus();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(!pointerMode||web==null)return false;return web.dispatchTouchEvent(event);}
    boolean key(KeyEvent e){
        if(!pointerMode||web==null)return false;
        if(android.os.Build.VERSION.SDK_INT>=30){WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();if(insets!=null&&insets.isVisible(WindowInsets.Type.ime()))return false;}
        int key=e.getKeyCode();boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        if(key==KeyEvent.KEYCODE_BACK){if(!down){web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE));web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ESCAPE));exitPointer();}return true;}
        if(key==KeyEvent.KEYCODE_MENU){if(!down){exitPointer();connectionMenu();}return true;}
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(down&&e.getRepeatCount()==0){touchDown=SystemClock.uptimeMillis();touching=true;touch(MotionEvent.ACTION_DOWN);}else if(!down&&touching){touch(e.isCanceled()?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP);touching=false;}return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(down){float speed=CardStore.prefs(activity).getInt("dashboard_pointer_speed",24)*(e.getRepeatCount()>6?2:1);
                pointerX=Math.max(2,Math.min(getWidth()-2,pointerX+(key==KeyEvent.KEYCODE_DPAD_LEFT?-speed:key==KeyEvent.KEYCODE_DPAD_RIGHT?speed:0)));
                pointerY=Math.max(2,Math.min(getHeight()-2,pointerY+(key==KeyEvent.KEYCODE_DPAD_UP?-speed:key==KeyEvent.KEYCODE_DPAD_DOWN?speed:0)));
                if(touching)touch(MotionEvent.ACTION_MOVE);decoration.invalidate();}return true;
        }
        if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN||key==KeyEvent.KEYCODE_PAGE_UP||key==KeyEvent.KEYCODE_PAGE_DOWN){if(down&&e.getRepeatCount()==0&&!touching)swipe(key==KeyEvent.KEYCODE_CHANNEL_DOWN||key==KeyEvent.KEYCODE_PAGE_DOWN?1:-1);return true;}
        return false;
    }
    void touch(int action){if(web==null)return;MotionEvent e=MotionEvent.obtain(touchDown,SystemClock.uptimeMillis(),action,pointerX,pointerY,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);web.dispatchTouchEvent(e);e.recycle();}
    void swipe(int direction){touchDown=SystemClock.uptimeMillis();touching=true;touch(MotionEvent.ACTION_DOWN);final float initial=pointerY;for(int i=1;i<=10;i++){final int step=i;postDelayed(()->{if(!touching||!pointerMode||paused)return;pointerY=Math.max(2,Math.min(getHeight()-2,initial-direction*getHeight()*.25f*step/10));touch(step==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE);if(step==10){touching=false;pointerY=initial;}decoration.invalidate();},i*25);}}
    final class Decoration extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Decoration(){super(activity);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            if(!ready)return;
            if(!selected.isEmpty())for(int i=0;i<positions.length();i++){JSONObject g=positions.optJSONObject(i);if(g==null||!selected.equals(g.optString("id")))continue;
                float x=(float)g.optDouble("x")*getWidth()/100,y=(float)g.optDouble("y")*getHeight()/100,w=(float)g.optDouble("width")*getWidth()/100,h=(float)g.optDouble("height")*getHeight()/100;
                paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff101820);paint.setStrokeWidth(7);c.drawRoundRect(x+2,y+2,x+w-2,y+h-2,14,14,paint);paint.setColor(0xff79e4ff);paint.setStrokeWidth(3);c.drawRoundRect(x+2,y+2,x+w-2,y+h-2,14,14,paint);
            }
            if(pointerMode){paint.setStyle(Paint.Style.FILL);paint.setColor(0xbb000000);c.drawCircle(pointerX,pointerY,15,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(touching?0xffffd573:0xff79e4ff);c.drawCircle(pointerX,pointerY,12,paint);paint.setColor(Color.WHITE);c.drawLine(pointerX-6,pointerY,pointerX+6,pointerY,paint);c.drawLine(pointerX,pointerY-6,pointerX,pointerY+6,paint);}
        }
    }
}
