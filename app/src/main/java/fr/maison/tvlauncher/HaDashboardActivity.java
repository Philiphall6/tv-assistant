package fr.maison.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.webkit.*;
import android.widget.*;

/** Optional real HA frontend. No periodic reload, native bridge, injected token or TLS bypass. */
public class HaDashboardActivity extends Activity {
    WebView web;Pointer pointer;FrameLayout surface;String origin;TextView status;int speed;boolean destroyed,loadFailed;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        SharedPreferences prefs=CardStore.prefs(this);String address;
        String requested=getIntent().getStringExtra("dashboard_path");try{address=DashboardUrl.resolve(prefs.getString("ha_url",""),requested==null?prefs.getString("dashboard_web_path","/lovelace/0"):requested);}catch(Exception e){Toast.makeText(this,"Configurez l’adresse HA et le chemin du tableau de bord.",Toast.LENGTH_LONG).show();finish();return;}
        origin=address;speed=prefs.getInt("dashboard_pointer_speed",24);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff101820);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(13);status.setPadding(16,10,16,10);status.setText("Chargement HA… · Flèches : pointeur · OK : clic · CH ± : défiler · Menu : options · Retour : Maison");root.addView(status);
        surface=new FrameLayout(this);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        try{web=new WebView(this);}catch(Exception e){status.setText("Android System WebView est indisponible. Mettez-le à jour depuis le Play Store. Retour : Maison");return;}
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setMediaPlaybackRequiresUserGesture(true);settings.setTextZoom(prefs.getInt("dashboard_web_text_zoom",100));settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return request.isForMainFrame()&&blocked(request.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return blocked(url);}
            boolean blocked(String url){if(DashboardUrl.sameOrigin(origin,url))return false;Toast.makeText(HaDashboardActivity.this,"Seuls les liens de votre serveur HA sont ouverts dans cette vue.",Toast.LENGTH_SHORT).show();return true;}
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){loadFailed=false;}
            @Override public void onPageFinished(WebView view,String url){if(loadFailed)return;status.setText("HA · Connexion web habituelle si demandée · Flèches : pointeur · OK : clic · CH ± : défiler · Menu : options · Retour : Maison");CookieManager.getInstance().flush();}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){loadFailed=true;status.setText("HA inaccessible. Vérifiez le réseau et l’adresse. Menu : réessayer · Retour : Maison");}}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();loadFailed=true;status.setText("Certificat HA non valide pour cette adresse. Utilisez son nom HTTPS valide. Retour : Maison");}
        });
        surface.addView(web,new FrameLayout.LayoutParams(-1,-1));pointer=new Pointer();surface.addView(pointer,new FrameLayout.LayoutParams(-1,-1));pointer.setClickable(false);pointer.setFocusable(false);
        web.loadUrl(address);web.requestFocus();
    }
    boolean keyboardVisible(){if(android.os.Build.VERSION.SDK_INT>=30){WindowInsets insets=getWindow().getDecorView().getRootWindowInsets();return insets!=null&&insets.isVisible(WindowInsets.Type.ime());}Rect r=new Rect();getWindow().getDecorView().getWindowVisibleDisplayFrame(r);return getWindow().getDecorView().getHeight()-r.bottom>getResources().getDisplayMetrics().density*150;}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int k=e.getKeyCode();if(keyboardVisible())return super.dispatchKeyEvent(e);
        if(k==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)finish();return true;}
        if(k==KeyEvent.KEYCODE_MENU){if(e.getAction()==KeyEvent.ACTION_UP)menu();return true;}
        if(web==null||pointer==null)return super.dispatchKeyEvent(e);
        if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN||k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){if(e.getAction()==KeyEvent.ACTION_DOWN){float step=speed*(e.getRepeatCount()>6?2:1);pointer.move(k==KeyEvent.KEYCODE_DPAD_LEFT?-step:k==KeyEvent.KEYCODE_DPAD_RIGHT?step:0,k==KeyEvent.KEYCODE_DPAD_UP?-step:k==KeyEvent.KEYCODE_DPAD_DOWN?step:0);}return true;}
        if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())tap();return true;}
        if(k==KeyEvent.KEYCODE_CHANNEL_UP||k==KeyEvent.KEYCODE_PAGE_UP||k==KeyEvent.KEYCODE_CHANNEL_DOWN||k==KeyEvent.KEYCODE_PAGE_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)swipe(k==KeyEvent.KEYCODE_CHANNEL_DOWN||k==KeyEvent.KEYCODE_PAGE_DOWN?1:-1);return true;}
        return super.dispatchKeyEvent(e);
    }
    void event(long down,long when,int action,float x,float y){if(destroyed||web==null)return;MotionEvent event=MotionEvent.obtain(down,when,action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);web.dispatchTouchEvent(event);event.recycle();}
    void tap(){long now=SystemClock.uptimeMillis();event(now,now,MotionEvent.ACTION_DOWN,pointer.x,pointer.y);event(now,now+60,MotionEvent.ACTION_UP,pointer.x,pointer.y);}
    void swipe(int direction){float x=pointer.x,y=web.getHeight()*(direction>0?.75f:.25f),distance=web.getHeight()*.5f*direction;long down=SystemClock.uptimeMillis();event(down,down,MotionEvent.ACTION_DOWN,x,y);for(int i=1;i<=10;i++){final int step=i;web.postDelayed(()->event(down,SystemClock.uptimeMillis(),step==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,y-distance*step/10),i*25);}}
    void menu(){String[] options={"Retour aux cartes Maison","Page précédente","Recharger le tableau de bord"};AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Tableau de bord HA").setItems(options,(v,i)->{if(i==0)finish();else if(web!=null){if(i==1&&web.canGoBack())web.goBack();else if(i==2)web.reload();}}).setNegativeButton("Fermer",null).create();dialog.setOnShowListener(v->TvFocus.dialog(dialog));dialog.show();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;if(web!=null){web.stopLoading();surface.removeView(web);web.destroy();web=null;}super.onDestroy();}
    final class Pointer extends View {
        float x,y;Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Pointer(){super(HaDashboardActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){x=w/2f;y=h/2f;}
        void move(float dx,float dy){x=Math.max(8,Math.min(getWidth()-8,x+dx));y=Math.max(8,Math.min(getHeight()-8,y+dy));invalidate();}
        @Override protected void onDraw(Canvas c){paint.setStyle(Paint.Style.FILL);paint.setColor(0xaa000000);c.drawCircle(x,y,15,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(0xff79e4ff);c.drawCircle(x,y,12,paint);paint.setColor(Color.WHITE);c.drawLine(x-6,y,x+6,y,paint);c.drawLine(x,y-6,x,y+6,paint);}
    }
}
