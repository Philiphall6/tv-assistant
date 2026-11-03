"""Language changes do not modify profiles, YAML, names or card configuration."""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_assets import serve_localization

root = Path(__file__).resolve().parents[1]
schema = json.loads((root / "custom_components/home_assistant_tv/schema.json").read_text())
script = (root / "custom_components/home_assistant_tv/panel.js").read_text()
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("CHROME_BIN"), headless=True, args=["--no-sandbox"])
    page = browser.new_page(viewport={"width":1440,"height":1000}, locale="en-US")
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.route("https://ha-test.invalid/**", lambda route: serve_localization(route) or route.fulfill(body="<!doctype html><html lang='en'><body></body></html>", content_type="text/html"))
    page.goto("https://ha-test.invalid/")
    page.add_script_tag(content=script, type="module")
    page.wait_for_function("!!customElements.get('home-assistant-tv-panel')")
    page.evaluate("""schema => {
      window.panel=document.createElement('home-assistant-tv-panel');document.body.append(panel);
      window.calls=[];window.saved={schema:1,settings:{hero_subtitle:'Maison'},cards:[{id:'one',type:'text',name:'Paramètres',content:'Votre maison'}]};
      window.ha={locale:{language:'en-GB'},language:'fr',states:{},callWS:async message=>{
        calls.push(message);
        if(message.type.endsWith('/list'))return {schema,profiles:[{id:'test',title:'Maison',revision:1}],devices:[{id:'tv1',name:'Paramètres',profile:'test',online:false,apps:[{package:'org.example.tv',label:'Maison'}]}]};
        if(message.type.endsWith('/get'))return {title:'Maison',revision:1,config:structuredClone(saved),yaml:JSON.stringify(saved)};
        if(message.type.endsWith('/validate'))return {config:message.config,yaml:JSON.stringify(message.config)};
        throw Error('Unexpected call: '+message.type);
      }};panel.hass=ha;panel.previewAuto=false;
    }""", schema)
    page.get_by_role("heading", name="For you elements", exact=True).wait_for()
    assert page.get_by_label("Profile", exact=True).locator("option:checked").text_content() == "Maison"
    page.get_by_role("button", name="Appearance", exact=True).click()
    page.get_by_label("Welcome text", exact=True).fill("Votre maison")
    page.evaluate("window.draft=JSON.stringify(panel.config);panel.hass={...ha,locale:{language:'fr-CA'}}")
    page.get_by_role("button", name="Apparence", exact=True).wait_for()
    assert page.get_by_label("Texte d’accueil", exact=True).input_value() == "Votre maison"
    assert page.evaluate("JSON.stringify(panel.config)===draft&&panel.dirty")
    # HA language takes precedence over browser language; unsupported languages use English.
    page.evaluate("panel.hass={...ha,locale:{language:'de-DE'}}")
    page.get_by_role("button", name="Appearance", exact=True).wait_for()
    page.get_by_role("button", name="Cards", exact=True).click()
    assert page.locator(".item .grow").first.text_content() == "Paramètres"
    page.get_by_role("button", name="TVs", exact=True).click()
    assert page.get_by_role("heading", name="Paramètres", exact=True).count() == 1
    page.get_by_role("button", name="Wallpaper", exact=True).click()
    page.get_by_role("button", name="Solid color: Sage green", exact=True).click()
    assert page.get_by_label("Wallpaper", exact=True).input_value() == "5"
    page.get_by_role("button", name="Screen", exact=True).click()
    page.get_by_label("Add an app", exact=True).select_option("org.example.tv")
    assert page.evaluate("panel.config.cards.at(-1).name") == "Maison"
    page.evaluate("panel.hass={...ha,locale:{language:'fr'}}")
    assert page.evaluate("panel.config.cards.at(-1).name") == "Maison"
    assert page.evaluate("calls.every(c=>!c.type.endsWith('/save')&&!c.type.includes('call_service'))")
    assert not errors, errors
    page.evaluate("panel.hass={...ha,locale:{language:'en'}}")
    page.screenshot(path="/tmp/tv-assistant-editor-en.png", full_page=True)
    page.set_viewport_size({"width":390,"height":844})
    assert page.evaluate("document.documentElement.scrollWidth<=window.innerWidth")
    page.screenshot(path="/tmp/tv-assistant-editor-en-mobile.png", full_page=True)
    browser.close()
print("PASS: HA language priority, regional locales, fallback, live switching, draft preservation and unmodified user content")
