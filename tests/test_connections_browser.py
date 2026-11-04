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
      window.devices=[{id:'fixture-tv',name:'Test TV',profile:'salon',approved:true,online:false}];
      window.panel=document.createElement('home-assistant-tv-panel');panel.tab='Téléviseurs';document.body.append(panel);
      panel.hass={language:'fr',states:{},callWS:async m=>{
        calls.push(m);const action=m.type.split('/').at(-1);
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
    assert page.evaluate("calls.every(c=>!c.type.includes('call_service'))")
    page.screenshot(path="/tmp/tv-assistant-connection-guide.png",full_page=True)
    page.set_viewport_size({"width":390,"height":844})
    assert page.evaluate("document.documentElement.scrollWidth<=window.innerWidth")
    assert not errors,errors
    browser.close()
print("Connection UI: cancellation, assigned-profile protection, last-profile deletion and English guidance passed")
