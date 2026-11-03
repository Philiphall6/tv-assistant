package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Live rendering diagnostic. Optional HTTP asset-cache refresh; no credentials/settings changed.
 * Never taps a card or calls a HA service. */
public final class HaCardInstrumentation extends Instrumentation {
    boolean refreshAssets;
    @Override public void onCreate(Bundle args){super.onCreate(args);refreshAssets=args!=null&&"true".equals(args.getString("refresh_assets"));start();}
    @Override public void onStart(){Bundle result=new Bundle();
        try{
            if(refreshAssets)runOnMainSync(()->{android.webkit.WebView temporary=new android.webkit.WebView(getTargetContext());temporary.clearCache(true);temporary.destroy();});
            MainActivity main=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<60;i++){
                AtomicReference<Boolean> ready=new AtomicReference<>(false);
                runOnMainSync(()->ready.set(main.haCards.ready));if(ready.get())break;SystemClock.sleep(500);
            }
            AtomicReference<String> state=new AtomicReference<>();
            runOnMainSync(()->state.set("ready="+main.haCards.ready+"; message="+main.haCards.message+"; visibleCards="+main.haCards.positions.length()));
            CountDownLatch done=new CountDownLatch(1);AtomicReference<String> dom=new AtomicReference<>("no WebView");
            runOnMainSync(()->{
                if(main.haCards.web==null){done.countDown();return;}
                // Only structure and visible card text, never auth/localStorage/network headers.
                String js="JSON.stringify((()=>{const deep=(root,tag)=>{const e=root.querySelector(tag);if(e)return e;for(const n of root.querySelectorAll('*'))if(n.shadowRoot){const r=deep(n.shadowRoot,tag);if(r)return r;}};const s=deep(document,'home-assistant-tv-cards'),b=s&&deep(s.shadowRoot,'button-card');return {path:location.pathname,renderer:!!s,helpers:!!window.loadCardHelpers,buttonDefined:!!customElements.get('button-card'),slots:s?s.shadowRoot.querySelectorAll('.slot').length:0,button:!!b,text:b?b.shadowRoot?.textContent?.slice(-1200):'',status:window.hatvCards?.status};})())";
                main.haCards.web.evaluateJavascript(js,value->{dom.set(value);done.countDown();});
            });
            done.await(5,TimeUnit.SECONDS);
            // Geometry only: verify the real PAC box against its slot without clicking it.
            CountDownLatch measured=new CountDownLatch(1);AtomicReference<String> sizes=new AtomicReference<>("no WebView");
            runOnMainSync(()->{
                if(main.haCards.web==null){measured.countDown();return;}
                String js="JSON.stringify((()=>{const deep=(r,t)=>{const e=r.querySelector(t);if(e)return e;for(const n of r.querySelectorAll('*'))if(n.shadowRoot){const v=deep(n.shadowRoot,t);if(v)return v;}};const s=deep(document,'home-assistant-tv-cards');return s?[...s.shadowRoot.querySelectorAll('.slot')].map(slot=>{const card=deep(slot,'ha-card'),box=slot.getBoundingClientRect(),r=card?.getBoundingClientRect();return {zone:slot.classList.contains('zone'),slot:{width:box.width,height:box.height},card:r?{width:r.width,height:r.height}:null};}):[];})())";
                main.haCards.web.evaluateJavascript(js,value->{sizes.set(value);measured.countDown();});
            });
            measured.await(5,TimeUnit.SECONDS);
            result.putString("stream",state.get()+"\n"+dom.get()+"\n"+sizes.get()+"\n");finish(-1,result);
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(1,result);}
    }
}
