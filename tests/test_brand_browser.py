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
    page.add_script_tag(content=script,type="module")
    page.wait_for_function("() => !!window.customIcons['tv-assistant']")
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
