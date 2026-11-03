"""Real Chromium panel exercise against a deterministic mock HA, no real service calls."""
import json
from pathlib import Path
import os
from playwright.sync_api import sync_playwright
from browser_assets import serve_localization

root=Path(__file__).resolve().parents[1]
schema=json.loads((root/"custom_components/home_assistant_tv/schema.json").read_text())
script=(root/"custom_components/home_assistant_tv/panel.js").read_text()
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ.get("CHROME_BIN"),headless=True,args=["--no-sandbox"])
    page=browser.new_page(viewport={"width":1440,"height":1050})
    errors=[];page.on("pageerror",lambda e:errors.append(str(e)))
    page.route("https://ha-test.invalid/**",lambda route:serve_localization(route) or route.fulfill(body="<!doctype html><html lang='fr'><body></body></html>",content_type="text/html"))
    page.goto("https://ha-test.invalid/")
    page.add_script_tag(content=script,type="module")
    page.wait_for_function("() => !!customElements.get('home-assistant-tv-panel')")
    page.evaluate("""schema=>{
      window.calls=[];window.revision=1;window.saved={schema:1,settings:{hero_subtitle:'Le salon',home_row_title:'Votre maison'},cards:[{id:'one',type:'text',name:'Température',entity:'sensor.test'}]};
      const image=document.createElement('canvas');image.width=16;image.height=9;window.testJpeg=image.toDataURL('image/jpeg').split(',')[1];
      const panel=document.createElement('home-assistant-tv-panel');document.body.append(panel);
      panel.hass={states:{'sensor.test':{state:'21',attributes:{friendly_name:'Température'}}},callWS:async m=>{calls.push(m);
        if(m.type.endsWith('/list'))return {schema,profiles:[{id:'salon',title:'Salon',revision}],devices:[{id:'tv1',name:'TV salon',profile:'salon',online:true,applied_revision:revision,reported_config:saved,apps:[{package:'org.example.tv',label:'Application test'}]}]};
        if(m.type.endsWith('/get'))return {title:'Salon',revision,config:structuredClone(saved),yaml:JSON.stringify(saved)};
        if(m.type.endsWith('/validate')){const config=m.config||JSON.parse(m.yaml);if(config.settings?.ha_token)throw Error('Réglage interdit');return {config,yaml:JSON.stringify(config)};}
        if(m.type.endsWith('/save')){if(m.revision!==revision)throw Error('Conflit');saved=structuredClone(m.config);return {config:saved,revision:++revision,yaml:JSON.stringify(saved)};}
        if(m.type.endsWith('/preview')){if(window.offline)throw Error('TV hors ligne');return {device_id:m.device_id,image:testJpeg,captured_at:new Date().toISOString(),profile:'salon',revision};}
        if(m.type.endsWith('/assign'))return null;throw Error('Unexpected command');
      }};
    }""",schema)
    try:
        page.get_by_role("heading",name="Éléments de Pour vous",exact=True).wait_for(timeout=5000)
    except Exception:
        print("Browser errors:",errors)
        print(page.evaluate("document.querySelector('home-assistant-tv-panel').shadowRoot.textContent.slice(-4000)"))
        raise
    page.get_by_role("button",name="Apparence",exact=True).click()
    page.get_by_label("Texte d’accueil",exact=True).fill("Mon salon personnalisé")
    page.get_by_label("Réglages à modifier",exact=True).select_option("Icônes des applications")
    assert page.get_by_label("Forme des icônes",exact=True).locator("option").count()==6
    for value,label in ((3,"Cookie à 4 côtés"),(4,"Cookie à 7 côtés"),(5,"Arche"),(0,"Ronde")):
        button=page.get_by_role("button",name="Choisir : "+label,exact=True)
        button.click()
        assert button.get_attribute("aria-pressed")=="true"
        assert page.get_by_label("Forme des icônes",exact=True).input_value()==str(value)
        assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.settings.app_icon_shape")==value
        assert page.locator(".icon-shapes button[aria-pressed=true]").count()==1
    page.get_by_label("Forme des icônes",exact=True).select_option("4")
    assert page.get_by_role("button",name="Choisir : Cookie à 7 côtés",exact=True).get_attribute("aria-pressed")=="true"
    page.screenshot(path="/tmp/ha-tv-icon-shapes-editor.png",full_page=True)
    page.get_by_role("button",name="Fond d’écran",exact=True).click()
    assert page.get_by_role("group",name="Couleurs de fond d’écran",exact=True).get_by_role("button").count()==8
    page.get_by_role("button",name="Fond uni : Bleu HA",exact=True).click()
    assert page.get_by_label("Fond d’écran",exact=True).input_value()=="5"
    assert page.get_by_label("Couleur du fond uni",exact=True).input_value()=="#03a9f4"
    page.get_by_label("Couleur du fond uni",exact=True).fill("#123456")
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.settings.wallpaper_color")=="#123456"
    assert page.evaluate("revision")==1, "colors stay a draft until explicit save"
    page.get_by_label("Fond d’écran",exact=True).select_option("4")
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.settings.wallpaper_color")=="#123456"
    page.get_by_role("button",name="Fond uni : Vert sauge",exact=True).click()
    assert page.get_by_label("Fond d’écran",exact=True).input_value()=="5"
    assert page.get_by_role("button",name="Fond uni : Vert sauge",exact=True).get_attribute("aria-pressed")=="true"
    page.screenshot(path="/tmp/ha-tv-wallpaper-colors.png",full_page=True)
    page.get_by_role("button",name="Envoyer aux TV du profil",exact=True).click()
    page.get_by_role("status").filter(has_text="version 2").wait_for()
    assert page.evaluate("saved.settings.hero_subtitle")=="Mon salon personnalisé"
    assert page.evaluate("saved.settings.app_icon_shape")==4
    assert page.evaluate("saved.settings.wallpaper_mode")==5
    assert page.evaluate("saved.settings.wallpaper_color")=="#3D5A50"
    page.get_by_role("button",name="Rangées",exact=True).click()
    try:page.get_by_label("Entité du groupe",exact=True).wait_for(timeout=5000)
    except Exception:
        print("Module errors:",errors)
        raise
    assert page.get_by_label("Entité du groupe",exact=True).get_attribute("list")=="hatv-entities"
    page.get_by_label("Entités dans l’ordre (une par ligne)",exact=True).fill("light.test\nsensor.test")
    page.get_by_role("button",name="Barre",exact=True).click()
    page.get_by_role("button",name="Position",exact=True).click()
    page.get_by_label("Position de la barre fixe",exact=True).select_option("2")
    page.get_by_label("Largeur en haut / en bas (%)",exact=True).fill("65")
    page.get_by_label("Espacement entre les onglets",exact=True).fill("24")
    page.get_by_role("button",name="Fond",exact=True).click()
    page.get_by_label("Fond coloré de la barre (désactivé = transparent)",exact=True).uncheck()
    page.get_by_alt_text("Capture réelle du launcher").wait_for(timeout=5000)
    assert page.locator(".studio .preview-side").count()==1
    page.get_by_role("button",name="Apparence",exact=True).click()
    page.get_by_label("Réglages à modifier",exact=True).select_option("Couleurs")
    page.get_by_label("Couleur du texte",exact=True).fill("#11aabb")
    for tab in ("Rangées","Apparence","Favoris","Fond d’écran","Écran"):
        page.get_by_role("button",name=tab,exact=True).click()
        assert page.get_by_role("button",name="Envoyer aux TV du profil",exact=True).is_enabled()
    page.get_by_role("button",name="Cartes",exact=True).click()
    page.get_by_role("button",name="Ajouter une carte",exact=True).click()
    page.get_by_label("Titre",exact=True).fill("Lumière")
    page.get_by_label("Entité",exact=True).fill("light.test")
    page.get_by_label("Action au clic",exact=True).select_option("toggle")
    page.get_by_role("button",name="Envoyer aux TV du profil",exact=True).click()
    page.get_by_role("status").filter(has_text="version 3").wait_for()
    assert page.evaluate("saved.cards[1].tap_action.action")=="toggle"
    page.get_by_role("button",name="Écran",exact=True).click()
    page.get_by_label("Réglages à modifier",exact=True).select_option("Éléments libres")
    page.get_by_role("button",name="+ Affichage seul",exact=True).click()
    page.get_by_label("Nom de l’élément",exact=True).fill("Mon affichage")
    page.get_by_label("Position X (%)",exact=True).fill("20")
    page.get_by_label("Largeur (%)",exact=True).fill("40")
    page.get_by_label("Opacité de l’élément (%)",exact=True).fill("0")
    frame=page.get_by_role("button",name="Placer Mon affichage",exact=True)
    frame.wait_for()
    frame.focus();page.keyboard.press("ArrowRight")
    assert page.get_by_label("Position X (%)",exact=True).input_value()=="21"
    frame=page.get_by_role("button",name="Placer Mon affichage",exact=True)
    box=frame.bounding_box()
    page.mouse.move(box["x"]+10,box["y"]+10);page.mouse.down();page.mouse.move(box["x"]+35,box["y"]+18,steps=5);page.mouse.up()
    assert int(page.get_by_label("Position X (%)",exact=True).input_value())>21
    assert not page.get_by_label("Cliquable à la télécommande",exact=True).is_checked()
    page.screenshot(path="/tmp/ha-tv-editor-free.png",full_page=True)
    page.get_by_label("Ajouter une application",exact=True).select_option("org.example.tv")
    page.get_by_role("button",name="Appliquer et voir sur la TV",exact=True).click()
    page.get_by_role("status").filter(has_text="version 4").wait_for()
    assert page.evaluate("saved.cards[2].interactive") is False
    assert page.evaluate("saved.cards[3].type")=="app"
    assert page.evaluate("saved.settings.nav_background") is False
    assert page.evaluate("saved.settings.nav_width")==65
    page.get_by_role("button",name="Barre",exact=True).click()
    page.get_by_role("button",name="Onglets",exact=True).click()
    page.get_by_role("button",name="Descendre Pour vous",exact=True).click()
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.navigation.filter(i=>i.type!=='voice')[0].type")=="applications"
    page.get_by_role("button",name="Retirer Maison",exact=True).click()
    page.get_by_label("Ajouter un onglet",exact=True).select_option("ha_dashboard")
    page.get_by_label("Libellé de l’onglet",exact=True).fill("Dashboard TV")
    page.get_by_label("Chemin HA de l’onglet",exact=True).fill("/dashboard-tv/tv")
    page.get_by_label("Affichage de cet onglet",exact=True).select_option("2")
    page.get_by_role("button",name="Logo",exact=True).click()
    page.get_by_label("Couleur du logo HA",exact=True).fill("#ffaa00")
    assert page.get_by_label("Libellé de l’onglet",exact=True).count()==0
    page.get_by_role("button",name="Position",exact=True).click()
    page.get_by_label("Position de la barre fixe",exact=True).select_option("0")
    assert page.evaluate("typeof document.querySelector('home-assistant-tv-panel').config.settings.nav_position")=="number"
    page.screenshot(path="/tmp/ha-tv-editor-bar.png",full_page=True)
    page.get_by_role("button",name="Écran",exact=True).click()
    block=page.get_by_role("button",name="Placer le bloc Salutation et sous-titre",exact=True)
    block.focus();page.keyboard.press("ArrowRight")
    assert page.get_by_label("Position horizontale (%)",exact=True).input_value()=="6"
    page.get_by_label("Afficher ce bloc",exact=True).uncheck()
    page.get_by_role("button",name="Placer le bloc Actions rapides",exact=True).click(position={"x":20,"y":15})
    assert page.get_by_label("Réglages à modifier",exact=True).input_value()=="Actions rapides"
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.navigation.at(-1).path")=="/dashboard-tv/tv"
    page.set_viewport_size({"width":1100,"height":740})
    assert page.locator(".controls").bounding_box()["x"]>page.locator(".preview-side").bounding_box()["x"]
    stage=page.locator(".preview-stage").bounding_box()
    assert abs(stage["width"]/stage["height"]-16/9)<0.02
    page.screenshot(path="/tmp/ha-tv-editor-laptop.png",full_page=True)
    page.set_viewport_size({"width":1440,"height":1050})
    page.get_by_role("button",name="Dashboard HA",exact=True).click()
    page.get_by_label("Chemin du tableau de bord (ex. /dashboard-tv/tv)",exact=True).fill("/dashboard-tv/tv")
    assert page.get_by_role("link",name="Créer ou modifier un tableau de bord HA ↗").get_attribute("target")=="_blank"
    page.evaluate("""() => {const panel=document.querySelector('home-assistant-tv-panel'),original=panel._hass.callWS;panel._hass.callWS=async m=>m.type.endsWith('/parse_card')?{lovelace:JSON.parse(m.yaml),yaml:m.yaml}:original(m);}""")
    page.get_by_role("button",name="Cartes",exact=True).click()
    page.get_by_role("button",name="Ajouter une vraie carte HA",exact=True).click()
    page.get_by_label("Titre dans l’éditeur",exact=True).fill("PAC HA")
    assert page.get_by_label("Dimensionnement de la carte",exact=True).input_value()=="zone"
    page.get_by_label("Dimensionnement de la carte",exact=True).select_option("yaml")
    raw=json.dumps({"type":"custom:button-card","name":"PAC","state_display":"[[[ return 'Chauffage'; ]]]","styles":{"card":[{"border-radius":"24px"}]}})
    page.get_by_label("YAML de la carte Home Assistant",exact=True).fill(raw)
    page.get_by_role("button",name="Envoyer aux TV du profil",exact=True).click()
    page.get_by_role("status").filter(has_text="Validez le YAML de chaque carte HA").wait_for()
    assert page.evaluate("revision")==4
    page.get_by_role("button",name="Valider et appliquer le YAML de la carte",exact=True).click()
    page.get_by_role("status").filter(has_text="Carte HA validée").wait_for()
    card=page.evaluate("document.querySelector('home-assistant-tv-panel').config.cards.at(-1)")
    assert card["type"]=="ha_card" and card["source_yaml"]==raw
    assert card["ha_sizing"]=="yaml"
    assert "tap_action" not in card and card["name"]=="PAC HA"
    page.get_by_role("button",name="Placer sur Pour vous",exact=True).click()
    assert page.get_by_role("button",name="Placer PAC HA",exact=True).count()==1
    assert page.get_by_label("Texte affiché ({state}, {name}, {greeting})",exact=True).count()==0
    page.get_by_label("Dimensionnement de la carte",exact=True).select_option("zone")
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.cards.at(-1).ha_sizing")=="zone"
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.cards.at(-1).source_yaml")==raw
    page.get_by_role("button",name="YAML",exact=True).click()
    page.get_by_label("Configuration YAML").fill('{"schema":1,"settings":{"ha_token":"not-a-token"},"cards":[]}')
    page.get_by_role("button",name="Valider le YAML",exact=True).click()
    page.get_by_role("status").filter(has_text="Réglage interdit").wait_for()
    assert page.evaluate("revision")==4
    page.get_by_label("Configuration YAML").fill('{"schema":1,"settings":{"hero_subtitle":"<img src=x onerror=alert(1)>"},"cards":[]}')
    page.get_by_role("button",name="Valider le YAML",exact=True).click()
    page.get_by_role("button",name="Aperçu",exact=True).click()
    page.get_by_alt_text("Capture réelle du launcher").wait_for(timeout=5000)
    assert page.get_by_alt_text("Capture réelle du launcher").get_attribute("src").startswith("data:image/jpeg;base64,")
    assert page.locator("img[src=x]").count()==0
    page.get_by_label("Actualiser toutes les 5 secondes",exact=True).uncheck()
    page.evaluate("window.offline=true")
    page.get_by_role("button",name="Actualiser la vue réelle",exact=True).click()
    page.get_by_role("status").filter(has_text="TV hors ligne").wait_for(timeout=5000)
    assert page.get_by_alt_text("Capture réelle du launcher").count()==1
    page.get_by_role("button",name="Apparence",exact=True).click()
    page.get_by_label("Réglages à modifier",exact=True).select_option("Général")
    assert page.get_by_label("Texte d’accueil",exact=True).input_value()=="<img src=x onerror=alert(1)>"
    page.get_by_role("button",name="Barre",exact=True).click()
    page.get_by_role("button",name="Informations",exact=True).click()
    page.get_by_label("Taille de l’heure",exact=True).fill("38")
    page.get_by_label("Taille de l’état de connexion",exact=True).fill("24")
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.settings.nav_clock_size")==38
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.settings.nav_status_size")==24
    page.get_by_role("button",name="Recherche vocale",exact=True).click()
    page.get_by_label("Service de recherche vocale",exact=True).select_option("2")
    page.get_by_label("Agent Assist (vide = agent HA par défaut)",exact=True).fill("conversation.test")
    page.get_by_label("Nom du troisième service",exact=True).fill("Mon assistant")
    page.get_by_label("Adresse HTTPS · {query} = demande",exact=True).fill("https://search.example/?q={query}")
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.settings.voice_custom_name")=="Mon assistant"
    page.get_by_label("Service de recherche vocale",exact=True).select_option("3")
    page.get_by_label("Service de recherche vocale",exact=True).select_option("2")
    page.get_by_role("button",name="Ajouter le micro à la barre",exact=True).click()
    page.get_by_role("button",name="Ajouter le micro à la barre",exact=True).click()
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.navigation.filter(i=>i.type==='voice').length")==1
    assert page.locator(".preview-side").count()==1
    page.get_by_role("button",name="Écran",exact=True).click()
    page.get_by_label("Réglages à modifier",exact=True).select_option("Éléments libres")
    page.get_by_role("button",name="Salutation → carte",exact=True).click()
    greeting=page.evaluate("document.querySelector('home-assistant-tv-panel').config.cards.find(c=>c.id==='home-greeting')")
    assert greeting["name"]=="{greeting}" and greeting["interactive"] is False and greeting["background_opacity"]==0
    page.get_by_role("button",name="Salutation → carte",exact=True).click()
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.cards.filter(c=>c.id==='home-greeting').length")==1
    page.get_by_role("button",name="Actions rapides → cartes",exact=True).click()
    cards=page.evaluate("document.querySelector('home-assistant-tv-panel').config.cards.filter(c=>c.id.startsWith('home-scene'))")
    assert len(cards)==2 and all(c["tap_action"]["confirmation"] for c in cards)
    assert all(c["placement"]["x"]+c["placement"]["width"]<=100 for c in cards)
    assert page.evaluate("document.querySelector('home-assistant-tv-panel').config.settings.show_quick") is False
    assert page.evaluate("revision")==4, "conversions and voice remain unsent drafts"
    page.screenshot(path="/tmp/ha-tv-home-cards-editor.png",full_page=True)
    page.clock.install()
    page.evaluate("() => { document.querySelector('home-assistant-tv-panel')._hass.callWS=()=>new Promise(()=>{}); }")
    page.get_by_role("button",name="Envoyer aux TV du profil",exact=True).click()
    page.clock.fast_forward(13000)
    page.get_by_role("status").filter(has_text="Home Assistant ne répond pas").wait_for(timeout=5000)
    assert page.get_by_role("button",name="Rangées",exact=True).is_enabled()
    assert not page.evaluate("calls.some(c=>c.type==='call_service')")
    page.get_by_role("button",name="Téléviseurs",exact=True).click()
    page.screenshot(path="/tmp/ha-tv-panel-browser.png",full_page=True)
    assert not errors,errors
    browser.close()
print("PASS: strict ES module, all settings tabs, readonly HTML fields, real preview, offline recovery and request timeout")
