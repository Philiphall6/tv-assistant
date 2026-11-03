package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Fake credentials on intercepted .invalid pages only. Never touches the user's HA/session. */
final class HaWebAuthTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static void run(Instrumentation test)throws Exception{
        String address="https://hatv-auth.invalid/home-assistant-tv-cards/test";
        check(HaWebAuth.authorized(address,"https://hatv-auth.invalid",address+"?external_auth=1",true),"main origin accepted");
        check(!HaWebAuth.authorized(address,"https://hatv-auth.invalid",address,false),"same-origin iframe denied");
        check(!HaWebAuth.authorized(address,"https://evil.invalid",address,true),"foreign origin denied");
        check(!HaWebAuth.authorized(address,"https://hatv-auth.invalid", "https://hatv-auth.invalid/local/untrusted.html",true),"other page on same server denied");
        check(!HaWebAuth.authorized(address,"http://hatv-auth.invalid",address,true),"downgrade denied");
        check(!HaWebAuth.authorized(address,"https://hatv-auth.invalid:8123",address,true),"other port denied");
        for(boolean admin:new boolean[]{false,true}){
            PanelRuntimeTest.Memory memory=new PanelRuntimeTest.Memory();String fake="test-token-not-real";
            TokenStore.save(memory.prefs,"https://hatv-auth.invalid",fake);
            memory.values.put("ha_authenticated_admin",admin);memory.values.put("ha_authenticated_token_fingerprint",HaWebAuth.fingerprint(fake));
            WebView[] view=new WebView[1];CountDownLatch loaded=new CountDownLatch(1);AtomicReference<String> failure=new AtomicReference<>("");
            test.runOnMainSync(()->{
                view[0]=new WebView(test.getTargetContext());view[0].getSettings().setJavaScriptEnabled(true);
                check(HaWebAuth.attach(memory.context(),view[0],address,failure::set),"secure V2 bridge supported");
                view[0].setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                        String html="<html><body><script>window.answers=[];window.externalAuthSetToken=(ok,data)=>answers.push({ok,valid:!!data&&data.access_token==='test-token-not-real'});</script></body></html>";
                        return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                    }
                    @Override public void onPageFinished(WebView v,String url){loaded.countDown();}
                });view[0].loadUrl(address);
            });
            try{
                check(loaded.await(10,TimeUnit.SECONDS),"fake page loaded");
                evaluate(test,view[0],"externalAppV2.postMessage(JSON.stringify({type:'getExternalAuth',payload:{callback:'externalAuthSetToken'}}))");
                String response="";for(int i=0;i<20;i++){response=evaluate(test,view[0],"JSON.stringify(answers)");if(!response.equals("\"[]\""))break;Thread.sleep(100);}
                check(response.contains(admin?"\\\"ok\\\":false":"\\\"valid\\\":true"),"non-admin credential only");
                String before=response;
                evaluate(test,view[0],"externalAppV2.postMessage(JSON.stringify({type:'getExternalAuth',payload:{callback:'alert'}}))");
                Thread.sleep(200);check(evaluate(test,view[0],"JSON.stringify(answers)").equals(before),"callback injection denied");
                check(admin?!failure.get().isEmpty():failure.get().isEmpty(),"admin refusal explained");
            }finally{test.runOnMainSync(()->{view[0].stopLoading();view[0].destroy();});}
        }
    }
    static String evaluate(Instrumentation test,WebView view,String js)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<String> out=new AtomicReference<>("");test.runOnMainSync(()->view.evaluateJavascript(js,value->{out.set(value);done.countDown();}));if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("WebView test callback timeout");return out.get();}
}
