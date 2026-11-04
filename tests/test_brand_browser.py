"""Local icon registration and real SVG raster checks, without an HA account."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
script = (root / "custom_components/home_assistant_tv/icons.js").read_text()

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("CHROME_BIN"), headless=True, args=["--no-sandbox"])
    page = browser.new_page(viewport={"width":600,"height":240})
    errors, requests = [], []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.on("request", lambda request: requests.append(request.url))
    # Global module loads independently of the panel and preserves other icon sets.
    page.evaluate("window.customIcons={existing:{getIcon:async()=>({path:'M0 0'})}}")
    # Model HA's real legacy renderer latch when an icon is requested before its
    # extra module loads. Repair must be limited to our icons, including shadows.
    page.evaluate("""() => {
      customElements.define('ha-icon', class extends HTMLElement {
        constructor(){super();this.attachShadow({mode:'open'});this._icon='';this.legacy=false;this.changes=[];}
        get icon(){return this._icon;}
        set icon(value){
          this._icon=value;this.changes.push(value);
          if(value.startsWith('mdi:')) this.legacy=false;
          else if(!window.customIcons?.[value.split(':')[0]]) this.legacy=true;
          this.shadowRoot.innerHTML=this.legacy?'<iron-icon></iron-icon>':'<ha-svg-icon></ha-svg-icon>';
          this.updateComplete=Promise.resolve(true);
        }
      });
      const host=document.createElement('div');host.attachShadow({mode:'open'});document.body.append(host);
      window.earlyIcon=document.createElement('ha-icon');earlyIcon.icon='tv-assistant:logo';host.shadowRoot.append(earlyIcon);
      window.otherIcon=document.createElement('ha-icon');otherIcon.icon='other:logo';host.shadowRoot.append(otherIcon);
    }""")
    page.add_script_tag(content=script,type="module")
    page.wait_for_function("() => !!window.customIcons['tv-assistant']")
    page.wait_for_function("() => earlyIcon.icon==='tv-assistant:logo' && !earlyIcon.legacy")
    assert page.evaluate("earlyIcon.changes")==['tv-assistant:logo','mdi:television','tv-assistant:logo']
    assert page.evaluate("otherIcon.changes")==['other:logo']
    page.evaluate("earlyIcon.parentNode.host.remove()")
    assert page.evaluate("async()=> (await customIcons.existing.getIcon()).path")=="M0 0"
    assert page.evaluate("async()=> (await customIcons['tv-assistant'].getIcon('unknown')).path")==""
    assert page.evaluate("async()=> (await customIcons['tv-assistant'].getIconList())[0].name")=="logo"
    for color, rgb in (("#222222",[34,34,34,255]),("#ffffff",[255,255,255,255]),("#00cc44",[0,204,68,255])):
        result = page.evaluate("""async color=>{
          const icon=await customIcons['tv-assistant'].getIcon('logo');
          const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
          svg.setAttribute('viewBox',icon.viewBox);svg.setAttribute('width','120');svg.setAttribute('height','120');
          svg.style.color=color;
          const path=document.createElementNS(svg.namespaceURI,'path');path.setAttribute('d',icon.path);path.setAttribute('fill','currentColor');svg.append(path);
          const box=document.createElement('div');box.style.cssText='display:inline-block;padding:24px;background:'+(color==='#222222'?'#fafafa':'#202020');box.append(svg);document.body.append(box);
          const url=URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svg)],{type:'image/svg+xml'}));
          const image=new Image();image.src=url;await image.decode();
          const canvas=document.createElement('canvas');canvas.width=canvas.height=120;
          const ctx=canvas.getContext('2d');ctx.drawImage(image,0,0);URL.revokeObjectURL(url);
          return {body:[...ctx.getImageData(25,40,1,1).data],branch:[...ctx.getImageData(60,50,1,1).data],outside:[...ctx.getImageData(0,0,1,1).data]};
        }""",color)
        assert result["body"]==rgb,result
        assert result["branch"][3]==0,result
        assert result["outside"][3]==0,result
    page.screenshot(path="/tmp/tv-assistant-sidebar-themes.png")
    assert not errors, errors
    assert all(url.startswith("blob:") for url in requests), "Icon registration must never fetch user data or remote assets"
    browser.close()
print("Sidebar logo: independent registration, theme colors and transparent branch passed")
