"""Destructive UI confirmations and connection guidance against a mock HA only."""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_assets import serve_localization

root=Path(__file__).resolve().parents[1]
schema=json.loads((root/"custom_components/home_assistant_tv/schema.json").read_text())
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ.get("CHROME_BIN"),headless=True,args=["--no-sandbox"])
    page=browser.new_page(viewport={"width":1280,"height":1000})
    errors=[];page.on("pageerror",lambda e:errors.append(str(e)))
    page.route("https://ha-test.invalid/**",lambda r:serve_localization(r) or r.fulfill(body="<!doctype html><html lang='fr'><body></body></html>",content_type="text/html"))
    page.goto("https://ha-test.invalid/")
    page.add_script_tag(content=(root/"custom_components/home_assistant_tv/panel.js").read_text(),type="module")
    page.wait_for_function("!!customElements.get('home-assistant-tv-panel')")
    page.evaluate("""schema=>{
      window.calls=[];window.profiles=[{id:'salon',title:'Salon',revision:1},{id:'unused',title:'Unused',revision:1}];
      window.devices=[{id:'fixture-tv',name:'Test TV',profile:'salon',approved:true,online:false,notification_entity:'notify.test_tv_notifications'}];
      window.notifyResult={response:{sent:1,skipped:0}};
      window.panel=document.createElement('home-assistant-tv-panel');panel.tab='Téléviseurs';document.body.append(panel);
      panel.hass={language:'fr',states:{},callWS:async m=>{
        calls.push(m);const action=m.type.split('/').at(-1);
        if(m.type==='call_service'){
          if(window.notifyError)throw Error('Fixture rejection');
          if(window.notifyPending)return await new Promise(resolve=>window.resolveNotify=resolve);
          return notifyResult;
        }
        if(action==='list')return {schema,profiles:structuredClone(profiles),devices:structuredClone(devices)};
        if(action==='get')return {...profiles.find(p=>p.id===m.profile),config:{schema:1,settings:{},cards:[]},yaml:'schema: 1'};
        if(action==='delete_profile'){
          if(devices.some(d=>d.profile===m.profile))throw Error('Ce profil est encore affecté à une TV. Réaffectez cette TV ou supprimez sa connexion avant de supprimer le profil.');
          profiles=profiles.filter(p=>p.id!==m.profile);return null;
        }
        if(action==='forget_device'){devices=devices.filter(d=>d.id!==m.device_id);return null;}
        throw Error('Unexpected call');
      }};
    }""",schema)
    page.get_by_role("heading",name="Connecter une TV en 3 étapes",exact=True).wait_for()
    assert page.get_by_text("Éditeur hors ligne",exact=True).count()==1
    assert page.get_by_text("Synchronisation en attente",exact=True).count()==1
    page.get_by_text("Envoyer une notification depuis HA",exact=True).click()
    assert page.locator("pre").inner_text().startswith("action: home_assistant_tv.notify")
    assert '"fixture-tv"' in page.locator("pre").inner_text()
    assert page.get_by_text("Notifications : non reliées ou désactivées",exact=True).count()==1
    page.set_viewport_size({"width":390,"height":844})
    assert page.evaluate("document.documentElement.scrollWidth<=window.innerWidth")
    page.set_viewport_size({"width":1280,"height":1000})
    for state,label in (("ready","Notifications : réception prête"),("paused","Notifications : veille ou affichage suspendu")):
        page.evaluate("state=>{panel.meta.devices[0].notifications=state;panel.render();}",state)
        assert page.get_by_text(label,exact=True).count()==1
        assert page.get_by_text("Éditeur hors ligne",exact=True).count()==1
    assert page.evaluate("calls.every(c=>c.type!=='call_service')"),"Opening instructions/status never sends"
    assert page.get_by_text("notify.test_tv_notifications",exact=True).count()==1
    page.evaluate("notifyPending=true;panel.config.settings.hero_subtitle='Unsaved';panel.dirty=true")
    page.get_by_role("button",name="Tester la notification",exact=True).click()
    assert page.get_by_role("button",name="Envoi du test…",exact=True).is_disabled()
    page.evaluate("panel.testNotification(panel.meta.devices[0]);panel.hass={...panel._hass,language:'en'}")
    assert page.get_by_role("button",name="Sending test…",exact=True).is_disabled()
    assert page.evaluate("calls.filter(c=>c.type==='call_service').length")==1
    page.evaluate("resolveNotify(notifyResult)")
    page.get_by_role("status").filter(has_text="Test sent to the TV").wait_for()
    assert page.evaluate("panel.dirty&&panel.config.settings.hero_subtitle==='Unsaved'")
    sent=page.evaluate("calls.find(c=>c.type==='call_service')")
    assert sent["service_data"]["device_ids"]==["fixture-tv"] and sent["return_response"] is True
    assert sent["service_data"]["duration"]==8 and sent["service_data"]["tag"]=="tv_assistant_test"
    page.evaluate("notifyPending=false;notifyResult={response:{sent:0,skipped:1}};panel.hass={...panel._hass,language:'fr'}")
    page.get_by_role("button",name="Tester la notification",exact=True).click()
    page.get_by_role("status").filter(has_text="Test non envoyé").wait_for()
    page.evaluate("notifyError=true")
    page.get_by_role("button",name="Tester la notification",exact=True).click()
    page.get_by_role("status").filter(has_text="Test impossible").wait_for()
    page.evaluate("notifyError=false;notifyResult={};")
    page.get_by_role("button",name="Tester la notification",exact=True).click()
    page.get_by_role("status").filter(has_text="Réponse non reconnue").wait_for()
    page.evaluate("window.originalTimeout=window.setTimeout;window.setTimeout=(fn,ms,...args)=>originalTimeout(fn,ms===12000?20:ms,...args);notifyPending=true")
    page.get_by_role("button",name="Tester la notification",exact=True).click()
    page.get_by_role("status").filter(has_text="HA n’a pas confirmé").wait_for()
    page.evaluate("window.setTimeout=originalTimeout;resolveNotify({response:{sent:1,skipped:0}});notifyPending=false")
    assert page.get_by_role("status").filter(has_text="HA n’a pas confirmé").count()==1,"Late result does not claim a confirmed send"
    page.evaluate("devices.push({id:'second-tv',name:'Second TV',profile:'salon',approved:true});panel.meta.devices=structuredClone(devices);panel.render();notifyResult={response:{sent:1,skipped:0}}")
    second=page.locator("section.card").filter(has=page.get_by_role("heading",name="Second TV",exact=True))
    second.get_by_role("button",name="Tester la notification",exact=True).click()
    second.get_by_role("status").filter(has_text="Test envoyé").wait_for()
    assert page.evaluate("calls.filter(c=>c.type==='call_service').at(-1).service_data.device_ids")==["second-tv"]
    page.evaluate("panel.meta.devices[1].approved=false;panel.render()")
    assert second.get_by_role("button",name="Tester la notification",exact=True).is_disabled()
    page.evaluate("panel.testNotification(panel.meta.devices[1])")
    assert page.evaluate("calls.filter(c=>c.type==='call_service').length")==6
    page.evaluate("devices.pop();panel.meta.devices=structuredClone(devices);panel.dirty=false;panel.render()")
    page.screenshot(path="/tmp/tv-assistant-notification-test.png",full_page=True)
    page.set_viewport_size({"width":390,"height":844})
    assert page.evaluate("document.documentElement.scrollWidth<=window.innerWidth")
    page.set_viewport_size({"width":1280,"height":1000})
    service_count=page.evaluate("calls.filter(c=>c.type==='call_service').length")
    page.once("dialog",lambda d:d.dismiss())
    page.get_by_role("button",name="Supprimer le profil",exact=True).click()
    assert page.evaluate("calls.every(c=>!c.type.endsWith('/delete_profile'))")
    page.once("dialog",lambda d:d.accept())
    page.get_by_role("button",name="Supprimer le profil",exact=True).click()
    page.get_by_role("status").filter(has_text="encore affecté").wait_for()
    assert page.evaluate("profiles.length")==2
    page.get_by_label("Profil",exact=True).select_option("unused")
    page.once("dialog",lambda d:d.accept())
    page.get_by_role("button",name="Supprimer le profil",exact=True).click()
    page.get_by_role("status").filter(has_text="Profil supprimé").wait_for()
    assert page.get_by_label("Profil",exact=True).input_value()=="salon"
    page.once("dialog",lambda d:d.dismiss())
    page.get_by_role("button",name="Supprimer la connexion",exact=True).click()
    assert page.evaluate("calls.every(c=>!c.type.endsWith('/forget_device'))")
    page.once("dialog",lambda d:d.accept())
    page.get_by_role("button",name="Supprimer la connexion",exact=True).click()
    page.get_by_role("heading",name="Aucune TV reliée pour le moment",exact=True).wait_for()
    assert page.evaluate("profiles.length")==1
    page.once("dialog",lambda d:d.accept())
    page.get_by_role("button",name="Supprimer le profil",exact=True).click()
    page.get_by_role("status").filter(has_text="Profil supprimé").wait_for()
    assert page.evaluate("profiles.length")==0
    assert page.get_by_role("button",name="Supprimer le profil",exact=True).count()==0
    assert page.get_by_label("Profil",exact=True).input_value()=="nouveau-profil"
    page.evaluate("panel.hass={...panel.hass,language:'en'}")
    page.get_by_role("heading",name="Connect a TV in 3 steps",exact=True).wait_for()
    assert page.evaluate("calls.filter(c=>c.type==='call_service').length")==service_count
    assert page.evaluate("calls.filter(c=>c.type==='call_service').every(c=>c.domain==='home_assistant_tv'&&c.service==='notify'&&c.service_data.device_ids.length===1)")
    page.screenshot(path="/tmp/tv-assistant-connection-guide.png",full_page=True)
    page.set_viewport_size({"width":390,"height":844})
    assert page.evaluate("document.documentElement.scrollWidth<=window.innerWidth")
    assert not errors,errors
    browser.close()
print("Connection UI: explicit test, pending, target isolation, sleep/error feedback, no automatic sends, deletion protection and English passed")
