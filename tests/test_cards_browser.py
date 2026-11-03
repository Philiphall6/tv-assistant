"""Persistent HA surface with a deterministic frontend mock; no device/service calls."""
from pathlib import Path
import os
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
script=(root/"custom_components/home_assistant_tv/cards.js").read_text()
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ.get("CHROME_BIN"),headless=True,args=["--no-sandbox"])
    page=browser.new_page(viewport={"width":960,"height":540})
    errors=[];page.on("pageerror",lambda e:errors.append(str(e)))
    requests=[]
    def serve(route):
        if route.request.url.endswith("/installed.js"):
            requests.append(route.request.url)
            route.fulfill(content_type="application/javascript",body="""
                window.resourceLoads=(window.resourceLoads||0)+1;
                customElements.define('test-card',class extends HTMLElement{
                  constructor(){super();this.attachShadow({mode:'open'});this.shadowRoot.innerHTML='<style>:host{display:block;width:178px;max-width:calc((100vw - 32px)/2);height:72px}ha-card{display:block}button{font-size:14px}</style><ha-card style="height:72px;width:178px;border:1px solid blue;border-radius:24px"><button>État</button><svg width="36" height="36"></svg></ha-card>';this.shadowRoot.querySelector('button').onclick=()=>this.dispatchEvent(new CustomEvent('hass-more-info',{detail:{entityId:'sensor.test'},bubbles:true,composed:true}));}
                  set hass(h){this.shadowRoot.querySelector('button').textContent='État : '+h.states['sensor.test'].state;}
                });
            """)
        else:route.fulfill(body="<!doctype html><html><body style='background:red'></body></html>",content_type="text/html")
    page.route("https://ha-test.invalid/**",serve)
    page.goto("https://ha-test.invalid/home-assistant-tv-cards/tv1")
    page.evaluate("""() => {
      window.loads=0;window.created=0;window.subscriptions=0;window.unsubscribed=0;window.moreInfo=0;
      const resolver=document.createElement('partial-panel-resolver');
      resolver.routerOptions={routes:{lovelace:{tag:'ha-panel-lovelace',load:async()=>{loads++;window.loadCardHelpers=async()=>({createCardElement:c=>{created++;const el=document.createElement('test-card');el.config=c;return el;}});}}}};document.body.append(resolver);
      window.hass={states:{'sensor.test':{state:'21'}},callWS:async m=>{if(m.type!=='lovelace/resources')throw Error('Unexpected call');return [{type:'module',url:'/installed.js'}];},connection:{subscribeMessage:async(fn,msg)=>{subscriptions++;window.receive=fn;return ()=>{unsubscribed++;};}}};
      document.body.addEventListener('hass-more-info',()=>moreInfo++);
    }""")
    page.add_script_tag(content=script,type="module")
    page.wait_for_function("!!customElements.get('home-assistant-tv-cards')")
    # Versioned modules may both arrive in a persistent WebView after an update.
    page.add_script_tag(content=script,type="module")
    page.evaluate("""() => {window.surface=document.createElement('home-assistant-tv-cards');surface.hass=hass;surface.route={path:'/tv1'};document.body.append(surface);receive({revision:1,profile:'salon',cards:[{id:'one',type:'ha_card',lovelace:{type:'custom:test-card'}}]});}""")
    assert page.evaluate("loads")==0
    assert requests==[]
    page.evaluate("hatvCards.layout([{id:'one',x:7,y:4,width:30,height:20}])")
    page.get_by_role("button",name="État : 21",exact=True).wait_for()
    assert page.evaluate("loads")==1
    assert page.evaluate("created")==1
    assert len(requests)==1
    assert page.evaluate("hatvCards.status.rendered")==1
    box=page.locator(".slot").bounding_box()
    assert abs(box["x"]-960*.07)<1 and abs(box["width"]-960*.30)<1
    page.evaluate("surface.hass={...hass,states:{'sensor.test':{state:'22'}}};hatvCards.layout([{id:'one',x:50,y:20,width:20,height:40}])")
    page.get_by_role("button",name="État : 22",exact=True).wait_for()
    assert page.evaluate("created")==1
    page.get_by_role("button",name="État : 22",exact=True).click()
    assert page.evaluate("moreInfo")==1
    # Opt-in fills fixed-size HACS/card-mod hosts and ha-card, without scaling text/SVG.
    page.evaluate("window.originalCard=surface.shadowRoot.querySelector('test-card');window.originalConfig=JSON.stringify(originalCard.config);receive({revision:2,profile:'salon',cards:[{id:'one',ha_sizing:'zone',lovelace:{type:'custom:test-card'}}]})")
    page.wait_for_function("!!originalCard.shadowRoot.querySelector('[data-hatv-zone-sizing]')")
    for width,height in ((30,20),(60,40),(12,10),(90,70)):
        page.evaluate("([width,height])=>hatvCards.layout([{id:'one',x:5,y:5,width,height}])",[width,height])
        page.wait_for_function("""() => {const s=surface.shadowRoot.querySelector('.slot').getBoundingClientRect(),c=originalCard.shadowRoot.querySelector('ha-card').getBoundingClientRect();return Math.abs(s.width-c.width)<1&&Math.abs(s.height-c.height)<1;}""")
        assert page.evaluate("surface.shadowRoot.querySelector('test-card')===originalCard")
        assert page.evaluate("JSON.stringify(originalCard.config)===originalConfig")
        assert page.evaluate("getComputedStyle(originalCard.shadowRoot.querySelector('button')).fontSize")=="14px"
        svg=page.evaluate("(()=>{const r=originalCard.shadowRoot.querySelector('svg').getBoundingClientRect();return {width:r.width,height:r.height,scale:Number(surface.shadowRoot.querySelector('.card-content').style.getPropertyValue('--hatv-content-scale'))};})()")
        assert abs(svg["width"]-36*svg["scale"])<.05 and abs(svg["height"]-36*svg["scale"])<.05,svg
    page.set_viewport_size({"width":1920,"height":1080})
    page.wait_for_function("Math.abs(originalCard.getBoundingClientRect().width-1920*.9)<1")
    page.get_by_role("button",name="État : 22",exact=True).click()
    assert page.evaluate("moreInfo")==2
    page.evaluate("receive({revision:2,profile:'salon',cards:[{id:'one',ha_sizing:'yaml',lovelace:{type:'custom:test-card'}}]})")
    page.wait_for_function("originalCard.getBoundingClientRect().width===178&&originalCard.shadowRoot.querySelector('ha-card').getBoundingClientRect().height===74")
    assert page.evaluate("originalCard.shadowRoot.querySelector('[data-hatv-zone-sizing]')===null")
    assert page.evaluate("created")==1
    page.set_viewport_size({"width":960,"height":540})
    page.evaluate("receive({revision:2,profile:'salon',cards:[{id:'one',interactive:false,lovelace:{type:'custom:test-card'}}]})")
    page.wait_for_function("surface.shadowRoot.querySelector('.slot').classList.contains('readonly')")
    assert page.evaluate("created")==1
    assert page.evaluate("getComputedStyle(surface.shadowRoot.querySelector('.slot')).pointerEvents")=="none"
    page.evaluate("receive({revision:3,profile:'salon',cards:[{id:'one',lovelace:{type:'custom:test-card',name:'Changed'}}]})")
    page.wait_for_function("created===2")
    page.evaluate("hatvCards.layout([])")
    assert page.locator(".slot").count()==0
    assert page.evaluate("hatvCards.status.rendered")==0
    page.evaluate("hatvCards.layout([{id:'one',x:7,y:4,width:30,height:20}])")
    page.get_by_role("button",name="État : 22",exact=True).wait_for()
    assert len(requests)==1 and page.evaluate("subscriptions")==1
    page.evaluate("receive({revision:4,profile:'salon',cards:[{id:'one',ha_sizing:'zone',lovelace:{type:'custom:test-card',name:'Changed'}}]});window.detachedCard=surface.shadowRoot.querySelector('test-card')")
    page.wait_for_function("!!detachedCard.shadowRoot.querySelector('[data-hatv-zone-sizing]')")
    page.evaluate("surface.remove()")
    page.wait_for_function("unsubscribed===1")
    assert page.evaluate("document.body.style.background")=="red"
    assert page.evaluate("detachedCard.shadowRoot.querySelector('[data-hatv-zone-sizing]')===null")
    assert not errors,errors
    print("PASS: on-demand resources, live states, persistent DOM, placement, HA events, read-only, cleanup")
    browser.close()
