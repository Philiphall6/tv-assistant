/* TV Assistant — local admin editor. No CDN, analytics or eval. Only explicit notification tests call an HA service. */
import "/home_assistant_tv/icons.js?v=0.7.5";
import {raw as userText, isRaw, languageOf, translate} from "/home_assistant_tv/localize.js?v=0.9.0";
const clone = value => JSON.parse(JSON.stringify(value));
function node(tag, attrs = {}, ...children) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (k === "class") e.className = v;
    else if (k === "style") Object.assign(e.style, v);
    else if (["value","checked","disabled","selected","multiple","spellcheck"].includes(k)) e[k] = v;
    else if (v != null) e.setAttribute(k, v);
  }
  for (const child of children.flat()) if (child != null) e.append(child instanceof Node ? child : document.createTextNode(String(child)));
  return e;
}
// Mirrors AppIconShape's unit-square masks; vector previews stay sharp at any zoom.
function iconShapePreview(shape,ratio=160,corners=18) {
  const svg=document.createElementNS("http://www.w3.org/2000/svg","svg");
  svg.setAttribute("viewBox","0 0 1 1");svg.setAttribute("width","32");svg.setAttribute("height","32");svg.setAttribute("aria-hidden","true");
  if(shape===6||shape===7){
    const aspect=shape===6?16/9:Math.max(50,Math.min(250,ratio))/100,w=Math.min(1,aspect),h=Math.min(1,1/aspect);
    const rect=document.createElementNS(svg.namespaceURI,"rect");
    for(const [k,v] of Object.entries({x:(1-w)/2,y:(1-h)/2,width:w,height:h,rx:Math.min(w,h)*(shape===6?.12:Math.max(0,Math.min(50,corners))/100),fill:"currentColor"}))rect.setAttribute(k,v);
    svg.append(rect);return svg;
  }
  let d;
  if(shape===1)d="M .22 0 H .78 A .22 .22 0 0 1 1 .22 V .78 A .22 .22 0 0 1 .78 1 H .22 A .22 .22 0 0 1 0 .78 V .22 A .22 .22 0 0 1 .22 0 Z";
  else if(shape===2)d="M 0 0 H 1 V 1 H 0 Z";
  else if(shape===3)d="M .5 .08 C .66 .08 .7 0 .82 0 C .96 0 1 .09 1 .18 C 1 .30 .92 .36 .92 .5 C .92 .66 1 .7 1 .82 C 1 .96 .91 1 .82 1 C .70 1 .64 .92 .5 .92 C .34 .92 .30 1 .18 1 C .04 1 0 .91 0 .82 C 0 .70 .08 .64 .08 .5 C .08 .34 0 .30 0 .18 C 0 .04 .09 0 .18 0 C .30 0 .36 .08 .5 .08 Z";
  else if(shape===4){
    const points=Array.from({length:224},(_,i)=>{const a=i*Math.PI*2/224,r=.46+.04*Math.cos(7*a);return [.5+r*Math.sin(a),.5-r*Math.cos(a)];});
    const xs=points.map(p=>p[0]),ys=points.map(p=>p[1]),x=Math.min(...xs),y=Math.min(...ys),w=Math.max(...xs)-x,h=Math.max(...ys)-y;
    d=points.map((p,i)=>`${i?"L":"M"} ${(p[0]-x)/w} ${(p[1]-y)/h}`).join(" ")+" Z";
  }else if(shape===5)d="M 0 .5 C 0 .223858 .223858 0 .5 0 C .776142 0 1 .223858 1 .5 L 1 .88 Q 1 1 .88 1 L .12 1 Q 0 1 0 .88 Z";
  else d="M .5 0 A .5 .5 0 1 1 .5 1 A .5 .5 0 1 1 .5 0 Z";
  const path=document.createElementNS(svg.namespaceURI,"path");path.setAttribute("d",d);path.setAttribute("fill","currentColor");svg.append(path);return svg;
}
const css = `
.wallpaper-palette{display:grid;grid-template-columns:repeat(4,1fr);gap:5px}.wallpaper-palette .btn{padding:5px 2px;font-size:10px;display:flex;align-items:center;flex-direction:column;gap:4px}.wallpaper-palette .btn[aria-pressed=true]{outline:2px solid var(--primary-color,#007eae);outline-offset:0}.wallpaper-swatch{display:block;width:30px;height:24px;border-radius:5px;border:1px solid var(--divider-color,#bbc8d2)}
.icon-shapes{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin-top:6px}.icon-shapes .btn{display:flex;align-items:center;justify-content:center;min-height:46px;color:var(--primary-color,#007eae)}.icon-shapes .btn[aria-pressed=true]{background:#007eae;color:white}
:host{display:block;height:100%;overflow:auto;background:var(--primary-background-color,#f5f7fa);color:var(--primary-text-color,#172735);font:13px var(--paper-font-body1_-_font-family,system-ui)}
*{box-sizing:border-box}button,input,select,textarea{font:inherit}button{cursor:pointer}button:disabled{opacity:.5;cursor:wait}button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:2px solid #03a9f4;outline-offset:2px}
.shell{max-width:1800px;margin:auto;padding:12px 16px 24px}.top,.toolbar,.tabs,.line{display:flex;gap:6px;align-items:center;flex-wrap:wrap}.top{justify-content:space-between}.brand{display:flex;gap:8px;align-items:center}.mark{background:#03a9f4;color:white;border-radius:9px;padding:7px;font-size:18px}h1{font-size:20px;margin:0}h2{font-size:16px;margin:0 0 8px}h3{font-size:14px;margin:8px 0}p{line-height:1.4;margin:7px 0}.muted{color:var(--secondary-text-color,#647481);font-size:12px}.toolbar{margin:8px 0}.btn{border:1px solid var(--divider-color,#d8e0e6);border-radius:7px;background:var(--card-background-color,white);color:inherit;padding:6px 9px;min-height:30px}.primary{background:#007eae;color:white;border-color:transparent;font-weight:650}.tabs{gap:3px;border-bottom:1px solid var(--divider-color,#d8e0e6);padding:0 0 7px;margin:8px 0}.tab{border:0;background:transparent;color:inherit;padding:7px 10px;border-radius:7px}.tab.active{background:#007eae;color:white}
.card{background:var(--card-background-color,white);border:1px solid var(--divider-color,#e1e6ec);border-radius:10px;padding:12px;margin:8px 0}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.field{display:flex;flex-direction:column;gap:4px;min-width:0}.field>span{font-size:12px;font-weight:550}.field input,.field select,.field textarea,.toolbar input,.toolbar select{width:100%;padding:5px 7px;min-height:30px;border:1px solid var(--divider-color,#bbc8d2);background:var(--secondary-background-color,#fafbfc);color:inherit;border-radius:6px}.field input[type=checkbox]{width:18px;height:18px;min-height:18px;accent-color:#007eae}.field textarea{resize:vertical;min-height:64px}.field small{font-size:11px;color:var(--secondary-text-color,#647481)}.field code{overflow-wrap:anywhere}.toolbar select{width:auto;min-width:150px}.toolbar input{width:190px}.status{padding:3px 0;color:#007eae}.status:empty{display:none}.error,.danger{color:var(--error-color,#b3261e)}.code{width:100%;min-height:480px;font:13px/1.5 ui-monospace,monospace;padding:12px;border-radius:8px;background:var(--secondary-background-color,#edf1f5);color:inherit;border:1px solid var(--divider-color,#c4d0da)}.item{display:flex;gap:6px;align-items:center;padding:7px 0;border-bottom:1px solid var(--divider-color,#e1e6ec)}.item .grow{flex:1;min-width:0;overflow-wrap:anywhere}.badge{border-radius:12px;padding:4px 8px;font-size:11px;background:#e0f2ed;color:#206348}.offline{background:#eee3d3;color:#75572d}.notice{background:var(--secondary-background-color,#eef3f6);border-radius:7px;padding:8px;line-height:1.4;font-size:12px}.split{display:grid;grid-template-columns:1fr 1.3fr;gap:14px}
.studio{display:grid;grid-template-columns:minmax(0,1fr) 310px;gap:14px;align-items:start}.controls{grid-column:2;grid-row:1;max-height:calc(100dvh - 205px);overflow:auto;padding:10px;border:1px solid var(--divider-color,#d8e0e6);border-radius:10px;background:var(--card-background-color,white);scrollbar-width:thin}.preview-side{grid-column:1;grid-row:1;position:sticky;top:10px;min-width:0}.preview-side .card{padding:0;border:0;background:none}.preview-side .toolbar{gap:6px;margin:6px 0}.settings-grid{gap:10px}.controls .settings-grid{grid-template-columns:1fr}.controls .card{border:0;padding:0;margin:10px 0}.setting{min-width:0;position:relative;padding-right:24px}.local-reset{position:absolute;right:0;top:0;min-height:22px;padding:0 4px;font-size:14px;border:0;background:transparent;color:var(--secondary-text-color,#647481)}.field input[type=range]{padding:0;min-height:14px;height:14px;accent-color:#007eae}.field input[type=color]{height:30px;padding:2px}.preview-options{margin-top:6px}.preview-options summary{cursor:pointer;padding:6px}.preview-stage{position:relative;line-height:0;touch-action:none;width:100%;aspect-ratio:16/9;margin:auto;background:#101820;border-radius:9px}.preview-stage>img{width:100%;height:100%!important;object-fit:fill}.empty-stage{display:grid;place-items:center;height:100%;color:#aab9c6;font-size:13px;line-height:1.4;text-align:center}.placement-overlay{position:absolute;inset:0;pointer-events:none}.placement-frame{position:absolute;border:1px dashed #77dcff;background:#009ccd12;pointer-events:auto;cursor:move;touch-action:none;line-height:1.25;color:white;text-shadow:0 1px 2px #000;overflow:visible;min-width:0;padding:2px;font-size:10px}.placement-frame.active{border:2px solid #ffe68c;background:#e4b91f22;z-index:3}.placement-frame .resize{position:absolute;right:-4px;bottom:-4px;width:12px;height:12px;background:#ffe68c;cursor:se-resize;touch-action:none}.placement-frame span{display:block;background:#142733dc;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;max-width:100%;width:max-content}.placement-frame.bar-frame{cursor:pointer;border-color:#bc9cff}.element-list{display:flex;flex-direction:column;gap:4px;margin:8px 0}.element-list button{text-align:left}.screen-objects{display:flex;gap:4px;flex-wrap:wrap;margin:8px 0}.screen-objects .btn{font-size:11px;padding:4px 7px;min-height:26px}.subtabs{display:flex;gap:4px;flex-wrap:wrap;margin:8px 0}.subtabs .btn{padding:4px 7px;font-size:12px}.nav-item{display:flex;gap:3px;align-items:center;margin:4px 0}.nav-item .choose{flex:1;min-width:0;text-align:left;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.nav-item .btn{padding:4px 6px}.inspector-note{border-left:2px solid #03a9f4;padding:5px 8px;margin:8px 0;font-size:12px}
.brand-icon{display:block;flex:0 0 40px;object-fit:contain}
@media(max-width:1000px){.studio{grid-template-columns:minmax(0,1fr) 270px}.shell{padding:10px}.controls{max-height:calc(100dvh - 220px)}}
@media(max-width:720px){.studio{grid-template-columns:1fr}.preview-side{position:static;grid-column:1;grid-row:1}.controls{grid-column:1;grid-row:2;max-height:none}.preview-stage{width:100%}.grid,.split{grid-template-columns:1fr}.toolbar input{width:150px}.tabs{gap:1px}.tab{padding:6px}.brand .muted{display:none}}
`;

class HomeAssistantTVPanel extends HTMLElement {
  constructor() {
    super(); this.attachShadow({mode:"open"}); this.tab="Disposition"; this.profile="salon"; this.profileTitle="TV salon";
    this.revision=0; this.config={schema:1,settings:{},cards:[]}; this.meta={profiles:[],devices:[],schema:{}};
    this.dirty=false; this.yamlDirty=false; this.yaml=""; this.message=""; this.editIndex=-1;
    this.notificationTests=new Map();
    this.previewId="";this.previewFrame=null;this.previewMessage="";this.previewBusy=false;this.previewGeneration=0;this.previewAuto=true;this.settingsSections={};this.navEdit=-1;this.selectedBlock="";this.placementIndex=-1;this.showPlacementFrames=true;
    this.resizePreview=()=>this.fitPreview();this.beforeUnload=e=>{if(this.dirty||this.yamlDirty){e.preventDefault();e.returnValue="";}};
    this.visibility=()=>{this.stopPreview();if(!document.hidden)this.schedulePreview(0);};
  }
  set panel(value) {
    this._panel=value;
    const previous=this.integrationVersion;
    const version=value?.config?.integration_version;
    this.integrationVersion=typeof version==="string"&&/^\d+\.\d+\.\d+$/.test(version)?version:"";
    if(this.isConnected&&previous!==this.integrationVersion)this.render();
  }
  get panel(){return this._panel;}
  set hass(value) {
    const previous=this.language;this._hass=value;this.language=languageOf(value);
    this.lang=this.language;
    if(!this.loaded&&!this.loading&&!this.loadFailed)this.run(()=>this.initialize());
    else if(previous!==this.language)this.render();
  }
  t(value){return translate(value,this.language||languageOf(this._hass));}
  f(source,values){return this.t(source).replace(/\{([a-z_]+)\}/g,(all,key)=>Object.hasOwn(values,key)?String(values[key]):all);}
  node(tag,attrs={},...children){
    const localized={...attrs};
    for(const key of ["aria-label","title","alt","placeholder"])if(key in localized)localized[key]=this.t(localized[key]);
    return node(tag,localized,...children.flat().map(child=>typeof child==="string"||isRaw(child)?this.t(child):child));
  }
  async initialize(){this.loading=true;try{await this.refresh();if(this.meta.profiles.length)await this.load(this.meta.profiles[0].id);this.loaded=true;this.loadFailed=false;}catch(e){this.loadFailed=true;throw e;}finally{this.loading=false;}}
  connectedCallback(){window.addEventListener("resize",this.resizePreview);window.addEventListener("beforeunload",this.beforeUnload);document.addEventListener("visibilitychange",this.visibility);this.render();this.schedulePreview(0);}
  disconnectedCallback(){window.removeEventListener("resize",this.resizePreview);window.removeEventListener("beforeunload",this.beforeUnload);document.removeEventListener("visibilitychange",this.visibility);this.stopPreview();this.previewFrame=null;}
  async ws(type, args={}) {if(!this._hass)throw Error("Connexion à Home Assistant en attente");let timer;try{return await Promise.race([this._hass.callWS({type:`home_assistant_tv/${type}`,...args}),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error("Home Assistant ne répond pas. Réessayez ; votre brouillon est conservé.")),12000);})]);}finally{clearTimeout(timer);}}
  async run(fn) {
    if(this.busy)return;this.busy=true;this.shadowRoot.querySelectorAll("button,input,select,textarea").forEach(e=>e.disabled=true);
    try {await fn();this.error=false;}catch(e){this.message=e.message||String(e);this.error=true;this.loading=false;}
    finally {this.busy=false;this.render();}
  }
  async refresh(){this.meta=await this.ws("list");}
  async load(id){this.haDrafts?.clear();const r=await this.ws("get",{profile:id});this.stopPreview();this.previewFrame=null;this.previewId="";this.profile=id;this.profileTitle=r.title;this.revision=r.revision;this.config=r.config||{schema:1,settings:{},cards:[]};this.yaml=r.yaml;this.dirty=false;this.yamlDirty=false;this.editIndex=-1;this.placementIndex=-1;this.navEdit=-1;this.selectedBlock="";this.resetUndo();}
  discard(){return !(this.dirty||this.yamlDirty)||window.confirm(this.t("Abandonner les modifications non envoyées ?"));}
  async deleteProfile(){
    if(!this.meta.profiles.some(p=>p.id===this.profile))return;
    if(!this.discard())return;
    if(!window.confirm(this.f("Supprimer définitivement le profil « {profile} » et son historique ? Les profils encore affectés à une TV doivent d’abord être détachés. Cette action ne réinitialise aucune TV.",{profile:this.profileTitle})))return;
    await this.ws("delete_profile",{profile:this.profile,revision:this.revision});
    this.stopPreview();this.previewFrame=null;this.previewId="";this.haDrafts?.clear();
    await this.refresh();
    if(this.meta.profiles.length)await this.load(this.meta.profiles[0].id);
    else {this.profile="nouveau-profil";this.profileTitle=this.t("Nouveau profil");this.revision=0;this.config={schema:1,settings:{},cards:[]};this.yaml="";this.yamlDirty=false;this.dirty=false;this.editIndex=-1;this.placementIndex=-1;this.selectedBlock="";}
    this.message="Profil supprimé. Les autres profils et les réglages des TV sont conservés.";
  }
  async forgetDevice(d){
    if(!window.confirm(this.f("Supprimer la connexion « {tv} » ? Son profil reste conservé. La TV garde son affichage local et son jeton HA. Si la liaison reste activée sur la TV, elle réapparaîtra à sa prochaine connexion, sans autorisation. Pour un retrait durable, désactivez d’abord « Édition depuis Home Assistant » sur la TV.",{tv:d.name})))return;
    await this.ws("forget_device",{device_id:d.id});
    if(this.previewId===d.id){this.stopPreview();this.previewFrame=null;this.previewId="";}
    await this.refresh();this.message="Connexion supprimée. Le jeton Home Assistant n’a pas été révoqué.";
  }
  async testNotification(d){
    if(d.approved===false||this.notificationTests.get(d.id)?.pending)return;
    const state={pending:true,text:"Envoi du test…",error:false};this.notificationTests.set(d.id,state);this.render();
    let timer;
    try{
      const result=await Promise.race([
        this._hass.callWS({type:"call_service",domain:"home_assistant_tv",service:"notify",return_response:true,service_data:{device_ids:[d.id],title:"TV Assistant",message:this.t("Ceci est une notification de test. Elle disparaîtra dans 8 secondes."),duration:8,position:"top_right",tag:"tv_assistant_test"}}),
        new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error("timeout")),12000);})
      ]);
      if(result?.response?.sent===1&&result?.response?.skipped===0)state.text="Test envoyé à la TV. Vérifiez le bandeau à l’écran ; il disparaît après 8 secondes.";
      else if(result?.response?.sent===0&&result?.response?.skipped===1)state.text="Test non envoyé : TV en veille, déconnectée ou réception désactivée. Aucun message ne sera conservé.";
      else {state.text="Réponse non reconnue : impossible de confirmer l’envoi. Aucun nouvel essai automatique.";state.error=true;}
    }catch(e){
      state.text=e?.message==="timeout"?"HA n’a pas confirmé l’envoi. Le test a peut-être été reçu ; aucun nouvel essai automatique.":"Test impossible : vérifiez la connexion HA, l’autorisation de la TV et l’activation des notifications. Attendez une seconde avant de réessayer.";state.error=true;
    }finally{clearTimeout(timer);state.pending=false;this.render();}
  }
  resetUndo(){this.undoProfile=this.profile;this.undoSteps=[];this.undoCurrent=clone(this.config);this.undoControl=null;}
  undoEdit(){
    if(!this.undoSteps?.length||this.undoProfile!==this.profile)return;
    if(this.haDrafts?.size||this.yamlDirty){this.message=this.t("Validez ou abandonnez le brouillon YAML avant d’annuler une autre modification.");this.error=true;this.render();return;}
    const placed=this.config.cards[this.placementIndex]?.id,edited=this.config.cards[this.editIndex]?.id;
    this.config=this.undoSteps.pop();this.undoCurrent=clone(this.config);this.undoControl=null;
    if(this.tab==="YAML")this.tab="Disposition";
    this.editIndex=edited?this.config.cards.findIndex(c=>c.id===edited):-1;this.placementIndex=placed?this.config.cards.findIndex(c=>c.id===placed):-1;
    this.presetUndo=null;this.mark(false);this.render();
  }
  mark(record=true){
    if(record&&this.undoProfile===this.profile&&this.undoCurrent&&JSON.stringify(this.config)!==JSON.stringify(this.undoCurrent)){
      const control=this.shadowRoot.activeElement,now=Date.now();
      if(!control||control!==this.undoControl||now-(this.undoTime||0)>600){this.undoSteps.push(clone(this.undoCurrent));if(this.undoSteps.length>20)this.undoSteps.shift();}
      this.undoControl=control;this.undoTime=now;this.undoCurrent=clone(this.config);
    }
    this.dirty=true;this.updateDraftNotice();this.paintPlacementOverlay();const e=this.shadowRoot.querySelector("#draft");if(e)e.textContent=this.t("Brouillon non envoyé");const undo=this.shadowRoot.querySelector("#undo-edit");if(undo)undo.disabled=!this.undoSteps?.length;
  }
  button(label, fn, cls=""){return this.node("button",{class:`btn ${cls}`,onclick:()=>this.run(fn)},label);}
  async applyYaml(){if(this.yamlDirty){const r=await this.ws("validate",{yaml:this.yaml});this.config=r.config;this.yaml=r.yaml;this.yamlDirty=false;this.dirty=true;}}
  async save(){if(this.haDrafts?.size)throw Error("Validez le YAML de chaque carte HA modifiée avant d’enregistrer.");await this.applyYaml();const valid=await this.ws("validate",{config:this.config});const r=await this.ws("save",{profile:this.profile,title:this.profileTitle,revision:this.revision,config:valid.config});this.config=r.config;this.yaml=r.yaml;this.revision=r.revision;this.dirty=false;await this.refresh();this.message=this.f("Profil « {profile} » enregistré, version {revision}. Envoyé aux TV connectées affectées à ce profil ; les autres le recevront au retour à l’accueil.",{profile:this.profileTitle,revision:r.revision});}
  setting(key){const device=this.meta.devices.find(d=>d.id===this.previewId)||this.meta.devices.find(d=>d.profile===this.profile);return this.config.settings[key]??device?.reported_config?.settings?.[key]??this.meta.schema[key]?.default;}
  input(label,value,changed,options={}) {
    const input=this.node(options.multiline?"textarea":options.options?"select":"input",{"aria-label":label,...(!options.options?{type:options.type||"text"}:{}),...(options.min!==undefined?{min:options.min,max:options.max}:{}),...(options.list?{list:options.list}:{}),oninput:e=>changed(options.type==="checkbox"?e.target.checked:options.type==="number"?Number(e.target.value):e.target.value)});
    if(options.options)for(const [v,l] of options.options)input.append(this.node("option",{value:String(v)},options.rawOptions?userText(l):l));
    if(options.type==="checkbox")input.checked=!!value;else input.value=value??"";
    return this.node("label",{class:"field"},this.node("span",{},label),input);
  }
  render(){
    try{this.renderContent();}
    catch(e){this.busy=false;this.loading=false;this.message=`Affichage impossible : ${e.message||e}. Vous pouvez changer d’onglet ou réessayer.`;this.error=true;this.shadowRoot.querySelectorAll("button,input,select,textarea").forEach(el=>el.disabled=false);const status=this.shadowRoot.querySelector("[role=status]");if(status){status.textContent=this.message;status.classList.add("error");}else this.shadowRoot.append(this.node("p",{role:"alert"},this.message),this.button("Réessayer le chargement",()=>this.initialize()));}
  }
  renderContent(){
    if(!this.shadowRoot)return;
    if(this.config&&this.undoProfile!==this.profile)this.resetUndo();
    const s=this.node("main",{class:"shell"});
    s.append(this.node("header",{class:"top"},this.node("div",{class:"brand"},this.node("button",{class:"btn",title:"Ouvrir le menu Home Assistant",onclick:()=>this.dispatchEvent(new CustomEvent("hass-toggle-menu",{bubbles:true,composed:true}))},"☰"),this.node("img",{class:"brand-icon",src:"/home_assistant_tv/brand-icon.svg"+(this.integrationVersion?"?v="+this.integrationVersion:""),alt:"",width:40,height:40,"aria-hidden":"true"}),this.node("div",{},this.node("h1",{},"TV Assistant"),this.node("div",{class:"muted"},this.t("Éditeur TV")+(this.integrationVersion?" · "+this.integrationVersion:"")))),this.node("span",{id:"draft",class:"badge"},this.dirty||this.yamlDirty?"Brouillon non envoyé":`Version ${this.revision}`)));
    const select=this.node("select",{"aria-label":"Profil",onchange:e=>this.run(async()=>{if(this.discard()){await this.load(e.target.value);this.message="";}})});
    const profiles=[...this.meta.profiles];if(!profiles.some(p=>p.id===this.profile))profiles.push({id:this.profile,title:this.profileTitle+" (nouveau)"});
    for(const p of profiles)select.append(this.node("option",{value:p.id},userText(p.title)));select.value=this.profile;
    s.append(this.node("div",{class:"toolbar"},select,this.node("input",{value:this.profileTitle,"aria-label":"Nom du profil",maxLength:80,oninput:e=>{this.profileTitle=e.target.value;this.mark();}}),this.button("Nouveau profil",async()=>{if(!this.discard())return;const id=window.prompt(this.t("Identifiant unique du profil (ex. chambre, salon, cuisine)"));if(!id)return;if(!/^[a-z0-9][a-z0-9_-]{0,39}$/.test(id))throw Error("Identifiant invalide");if(this.meta.profiles.some(p=>p.id===id))throw Error("Ce profil existe déjà");this.profile=id;this.profileTitle=id;this.revision=0;this.config={schema:1,settings:{},cards:[]};this.yaml="";this.yamlDirty=false;this.dirty=true;this.editIndex=-1;}),this.button("Envoyer aux TV du profil",()=>this.save(),"primary")));
    if(this.meta.profiles.some(p=>p.id===this.profile))s.querySelector(".toolbar").append(this.button("Supprimer le profil",()=>this.deleteProfile(),"danger"));
    s.append(this.node("button",{class:"btn",id:"undo-edit",disabled:!this.undoSteps?.length,title:"Historique de cette session, 20 modifications. Reste en brouillon jusqu’à l’envoi.",onclick:()=>this.undoEdit()},"Annuler la dernière modification"));
    s.append(this.node("nav",{class:"tabs"},["Disposition","Barre","Apparence","Fond d’écran","Cartes","Rangées","Favoris","Dashboard HA","Téléviseurs","Aperçu","YAML"].map(t=>this.node("button",{class:`tab ${t===this.tab?"active":""}`,onclick:()=>this.run(async()=>{await this.applyYaml();this.stopPreview();this.tab=t;if(t==="YAML"){const r=await this.ws("validate",{config:this.config});this.yaml=r.yaml;}if(this.hasPreview()){await this.refresh();this.schedulePreview(0);}})},t==="Disposition"?"Écran":t))));
    s.append(this.node("div",{class:`status ${this.error?"error":""}`,role:"status","aria-live":"polite"},this.message));
    if(!this.loaded)s.append(this.node("p",{},this.loadFailed?"Le panneau n’a pas pu être chargé.":"Chargement du panneau…"),...(this.loadFailed?[this.button("Réessayer le chargement",()=>this.initialize())]:[]));
    else if(this.tab==="Téléviseurs")this.renderDevices(s);
    else if(this.tab==="Aperçu")this.renderPreview(s);
    else if(this.tab==="Cartes")this.renderCards(s);
    else if(this.tab==="YAML")this.renderYaml(s);
    else if(this.hasPreview()){const studio=this.node("div",{class:"studio"}),controls=this.node("section",{class:"controls"}),preview=this.node("aside",{class:"preview-side"});if(this.tab==="Barre")this.renderNavigation(controls);else this.renderSettings(controls);this.renderPreview(preview);studio.append(controls,preview);s.append(studio);}
    else this.renderSettings(s);
    if(this.tab==="Rangées"||this.tab==="Cartes"||this.tab==="Disposition"){const entities=this.node("datalist",{id:"hatv-entities"});for(const [id,state] of Object.entries(this._hass?.states||{}))entities.append(this.node("option",{value:id},userText(state.attributes?.friendly_name||id)));s.append(entities);}
    this.shadowRoot.replaceChildren(this.node("style",{},css),s);this.fitPreview();this.paintPlacementOverlay();this.updateDraftNotice();if(this.hasPreview()&&!this.previewBusy)this.schedulePreview(this.previewFrame?5000:0);
  }
  renderDevices(s){
    s.append(this.node("section",{class:"card"},this.node("h2",{},"Connecter une TV en 3 étapes"),this.node("ol",{},
      this.node("li",{},"Sur la TV : Réglages du launcher → Home Assistant. Saisissez l’adresse HTTPS de votre HA et le jeton d’un utilisateur non administrateur, puis testez la connexion."),
      this.node("li",{},"Sur la TV : ouvrez « Édition depuis Home Assistant », donnez un nom à la TV et activez la liaison. Revenez à l’accueil du launcher."),
      this.node("li",{},"Ici, avec votre compte administrateur : créez et enregistrez un profil, vérifiez l’identifiant de la TV, choisissez ce profil puis cliquez sur « Affecter ce profil ». Plusieurs TV peuvent partager un profil.")),
      this.node("p",{class:"notice"},"« Maison connectée » sur la TV signifie que les données HA sont accessibles. Cela ne confirme pas la liaison avec cet éditeur : celle-ci doit être activée et autorisée séparément."),
      this.node("p",{class:"muted"},"Hors ligne signifie que l’éditeur ne reçoit plus la liaison du launcher. La TV peut être en veille, dans une autre application, ou avoir une liaison désactivée/refusée. HA seul ne peut pas déterminer la cause : consultez l’état de liaison sur la TV.")),
      this.button("Actualiser les connexions",async()=>{await this.refresh();this.message="Connexions actualisées";}));
    if(!this.meta.devices.length)s.append(this.node("div",{class:"card"},this.node("h2",{},"Aucune TV reliée pour le moment"),this.node("p",{},"Activez la liaison sur la TV puis actualisez cette liste. Aucun jeton ne doit être saisi dans cet éditeur.")));
    for(const d of this.meta.devices){
      let name=d.name,profile=d.profile;
      const savedProfile=this.meta.profiles.find(p=>p.id===d.profile);
      const stage=d.approved===false?"Autorisation en attente":!savedProfile?"Profil à créer":d.error?"Profil refusé par la TV":d.online&&d.applied_revision===savedProfile.revision?"Profil synchronisé":"Synchronisation en attente";
      const card=this.node("section",{class:"card"},this.node("div",{class:"top"},this.node("h2",{},userText(d.name)),this.node("span",{class:`badge ${d.online?"":"offline"}`},d.online?"Éditeur relié":"Éditeur hors ligne")),this.node("p",{},stage),this.node("p",{class:"muted"},this.f("Identifiant à comparer sur la TV : {id}",{id:d.id})),this.node("p",{class:"muted"},this.f("Profil : {profile} · révision appliquée : {revision} · dernière liaison : {date}",{profile:d.profile,revision:d.applied_revision??"—",date:d.last_seen?new Date(d.last_seen).toLocaleString(this._hass?.locale?.language||this.language):"—"})));
      if(d.approved===false)card.append(this.node("p",{class:"notice"},"Nouvelle TV en attente : vérifiez son identité puis affectez explicitement un profil pour autoriser sa lecture. Aucun profil n’est encore transmis."));
      if(d.error)card.append(this.node("p",{class:"error"},d.error));
      card.append(this.node("p",{class:"muted"},d.notifications==="ready"?"Notifications : réception prête":d.notifications==="paused"?"Notifications : veille ou affichage suspendu":"Notifications : non reliées ou désactivées"));
      const test=this.notificationTests.get(d.id);
      card.append(this.node("button",{class:"btn primary",disabled:d.approved===false||!!test?.pending,onclick:()=>this.testNotification(d)},test?.pending?"Envoi du test…":"Tester la notification"),this.node("p",{class:`muted ${test?.error?"error":""}`,role:"status","aria-live":"polite"},test?.text||"Test de 8 secondes sur cette TV uniquement, sans la réveiller."));
      if(d.notification_entity)card.append(this.node("p",{},"Entité de notification : ",this.node("code",{style:{overflowWrap:"anywhere"}},userText(d.notification_entity))));
      card.append(this.node("p",{class:"muted"},"Dans une automatisation HA : choisissez l’action « Notifications : Envoyer un message », puis l’entité de cette TV. Titre et message suffisent."));
      card.append(this.node("details",{},this.node("summary",{},"Envoyer une notification depuis HA"),
        this.node("p",{class:"notice"},"Activez les notifications sur la TV et autorisez la superposition. Aucun réveil ni rattrapage : les messages en veille ou hors ligne sont abandonnés. L’éditeur peut être hors ligne alors que la réception des notifications est prête."),
        this.node("pre",{style:{whiteSpace:"pre-wrap",overflowWrap:"anywhere"}},userText(`action: home_assistant_tv.notify\ndata:\n  device_ids: [${JSON.stringify(d.id)}]\n  title: TV Assistant\n  message: Test\n  duration: 8\n  position: top_right\n  tag: test`)),
        this.node("p",{},"Pour retirer un bandeau : action home_assistant_tv.dismiss_notification, avec les mêmes device_ids et tag.")));
      const opts=this.meta.profiles.map(p=>[p.id,userText(p.title)]);if(!opts.some(o=>o[0]===profile))opts.push([profile,profile+" (à créer)"]);
      card.append(this.node("div",{class:"grid"},this.input("Nom de la TV",name,v=>name=v),this.input("Profil affecté",profile,v=>profile=v,{options:opts})),this.node("div",{class:"toolbar"},this.button("Affecter ce profil",async()=>{await this.ws("assign",{device_id:d.id,name,profile});await this.refresh();this.message="Affectation enregistrée. Les autres TV conservent leur profil.";}),this.button("Importer les réglages de cette TV dans le brouillon",async()=>{if(!this.discard())return;this.config=clone(d.reported_config);this.dirty=true;this.yamlDirty=false;this.editIndex=-1;this.message="Réglages importés dans le brouillon. Vérifiez-les puis envoyez le profil.";})));
      card.append(this.button("Supprimer la connexion",()=>this.forgetDevice(d),"danger"));
      s.append(card);
    }
  }
  hasPreview(){return ["Aperçu","Disposition","Barre","Apparence","Fond d’écran"].includes(this.tab);}
  updateDraftNotice(){const e=this.shadowRoot.querySelector("#preview-draft");if(e)e.textContent=this.t(this.dirty||this.yamlDirty?"Brouillon non envoyé : l’image montre encore la version appliquée sur la TV.":"Image réelle de la version appliquée sur la TV."); }
  numberControl(label,value,changed,min,max){
    const number=this.input(label,value,v=>{const input=number.querySelector("input");v=Math.max(Number(input.min),Math.min(Number(input.max),Math.round(v)));input.value=String(v);range.value=String(v);changed(v);},{type:"number",min,max});
    const range=this.node("input",{type:"range",min,max,step:1,value,"aria-label":this.t(label)+this.t(" — curseur"),oninput:e=>{const v=Number(e.target.value);number.querySelector("input").value=String(v);changed(v);}});
    number.append(range);return number;
  }
  iconShapeControl(rule,change){
    const buttons=[],choose=value=>{value=Number(value);select.querySelector("select").value=String(value);for(const [id,b] of buttons)b.setAttribute("aria-pressed",String(id===value));change(value);this.refreshIconControls();};
    const select=this.input(rule.label,this.setting("app_icon_shape"),choose,{options:rule.options});
    const previews=this.node("div",{class:"icon-shapes",role:"group","aria-label":"Aperçu des formes"});
    for(const [id,label] of rule.options){const b=this.node("button",{class:"btn",type:"button",title:label,"data-icon-shape":id,"aria-label":this.t("Choisir : ")+this.t(label),"aria-pressed":String(id===Number(this.setting("app_icon_shape"))),onclick:()=>choose(id)},iconShapePreview(id,this.setting("app_icon_ratio"),this.setting("app_icon_corners")));buttons.push([id,b]);previews.append(b);}
    return this.node("div",{class:"field"},select,previews,this.node("small",{},"Favoris, applications et raccourcis placés. Icônes remplies, sans contour."),this.node("small",{},"100 % = carré ; 160 % = rectangle. Le logo garde ses proportions."));
  }
  refreshIconControls(){
    for(const e of this.shadowRoot.querySelectorAll("[data-custom-icon]"))for(const input of e.querySelectorAll("input"))input.disabled=Number(this.setting("app_icon_shape"))!==7;
    const b=this.shadowRoot.querySelector('[data-icon-shape="7"]');if(b)b.replaceChildren(iconShapePreview(7,this.setting("app_icon_ratio"),this.setting("app_icon_corners")));
  }
  wallpaperColorControl(rule,change){
    const buttons=[],choose=value=>{
      if(!/^#[0-9a-f]{6}$/i.test(value))return;
      value=value.toUpperCase();color.querySelector("input").value=value;
      for(const [code,b] of buttons)b.setAttribute("aria-pressed",String(code.toUpperCase()===value));
      this.config.settings.wallpaper_mode=5;
      const mode=this.shadowRoot.querySelector('select[aria-label="'+this.t("Fond d’écran")+'"]');if(mode)mode.value="5";
      change(value);
    };
    const color=this.input(rule.label,this.setting("wallpaper_color"),choose,{type:"color"});
    const palette=this.node("div",{class:"wallpaper-palette",role:"group","aria-label":"Couleurs de fond d’écran"});
    for(const [code,label] of rule.presets||[]){
      const b=this.node("button",{class:"btn",type:"button",title:label+" · "+code,"aria-label":this.t("Fond uni : ")+this.t(label),"aria-pressed":String(code.toUpperCase()===String(this.setting("wallpaper_color")).toUpperCase()),onclick:()=>choose(code)},this.node("span",{class:"wallpaper-swatch",style:{backgroundColor:code},"aria-hidden":"true"}),label);
      buttons.push([code,b]);palette.append(b);
    }
    return this.node("div",{class:"field"},color,palette,this.node("small",{},"Choisir une teinte active Couleur unie dans le brouillon. Sans dégradés de photo ; l’assombrissement reste réglable. Pour les couleurs claires, pensez aussi à la couleur des textes."));
  }
  renderLayoutPresets(s){
    const presets=this.meta.layout_presets||[];if(!presets.length)return;
    const section=this.node("details",{class:"preview-options"},this.node("summary",{},"Dispositions prêtes à l’emploi"));
    section.append(this.node("p",{class:"muted"},"Change uniquement le placement des rangées classiques et la barre. Applications, connexions et cartes libres conservées. Vérifiez leurs chevauchements dans l’aperçu avant d’envoyer."));
    for(const preset of presets)section.append(this.button(preset.name,async()=>{
      const values={};for(const [key,value] of Object.entries(preset.settings)){if(!this.meta.schema[key])throw Error(this.t("Modèle incompatible avec cette version"));values[key]=[Object.hasOwn(this.config.settings,key),this.config.settings[key]];}
      this.presetUndo={profile:this.profile,values};Object.assign(this.config.settings,clone(preset.settings));this.mark();
    }),this.node("p",{class:"muted"},preset.description));
    if(this.presetUndo?.profile===this.profile)section.append(this.button("Annuler le dernier modèle",async()=>{
      for(const [key,[present,value]] of Object.entries(this.presetUndo.values)){if(present)this.config.settings[key]=value;else delete this.config.settings[key];}
      this.presetUndo=null;this.mark();
    }));s.append(section);
  }
  renderSettings(s){
    s.append(this.node("p",{class:"muted"},this.tab==="Disposition"?"Cliquez un cadre dans l’écran pour le régler.":"Les changements restent en brouillon jusqu’à l’envoi."));
    if(this.tab==="Disposition")this.renderLayoutPresets(s);
    const fields=Object.entries(this.meta.schema).filter(([,r])=>r.group===this.tab),groups=[...new Set(fields.map(([,r])=>r.section||"Général"))];
    if(this.tab==="Disposition")groups.unshift("Éléments libres");
    let section=this.settingsSections[this.tab];if(!groups.includes(section))section=this.tab==="Disposition"?"Éléments libres":groups.includes("Général")?"Général":groups[0];
    this.settingsSections[this.tab]=section;
    if(this.hasPreview())s.append(this.input("Réglages à modifier",section,v=>{this.settingsSections[this.tab]=v;this.render();},{options:groups.map(g=>[g,g])}));
    if(this.tab==="Disposition"&&section==="Éléments libres"){this.renderPlacement(s);return;}
    if(this.tab==="Disposition"){const key=this.blockEntries().find(([,label])=>label===section)?.[0];if(key){this.selectedBlock=key;s.append(this.input("Afficher ce bloc",(this.setting("show_"+key)!==false||(key==="greeting"&&this.setting("show_subtitle")!==false)),v=>{this.config.settings["show_"+key]=v;if(key==="greeting")this.config.settings.show_subtitle=v;this.mark();},{type:"checkbox"}));}}
    if(this.tab==="Dashboard HA")s.append(this.node("section",{class:"notice"},this.node("h2",{},"Un vrai dashboard HA, à la demande"),this.node("p",{},"Maison conserve ses cartes natives. Activez le bouton, puis indiquez le chemin du tableau de bord dédié à la TV. La première ouverture demande la connexion web habituelle de HA ; aucun jeton du launcher n’est injecté. Les cartes HACS installées dans HA fonctionnent dans cette vue web, selon leur compatibilité Android WebView."),this.node("a",{href:"/config/lovelace/dashboards",target:"_blank",rel:"noopener"},"Créer ou modifier un tableau de bord HA ↗"),this.node("p",{},"Dans HA : Paramètres → Tableaux de bord → Ajouter → Nouveau tableau de bord. Nommez-le TV, ajoutez vos cartes, ouvrez sa vue et copiez seulement le chemin (par exemple /dashboard-tv/tv)."),this.node("p",{},"Télécommande : flèches = pointeur, OK = clic, CH +/− = défilement, Menu = options, Retour = cartes Maison. Aucun rechargement périodique. Les aperçus de l’extension ne capturent pas cette vue web.")));
    const grid=this.node("div",{class:"grid settings-grid"});
    for(const [key,r] of fields){
      if(this.hasPreview()&&(r.section||"Général")!==section)continue;
      const customIcon=["app_icon_ratio","app_icon_corners","app_icon_logo_size"].includes(key);
      const wrap=this.node("div",{class:"setting",...(customIcon?{"data-custom-icon":"true"}:{})}),change=v=>{this.config.settings[key]=r.type==="integer"?Number(v):v;this.mark();if(customIcon)this.refreshIconControls();};
      const control=key==="app_icon_shape"?this.iconShapeControl(r,change):key==="wallpaper_color"?this.wallpaperColorControl(r,change):r.type==="integer"&&!r.options?this.numberControl(r.label,this.setting(key),change,r.min,r.max):this.input(r.label,this.setting(key),change,{type:r.type==="boolean"?"checkbox":r.type==="integer"?"number":r.format==="color"?"color":"text",min:r.min,max:r.max,options:r.options,multiline:["entities","packages"].includes(r.format),list:r.format==="entity"?"hatv-entities":undefined});
      if(customIcon)for(const input of control.querySelectorAll("input"))input.disabled=Number(this.setting("app_icon_shape"))!==7;
      wrap.append(control);if(Object.prototype.hasOwnProperty.call(this.config.settings,key))wrap.append(this.node("button",{class:"btn local-reset",title:"Revenir au réglage local de la TV","aria-label":this.t("Réglage local : ")+this.t(r.label),onclick:()=>{delete this.config.settings[key];this.mark();this.render();}},"↺"));grid.append(wrap);
    }
    s.append(this.node("section",{class:"card"},this.node("h2",{},this.hasPreview()?section:this.tab),grid));
    if(this.tab==="Favoris"){
      const apps=new Map();for(const d of this.meta.devices.filter(d=>d.profile===this.profile))for(const a of d.apps||[])apps.set(a.package,a.label);
      s.append(this.node("h2",{},"Applications signalées par les TV de ce profil"));
      for(const [pkg,label] of apps){const list=(this.config.settings.favorite_apps_order||"").split("\n").filter(Boolean);s.append(this.button(`${list.includes(pkg)?"✓ ":"+ "}${label}`,async()=>{this.config.settings.favorite_apps_order=(list.includes(pkg)?list.filter(x=>x!==pkg):[...list,pkg]).join("\n");this.mark();}));}
      s.append(this.node("p",{class:"muted"},"L’ordre des lignes fixe l’ordre des favoris. Une application absente d’une TV est ignorée sur cette TV."));
    }
    if(this.tab==="Fond d’écran")s.append(this.node("p",{class:"notice"},"Les photos restent un diaporama natif. L’album est vérifié uniquement lorsque l’accueil est visible. L’image personnelle doit être importée sur chaque TV. Le lien partagé de l’album est stocké dans HA : ne partagez pas l’export YAML publiquement."));
  }
  navigationItems(){
    const device=this.meta.devices.find(d=>d.id===this.previewId)||this.meta.devices.find(d=>d.profile===this.profile);
    return this.config.navigation??device?.reported_config?.navigation??[
      {id:"voice",type:"voice",label:"Recherche",icon:"mdi:microphone",display:1},
      {id:"home",type:"home",label:"Pour vous",icon:"mdi:home"},
      {id:"applications",type:"applications",label:"Applications",icon:"mdi:apps"},
      {id:"house",type:"house",label:"Maison",icon:"mdi:home-automation"},
      {id:"tv_settings",type:"tv_settings",label:"Paramètres TV",icon:"mdi:television"},
      {id:"launcher_settings",type:"launcher_settings",label:"Home Assistant",icon:"mdi:cog"}].map(item=>({...item,label:this.t(item.label)}));
  }
  editNavigation(){if(!this.config.navigation)this.config.navigation=clone(this.navigationItems());return this.config.navigation;}
  renderNavigation(s){
    s.append(this.node("h2",{},"Barre de navigation"),this.node("p",{class:"muted"},"Ces réglages concernent uniquement la barre, jamais les cartes."));
    const sections=["Onglets","Position","Fond","Textes et icônes","Informations","Recherche vocale","Logo"];
    let section=this.settingsSections.Barre||"Onglets";this.settingsSections.Barre=section;
    s.append(this.node("div",{class:"subtabs"},sections.map(label=>this.node("button",{class:"btn "+(label===section?"primary":""),onclick:()=>{this.settingsSections.Barre=label;this.render();}},label))));
    if(section!=="Onglets"){
      if(section==="Informations")s.append(this.node("p",{class:"inspector-note"},"Réseau et HA sont indépendants. Icône orange : Internet non confirmé par Android. Débit ↓ / ↑ : trafic total de la TV, réseau local compris, pas la vitesse maximale de la ligne. Aucun test ni téléchargement ; affichage uniquement sur l’accueil."));
      if(section==="Recherche vocale"){s.append(this.button("Ajouter le micro à la barre",async()=>{const items=this.editNavigation();if(!items.some(i=>i.type==="voice")){if(items.length>=40)throw Error("La barre contient déjà 40 onglets.");items.unshift({id:crypto.randomUUID(),type:"voice",label:"Recherche",icon:"mdi:microphone",display:1});this.mark();}}),this.node("p",{class:"notice"},"Assist utilise le compte de la TV et ses droits. La transcription utilise le service vocal Android installé, pas le moteur STT de HA. Aucune écoute permanente. Sans service vocal, la saisie texte reste disponible. Le troisième service ouvre une application vocale ou une adresse HTTPS avec {query}. Aucun jeton HA n’est transmis ; n’inscrivez pas de secret dans l’adresse."));}
      if(section==="Logo")s.append(this.node("p",{class:"inspector-note"},"TV Assistant : texte et couleur blanche fixes. Le logo reste accessible pour ouvrir les paramètres."));
      const grid=this.node("div",{class:"settings-grid grid"});
      for(const [key,r] of Object.entries(this.meta.schema).filter(([,r])=>r.group==="Barre"&&r.section===section)){
        const change=v=>{this.config.settings[key]=r.type==="integer"?Number(v):v;this.mark();};
        const control=r.type==="integer"&&!r.options?this.numberControl(r.label,this.setting(key),change,r.min,r.max):this.input(r.label,this.setting(key),change,{type:r.type==="boolean"?"checkbox":r.format==="color"?"color":"text",options:r.options});
        if(key==="voice_custom_package"){const dl=this.node("datalist",{id:"voice-apps"},this.profileApps().map(([pkg,label])=>this.node("option",{value:pkg},userText(label))));control.querySelector("input")?.setAttribute("list","voice-apps");control.append(dl);}
        const wrap=this.node("div",{class:"setting"},control);if(Object.hasOwn(this.config.settings,key))wrap.append(this.node("button",{class:"btn local-reset",title:"Revenir au réglage local de la TV","aria-label":this.t("Réglage local : ")+this.t(r.label),onclick:()=>{delete this.config.settings[key];this.mark();this.render();}},"↺"));grid.append(wrap);
      }s.append(grid);return;
    }
    s.append(this.node("div",{class:"inspector-note"},"🔒 TV Assistant · identité fixe"));
    const items=this.navigationItems();
    items.forEach((item,i)=>{
      const move=step=>{const list=this.editNavigation(),target=i+step;if(target<0||target>=list.length)return;[list[i],list[target]]=[list[target],list[i]];this.navEdit=target;this.mark();this.render();};
      s.append(this.node("div",{class:"nav-item"},
        this.node("button",{class:"btn choose "+(this.navEdit===i?"primary":""),onclick:()=>{this.navEdit=i;this.render();}},userText((item.enabled===false?"○ ":"")+item.label)),
        this.node("button",{class:"btn",disabled:i===0,"aria-label":"Monter "+item.label,onclick:()=>move(-1)},"↑"),
        this.node("button",{class:"btn",disabled:i===items.length-1,"aria-label":"Descendre "+item.label,onclick:()=>move(1)},"↓"),
        this.node("button",{class:"btn danger","aria-label":"Retirer "+item.label,onclick:()=>{this.editNavigation().splice(i,1);this.navEdit=-1;this.mark();this.render();}},"×")));
    });
    if(!items.length)s.append(this.node("p",{class:"muted"},"Aucun onglet. Le logo et la touche Menu donnent toujours accès aux paramètres."));
    const labels={home:"Pour vous",applications:"Applications",house:"Maison",tv_settings:"Paramètres TV",launcher_settings:"Home Assistant",voice:"Recherche vocale",app:"Application Android",ha_dashboard:"Tableau de bord HA"};
    const types=Object.keys(labels).filter(type=>type==="app"||type==="ha_dashboard"||!items.some(i=>i.type===type));
    if(items.length<40)s.append(this.input("Ajouter un onglet","",type=>{if(!type)return;const item={id:crypto.randomUUID(),type,label:labels[type],icon:type==="app"?"mdi:apps":type==="ha_dashboard"?"mdi:view-dashboard":"mdi:home"};if(type==="voice"){item.icon="mdi:microphone";item.display=1;}if(type==="app")item.package=this.profileApps()[0]?.[0]||"org.example.app";if(type==="ha_dashboard")item.path=this.setting("dashboard_web_path")||"/lovelace/0";const list=this.editNavigation();list.push(item);this.navEdit=list.length-1;this.mark();this.render();},{options:[["","Choisir une destination…"],...types.map(type=>[type,labels[type]])]}));
    const selected=items[this.navEdit];
    if(selected){
      const update=(key,v)=>{this.editNavigation()[this.navEdit][key]=v;this.mark();};
      s.append(this.node("h3",{},"Onglet sélectionné"),this.input("Libellé de l’onglet",selected.label,v=>update("label",v)),this.input("Onglet visible",selected.enabled!==false,v=>update("enabled",v),{type:"checkbox"}));
      if(selected.type==="app")s.append(this.input("Application de l’onglet",selected.package,v=>update("package",v),{rawOptions:true,options:this.profileApps().some(a=>a[0]===selected.package)?this.profileApps():[[selected.package,selected.package+" (non signalée)"],...this.profileApps()]}),this.input("Package Android",selected.package,v=>update("package",v)));
      if(selected.type==="ha_dashboard")s.append(this.input("Chemin HA de l’onglet",selected.path,v=>update("path",v)));
      s.append(this.input("Affichage de cet onglet",selected.display??-1,v=>{if(Number(v)<0)delete this.editNavigation()[this.navEdit].display;else update("display",Number(v));this.mark();},{options:[[-1,"Réglage commun"],[0,"Texte"],[1,"Icône"],[2,"Texte et icône"]]}),this.input("Icône de l’onglet (mdi:…)",selected.icon||"mdi:home",v=>update("icon",v)));
      const style=this.node("details",{},this.node("summary",{},"Style propre à cet onglet"));
      for(const [k,label,base,min,max] of [["text_size","Taille du texte","nav_text_size",10,40],["icon_size","Taille de l’icône","nav_icon_size",16,56],["color","Couleur du texte","nav_text_color"],["icon_color","Couleur de l’icône","nav_icon_color"]]){
        style.append(min?this.numberControl(label,selected[k]??this.setting(base),v=>update(k,v),min,max):this.input(label,selected[k]??this.setting(base),v=>update(k,v),{type:"color"}));
      }
      style.append(this.node("button",{class:"btn",onclick:()=>{const item=this.editNavigation()[this.navEdit];for(const k of ["display","text_size","icon_size","color","icon_color"])delete item[k];this.mark();this.render();}},"Reprendre le style commun"));s.append(style);
    }
    if(Object.hasOwn(this.config,"navigation"))s.append(this.node("button",{class:"btn",onclick:()=>{delete this.config.navigation;this.navEdit=-1;this.mark();this.render();}},"Revenir aux onglets locaux"));
    s.append(this.node("p",{class:"muted"},"Au-delà de la place disponible, les onglets défilent avec la télécommande. Une application doit être installée sur la TV."));
  }
  blockEntries(){return [["greeting","Salutation et sous-titre"],["quick","Actions rapides"],["continue","Reprise de lecture"],["favorites","Applications favorites"],["home","Cartes de la maison"]];}
  blockHeight(key){const start=Number(this.setting("section_title_size"))+25;return key==="greeting"?130:key==="quick"?66:key==="continue"?start+110:key==="favorites"?125+start+Number(this.setting("favorite_icon_size"))*1.04+Number(this.setting("app_label_size")):start+Number(this.setting("home_card_height"));}
  blockPlacement(key){const g=this.contentGeometry(),x=Number(this.setting("layout_"+key+"_x")),y=Number(this.setting("layout_"+key+"_y")),height=this.blockHeight(key),scale=Math.max(.1,Math.min(Number(this.setting("layout_"+key+"_scale"))/100,(g.h*(1-y/100)-6)/height));return {x,y,width:Math.max(1,Math.min(g.w*Number(this.setting("layout_"+key+"_width"))/100,g.w*(1-x/100)-6))/g.w*100,height:height*scale/g.h*100};}
  changeBlock(key,initial,dx,dy,resize){
    const set=(suffix,value)=>{const k="layout_"+key+"_"+suffix,r=this.meta.schema[k];this.config.settings[k]=Math.max(r.min,Math.min(r.max,Math.round(value)));};
    if(resize){set("width",Math.min(100-initial.x,initial.width+dx));set("scale",(initial.height+dy)*this.contentGeometry().h/this.blockHeight(key));}
    else{set("x",Math.min(100-initial.width,initial.x+dx));set("y",Math.min(100-initial.height,initial.y+dy));}
  }
  paintBlockFrames(overlay){
    const edge=Number(this.setting("nav_position")),scale=Number(this.setting("nav_scale"))/100,width=edge<2?Number(this.setting("nav_width")):320*scale/1920*100,height=edge<2?118*scale/1080*100:100;
    const bar=this.node("div",{class:"placement-frame bar-frame",role:"button",tabindex:0,"aria-label":"Régler la barre de navigation",style:{left:(edge<2?(100-width)*Number(this.setting("nav_alignment"))/2:edge===3?100-width:0)+"%",top:(edge===1?100-height:0)+"%",width:width+"%",height:height+"%"}},this.node("span",{},"Barre de navigation · cliquer pour régler"));
    const open=()=>{this.tab="Barre";this.render();};bar.onclick=open;bar.onkeydown=e=>{if(e.key==="Enter"||e.key===" "){e.preventDefault();open();}};overlay.append(bar);if(this.tab!=="Disposition")return;
    for(const [key,label] of this.blockEntries()){
      if(this.setting("show_"+key)===false&&!(key==="greeting"&&this.setting("show_subtitle")!==false))continue;
      const f=this.node("div",{class:"placement-frame block-frame "+(this.selectedBlock===key?"active":""),tabindex:0,role:"button","aria-label":"Placer le bloc "+label},this.node("span",{},label),this.node("div",{class:"resize","aria-hidden":"true"}));
      this.frameStyle(f,this.blockPlacement(key));
      const select=()=>{this.selectedBlock=key;this.placementIndex=-1;this.settingsSections.Disposition=label;};
      f.addEventListener("keydown",e=>{const dirs={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};if(e.key==="Enter"){select();this.render();return;}if(!dirs[e.key])return;e.preventDefault();select();this.changeBlock(key,this.blockPlacement(key),...dirs[e.key],e.shiftKey);this.mark();this.render();this.shadowRoot.querySelector('[aria-label="Placer le bloc '+label+'"]')?.focus();});
      f.addEventListener("pointerdown",e=>{if(e.button!==0)return;e.preventDefault();this.stopPreview();this.dragging=true;select();const initial=this.blockPlacement(key),previous=clone(this.config.settings),x=e.clientX,y=e.clientY,g=this.contentGeometry(),bounds=overlay.getBoundingClientRect(),resize=e.target.classList.contains("resize");let moved=false;f.setPointerCapture(e.pointerId);
        const move=ev=>{moved=true;this.changeBlock(key,initial,Math.round((ev.clientX-x)/bounds.width*1920/g.w*100),Math.round((ev.clientY-y)/bounds.height*1080/g.h*100),resize);this.frameStyle(f,this.blockPlacement(key));};
        const finish=ev=>{if(ev.type==="pointercancel"){this.config.settings=previous;moved=false;}f.removeEventListener("pointermove",move);f.removeEventListener("pointerup",finish);f.removeEventListener("pointercancel",finish);this.dragging=false;if(moved)this.mark();this.render();this.schedulePreview();};
        f.addEventListener("pointermove",move);f.addEventListener("pointerup",finish);f.addEventListener("pointercancel",finish);
      });overlay.append(f);
    }
  }
  profileApps(){const apps=new Map();for(const d of this.meta.devices.filter(d=>d.profile===this.profile))for(const a of d.apps||[])apps.set(a.package,a.label);return [...apps].sort((a,b)=>a[1].localeCompare(b[1],this._hass?.locale?.language||this.language));}
  placeCard(c){c.placement={x:5,y:35,width:c.type==="app"?12:30,height:c.type==="app"?22:20};this.placementIndex=this.config.cards.indexOf(c);this.selectedBlock="";this.mark();}
  convertHomeCards(kind){
    const add=c=>{if(this.config.cards.some(old=>old.id===c.id))return;this.config.cards.push(c);this.placementIndex=this.config.cards.length-1;};
    const place=(key,width,height)=>{const b=this.blockPlacement(key),x=Math.round(b.x),y=Math.round(b.y);return {x,y,width:Math.max(1,Math.min(width,100-x)),height:Math.max(1,Math.min(height,100-y))};};
    if(kind==="greeting"){
      add({id:"home-greeting",type:"text",name:"{greeting}",content:this.setting("show_subtitle")!==false?this.setting("hero_subtitle"):"",show_icon:false,show_state:false,interactive:false,background_opacity:0,title_size:this.setting("greeting_size"),body_size:this.setting("subtitle_size"),tap_action:{action:"none"},placement:place("greeting",65,22)});
      this.config.settings.show_greeting=false;this.config.settings.show_subtitle=false;
    }else{
      for(const key of ["scene_movie","scene_off"])if(!this.setting(key))throw Error("Renseignez les deux scènes dans Rangées → Actions rapides avant la conversion.");
      const p=place("quick",23,12);
      for(const [i,key,name,icon] of [[0,"scene_movie","Soirée cinéma","mdi:play-circle"],[1,"scene_off","Tout éteindre","mdi:power"]]){
        const entity=this.setting(key);if(!entity)throw Error("Renseignez la scène dans Rangées → Actions rapides avant la conversion.");
        const x=Math.min(100-p.width,p.x+i*(p.width+1));
        add({id:"home-"+key,type:"button",name,entity,icon,show_state:false,interactive:true,tap_action:{action:"perform-action",perform_action:"scene.turn_on",target:{entity_id:entity},confirmation:true},placement:{...p,x}});
      }
      this.config.settings.show_quick=false;
    }
    this.selectedBlock="";this.settingsSections.Disposition="Éléments libres";this.mark();
  }
  renderPlacement(s){
    s.append(this.node("h2",{},"Éléments de Pour vous"));
    s.append(this.node("div",{class:"toolbar"},this.button("Salutation → carte",async()=>this.convertHomeCards("greeting")),this.button("Actions rapides → cartes",async()=>this.convertHomeCards("quick"))),this.node("p",{class:"muted"},"Convertit les blocs en cartes déplaçables dans le brouillon, sans lancer d’action. {greeting} suit automatiquement l’heure de la TV. Les cartes existantes ne sont pas écrasées."));
    const add=(type,readonly)=>{const c={id:crypto.randomUUID(),type,name:readonly?"Affichage":"Nouvelle carte",content:"",interactive:!readonly,show_icon:!readonly,tap_action:{action:readonly?"none":"more-info"}};this.config.cards.push(c);this.placeCard(c);};
    s.append(this.node("div",{class:"toolbar"},this.button("+ Carte native",async()=>add("button",false)),this.button("+ Vraie carte HA",async()=>{const c=this.newHaCard();this.config.cards.push(c);this.placeCard(c);this.editIndex=this.config.cards.length-1;this.tab="Cartes";this.stopPreview();}),this.button("+ Affichage seul",async()=>add("text",true))));
    const apps=this.profileApps();s.append(this.input("Ajouter une application", "",v=>{if(!v)return;const c={id:crypto.randomUUID(),type:"app",name:apps.find(a=>a[0]===v)?.[1]||v,package:v};this.config.cards.push(c);this.placeCard(c);this.render();},{options:[["",this.t("Choisir une application…")],...apps.map(([pkg,label])=>[pkg,userText(label)])]}));
    const unplaced=this.config.cards.map((c,i)=>[String(i),c.name||c.entity?userText(c.name||c.entity):"Carte"]).filter(([i])=>!this.config.cards[Number(i)].placement);
    if(unplaced.length)s.append(this.input("Placer une carte existante","",v=>{if(v!==""){this.placeCard(this.config.cards[Number(v)]);this.render();}},{options:[["","Choisir une carte…"],...unplaced]}));
    const placed=this.config.cards.flatMap((c,i)=>c.placement?[[String(i),c.name||c.entity?userText(c.name||c.entity):"Carte"]]:[]);if(placed.length)s.append(this.input("Élément sélectionné",String(this.placementIndex),v=>{this.placementIndex=Number(v);this.selectedBlock="";this.render();},{options:[["-1","Sélectionner dans l’écran…"],...placed]}));
    const c=this.config.cards[this.placementIndex];
    if(c?.placement){
      const change=(key,value)=>{c[key]=value;this.mark();},grid=this.node("div",{class:"grid"},this.input("Nom de l’élément",c.name||"",v=>change("name",v)),this.input("Élément visible",c.enabled!==false,v=>change("enabled",v),{type:"checkbox"}));
      for(const [k,label] of [["x","Position X (%)"],["y","Position Y (%)"],["width","Largeur (%)"],["height","Hauteur (%)"]])grid.append(this.numberControl(label,c.placement[k],v=>{const p=c.placement;if(k==="x"||k==="y")p[k]=Math.min(v,100-p[k==="x"?"width":"height"]);else p[k]=Math.min(v,100-p[k==="width"?"x":"y"]);this.mark();this.syncPlacementInputs(c);},k==="x"||k==="y"?0:1,k==="x"||k==="y"?100-c.placement[k==="x"?"width":"height"]:100-c.placement[k==="width"?"x":"y"]));
      grid.append(this.input("Cliquable à la télécommande",c.interactive!==false,v=>change("interactive",v),{type:"checkbox"}));
      if(c.type==="ha_card")grid.append(this.haSizingControl(c));
      if(c.type!=="app"&&c.type!=="ha_card")grid.append(this.input("Entité affichée",c.entity||"",v=>change("entity",v),{list:"hatv-entities"}),this.input("Texte affiché ({state}, {name}, {greeting})",c.content||"",v=>change("content",v),{multiline:true}),this.input("Afficher l’icône",c.show_icon!==false,v=>change("show_icon",v),{type:"checkbox"}),this.input("Fond de l’élément",c.background||"#19232C",v=>change("background",v),{type:"color"}),this.numberControl("Opacité de l’élément (%)",c.background_opacity??93,v=>change("background_opacity",v),0,100),this.numberControl("Taille du titre de l’élément",c.title_size??this.setting("card_title_size"),v=>change("title_size",v),8,100),this.numberControl("Taille du texte de l’élément",c.body_size??this.setting("card_state_size"),v=>change("body_size",v),8,100));
      s.append(this.node("section",{class:"card"},grid,this.node("div",{class:"toolbar"},this.button("Actions et détails",async()=>{this.editIndex=this.placementIndex;this.tab="Cartes";this.stopPreview();}),this.button("Dupliquer",async()=>{this.config.cards.push({...clone(c),id:crypto.randomUUID(),name:(c.name||"Élément")+" (copie)"});this.placementIndex=this.config.cards.length-1;this.mark();}),this.button("Au premier plan",async()=>{this.config.cards.splice(this.placementIndex,1);this.config.cards.push(c);this.placementIndex=this.config.cards.length-1;this.mark();}),this.button("Supprimer l’élément",async()=>{if(window.confirm(this.t("Supprimer cet élément du brouillon ?"))){this.config.cards.splice(this.placementIndex,1);this.placementIndex=-1;this.mark();}},"danger"))));
    }
    s.append(this.button("Masquer les blocs classiques dans le brouillon",async()=>{for(const k of ["show_greeting","show_subtitle","show_quick","show_continue","show_favorites","show_home"])this.config.settings[k]=false;this.mark();}),this.node("p",{class:"muted"},"Facultatif : masque seulement les blocs de Pour vous. Les pages Applications et Maison restent disponibles. Vous pouvez réactiver les blocs dans Apparence et Rangées."));
  }
  syncPlacementInputs(c){for(const [k,label]of [["x","Position X (%)"],["y","Position Y (%)"],["width","Largeur (%)"],["height","Hauteur (%)"]])for(const input of this.shadowRoot.querySelectorAll("input"))if(input.getAttribute("aria-label")===label||input.getAttribute("aria-label")===label+" — curseur"){input.value=c.placement[k];input.max=100-c.placement[k==="x"?"width":k==="y"?"height":k==="width"?"x":"y"];}}
  contentGeometry(){const edge=Number(this.setting("nav_position")||0),scale=Number(this.setting("nav_scale")||100)/100,n=(edge<2?118:320)*scale;return {x:edge===2?n:0,y:edge===0?n:0,w:1920-(edge>=2?n:0),h:1080-(edge<2?n:0)};}
  frameStyle(frame,p){const g=this.contentGeometry();Object.assign(frame.style,{left:(g.x+g.w*p.x/100)/1920*100+"%",top:(g.y+g.h*p.y/100)/1080*100+"%",width:g.w*p.width/1920+"%",height:g.h*p.height/1080+"%"});}
  paintPlacementOverlay(){
    if(this.dragging)return;const overlay=this.shadowRoot.querySelector(".placement-overlay");if(!overlay)return;overlay.replaceChildren();if(!["Disposition","Barre"].includes(this.tab)||!this.showPlacementFrames)return;this.paintBlockFrames(overlay);if(this.tab!=="Disposition")return;
    this.config.cards.forEach((c,i)=>{if(!c.placement||c.enabled===false)return;const f=this.node("div",{class:`placement-frame free-frame ${i===this.placementIndex&&!this.selectedBlock?"active":""}`,tabindex:0,role:"button","aria-label":"Placer "+(c.name||c.entity||"Carte")},this.node("span",{},c.name||c.entity?userText(c.name||c.entity):"Carte"),this.node("div",{class:"resize","aria-hidden":"true"}));this.frameStyle(f,c.placement);
      f.addEventListener("keydown",e=>{const dirs={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};if(e.key==="Enter"){e.preventDefault();this.placementIndex=i;this.selectedBlock="";this.settingsSections.Disposition="Éléments libres";this.render();return;}if(!dirs[e.key])return;e.preventDefault();this.movePlacement(c,dirs[e.key][0],dirs[e.key][1],e.shiftKey);this.placementIndex=i;this.selectedBlock="";this.settingsSections.Disposition="Éléments libres";this.mark();this.render();this.shadowRoot.querySelectorAll(".placement-frame.free-frame")[this.config.cards.filter((a,j)=>j<i&&a.placement&&a.enabled!==false).length]?.focus();});
      f.addEventListener("pointerdown",e=>{if(e.button!==0)return;e.preventDefault();this.stopPreview();this.dragging=true;this.placementIndex=i;this.selectedBlock="";const initial=clone(c.placement),x=e.clientX,y=e.clientY,g=this.contentGeometry(),bounds=overlay.getBoundingClientRect(),resize=e.target.classList.contains("resize");f.setPointerCapture(e.pointerId);f.classList.add("active");
        const move=ev=>{c.placement=clone(initial);this.movePlacement(c,Math.round((ev.clientX-x)/bounds.width*1920/g.w*100),Math.round((ev.clientY-y)/bounds.height*1080/g.h*100),resize);this.frameStyle(f,c.placement);};
        const finish=ev=>{if(ev.type==="pointercancel")c.placement=initial;f.removeEventListener("pointermove",move);f.removeEventListener("pointerup",finish);f.removeEventListener("pointercancel",finish);this.dragging=false;this.settingsSections.Disposition="Éléments libres";this.mark();this.render();this.schedulePreview();};
        f.addEventListener("pointermove",move);f.addEventListener("pointerup",finish);f.addEventListener("pointercancel",finish);
      });overlay.append(f);
    });
  }
  movePlacement(c,dx,dy,resize){const p=c.placement;if(resize){p.width=Math.max(1,Math.min(100-p.x,p.width+dx));p.height=Math.max(1,Math.min(100-p.y,p.height+dy));}else{p.x=Math.max(0,Math.min(100-p.width,p.x+dx));p.y=Math.max(0,Math.min(100-p.height,p.y+dy));}}
  renderPreview(s){
    const devices=this.meta.devices.filter(d=>d.profile===this.profile);
    if(!devices.some(d=>d.id===this.previewId)){this.previewId=(devices.find(d=>d.online)||devices[0])?.id||"";this.previewFrame=null;}
    const options=this.node("details",{class:"preview-options"},this.node("summary",{},"TV, actualisation et aides de placement"));const previewHead=this.node("div",{class:"top"},this.node("h2",{},"Écran TV"));s.append(previewHead,this.node("p",{id:"preview-draft",class:"notice"}));
    if(this.tab!=="Aperçu")previewHead.append(this.button("Appliquer et voir sur la TV",async()=>{await this.save();this.previewMessage="Envoi effectué ; attente de la prochaine capture.";this.schedulePreview(1500);},"primary"));
    if(this.tab==="Disposition"){const objects=this.node("div",{class:"screen-objects"});for(const [key,label] of this.blockEntries())objects.append(this.node("button",{class:"btn",onclick:()=>{this.selectedBlock=key;this.placementIndex=-1;this.settingsSections.Disposition=label;this.render();}},label));objects.append(this.node("button",{class:"btn",onclick:()=>{this.selectedBlock="";this.settingsSections.Disposition="Éléments libres";this.render();}},"+ Élément libre"));s.append(objects);}if(["Disposition","Barre"].includes(this.tab))options.append(this.input("Cadres de placement du brouillon",this.showPlacementFrames,v=>{this.showPlacementFrames=v;this.paintPlacementOverlay();},{type:"checkbox"}),this.node("p",{class:"muted"},"Glissez les cadres ; tirez le coin inférieur droit pour redimensionner. Flèches : déplacer de 1 %, Maj + flèches : redimensionner. Les cadres ne font pas partie de la capture."));
    if(!devices.length)s.append(this.node("p",{class:"muted"},"Aucune TV affectée : vous pouvez préparer le placement sans capture."));
    options.append(this.node("div",{class:"toolbar"},this.input("TV à afficher",this.previewId,v=>{this.stopPreview();this.previewId=v;this.previewFrame=null;this.previewMessage="";this.render();this.schedulePreview(0);},{options:devices.map(d=>[d.id,`${d.name}${d.online?"":" · hors ligne"}`])}),this.node("button",{class:"btn",onclick:()=>this.requestPreview()},"Actualiser la vue réelle"),this.input("Actualiser toutes les 5 secondes",this.previewAuto,v=>{this.previewAuto=v;this.stopPreview();if(v)this.schedulePreview(0);},{type:"checkbox"})));
    if(this.tab==="Aperçu")options.open=true;const area=this.node("section",{id:"real-preview",class:"card"});this.paintPreview(area);s.append(area,options);
  }
  stopPreview(){clearTimeout(this.previewTimer);this.previewGeneration++;this.previewBusy=false;}
  schedulePreview(delay=5000){clearTimeout(this.previewTimer);if(this.isConnected&&!document.hidden&&this.hasPreview()&&this.previewAuto)this.previewTimer=setTimeout(()=>this.requestPreview(),delay);}
  paintPreview(area){
    const frame=this.previewFrame;area.replaceChildren();
    area.append(this.node("p",{class:"muted",role:"status"},this.previewBusy?"Capture en cours…":this.previewMessage||(frame?"Vue réelle reçue":"En attente d’une capture réelle.")));
    if(frame)area.append(this.node("div",{class:"preview-stage"},this.node("img",{src:`data:image/jpeg;base64,${frame.image}`,alt:"Capture réelle du launcher",style:{display:"block",width:"100%",height:"auto",borderRadius:"12px",background:"#090b0e"}}),this.node("div",{class:"placement-overlay"})),this.node("p",{class:"muted"},`Dernière capture : ${new Date(frame.captured_at).toLocaleTimeString(this._hass?.locale?.language||this.language)} · profil ${frame.profile} · version ${frame.revision??"—"}${this.previewMessage?" · image précédente conservée":""}`));
    else area.append(this.node("div",{class:"preview-stage"},this.node("div",{class:"empty-stage"},"Plan de placement — aucune capture reçue"),this.node("div",{class:"placement-overlay"})));
  }
  fitPreview(){const stage=this.shadowRoot.querySelector(".preview-stage");if(!stage)return;const room=Math.max(140,window.innerHeight-stage.getBoundingClientRect().top-80);stage.style.width=window.innerWidth<=720?"100%":Math.min(stage.parentElement.clientWidth,room*16/9)+"px";}
  updatePreview(){const area=this.shadowRoot.querySelector("#real-preview");if(area)this.paintPreview(area);this.fitPreview();this.paintPlacementOverlay();}
  async requestPreview(){
    if(this.dragging||this.previewBusy||!this.hasPreview()||document.hidden||!this.isConnected||!this.previewId)return;
    clearTimeout(this.previewTimer);const generation=this.previewGeneration,id=this.previewId;this.previewBusy=true;this.previewMessage="";this.updatePreview();
    try{const frame=await this.ws("preview",{device_id:id});if(generation!==this.previewGeneration||id!==this.previewId)return;if(frame.device_id!==id||typeof frame.image!=="string"||frame.image.length>700000||!/^\/9j\/[A-Za-z0-9+/]*={0,2}$/.test(frame.image))throw Error("Capture reçue invalide");this.previewFrame=frame;}
    catch(e){if(generation===this.previewGeneration)this.previewMessage=e.message||String(e);}
    finally{if(generation===this.previewGeneration){this.previewBusy=false;this.updatePreview();this.schedulePreview();}}
  }
  renderConditions(s,c){
    const list=c.conditions||[],section=this.node("details",list.length?{open:""}:{},this.node("summary",{},"Conditions d’affichage"));
    section.append(this.node("p",{class:"muted"},"Toutes les conditions doivent être vraies. Hors connexion ou état inconnu : carte masquée, jamais supprimée. Les états HA sont exacts : on, off, playing…"));
    list.forEach((condition,i)=>section.append(this.node("div",{class:"card"},
      this.input("Entité de condition",condition.entity,v=>{condition.entity=v;this.mark();},{list:"hatv-entities"}),
      this.input("Comparaison",condition.operator,v=>{condition.operator=v;condition.value=v==="above"||v==="below"?0:"on";this.mark();this.render();},{options:[["state","Égal à"],["not_state","Différent de"],["above","Supérieur à"],["below","Inférieur à"]]}),
      this.input("Valeur de condition",condition.value,v=>{condition.value=v;this.mark();},{type:condition.operator==="above"||condition.operator==="below"?"number":"text"}),
      this.button("Retirer cette condition",async()=>{c.conditions.splice(i,1);if(!c.conditions.length)delete c.conditions;this.mark();}))));
    if(list.length<8)section.append(this.button("Ajouter une condition",async()=>{(c.conditions||=[]).push({entity:c.entity||"",operator:"state",value:"on"});this.mark();}));s.append(section);
  }
  renderCards(s){
    s.append(this.node("p",{class:"notice"},"Cartes natives ou vraies cartes Home Assistant : collez le YAML d’une carte HA pour conserver ses styles, animations et actions. Les ressources HACS doivent déjà être installées dans HA. Les cartes utilisent le jeton du compte TV non administrateur ; une connexion web séparée reste possible en secours. Placement dans Disposition → Éléments libres."));
    const split=this.node("div",{class:"split"}),list=this.node("section",{class:"card"}),editor=this.node("section",{class:"card"});
    list.append(this.node("h2",{},this.f("{count} carte(s)",{count:this.config.cards.length})),this.button("Ajouter une carte",async()=>{this.config.cards.push({id:crypto.randomUUID(),type:"button",name:"Nouvelle carte",show_state:true,tap_action:{action:"more-info"}});this.editIndex=this.config.cards.length-1;this.mark();},"primary"));
    list.append(this.button("Ajouter une vraie carte HA",async()=>{const c=this.newHaCard();this.config.cards.push(c);this.editIndex=this.config.cards.length-1;this.mark();},"primary"));
    this.config.cards.forEach((c,i)=>list.append(this.node("div",{class:"item"},this.node("span",{class:"grow"},c.name||c.entity?userText(c.name||c.entity):"Texte"),this.button("Modifier",async()=>{this.editIndex=i;}),this.button("↑",async()=>{if(i>0){[this.config.cards[i-1],this.config.cards[i]]=[c,this.config.cards[i-1]];this.editIndex=i-1;this.mark();}}),this.button("↓",async()=>{if(i+1<this.config.cards.length){[this.config.cards[i+1],this.config.cards[i]]=[c,this.config.cards[i+1]];this.editIndex=i+1;this.mark();}}))));
    const c=this.config.cards[this.editIndex];
    if(c){
      editor.append(this.node("h2",{},"Modifier la carte"));
      this.renderConditions(editor,c);
      const change=(k,v)=>{c[k]=v;this.mark();};
      if(c.type==="ha_card"){this.renderHaCard(editor,c);split.append(list,editor);s.append(split);return;}
      if(/^\s*type\s*:/m.test(c.content||""))editor.append(this.button("Importer ce YAML comme vraie carte HA",async()=>{await this.importHaCard(c,c.content);},"primary"));
      const grid=this.node("div",{class:"grid"},this.input("Titre",c.name||"",v=>change("name",v)),this.input("Type",c.type||"button",v=>{c.type=v;if(v!=="app")delete c.package;else{c.package=this.profileApps()[0]?.[0]||"";if(!c.placement)this.placeCard(c);}if(v!=="heat_pump")for(const k of ["frequency_entity","defrost_entity","pump_entity","heating_allowed_entity"])delete c[k];this.mark();this.render();},{options:[["button","Bouton"],["entity","Entité"],["text","Texte"],["heat_pump","Pompe à chaleur"],["app","Application Android"]]}),this.input("Entité",c.entity||"",v=>change("entity",v),{list:"hatv-entities"}),this.input("Icône mdi:…",c.icon||"",v=>change("icon",v)),this.input("Texte ({state}, {name}, {entity})",c.content||"",v=>change("content",v),{multiline:true}),this.input("Couleur #RRGGBB (facultatif)",c.color||"",v=>change("color",v)),this.input("Visible",c.enabled!==false,v=>change("enabled",v),{type:"checkbox"}),this.input("Afficher l’état",c.show_state!==false,v=>change("show_state",v),{type:"checkbox"}));
      grid.append(this.input("Cliquable",c.interactive!==false,v=>change("interactive",v),{type:"checkbox"}));
      if(c.type==="app")grid.append(this.input("Package de l’application",c.package||"",v=>change("package",v)));
      editor.append(this.button(c.placement?"Modifier le placement libre":"Placer sur Pour vous",async()=>{if(!c.placement)this.placeCard(c);this.placementIndex=this.editIndex;this.settingsSections.Disposition="Éléments libres";this.tab="Disposition";this.schedulePreview(0);}));
      if(c.type!=="app"&&c.placement)editor.append(this.button("Remettre dans la rangée Maison",async()=>{delete c.placement;this.mark();}));
      if(c.type==="heat_pump")for(const [k,l] of [["frequency_entity","Fréquence compresseur"],["defrost_entity","Dégivrage"],["pump_entity","Circulateur"],["heating_allowed_entity","Autorisation chauffage"]])grid.append(this.input(l,c[k]||"",v=>change(k,v),{list:"hatv-entities"}));
      const action=c.tap_action?.action||"more-info";
      grid.append(this.input("Action au clic",action,v=>{c.tap_action={action:v};this.mark();this.render();},{options:[["none","Aucune"],["more-info","Informations"],["toggle","Basculer"],["perform-action","Action Home Assistant"],["call-service","Service HA (ancien format)"],["navigate","Page du launcher"],["launch-app","Application Android"],["url","Lien externe"]]}));
      if(["perform-action","call-service"].includes(action)){
        grid.append(this.input("Action HA (ex. light.turn_on)",c.tap_action.perform_action||c.tap_action.service||"",v=>{c.tap_action.perform_action=v;this.mark();}));
        grid.append(this.input("Cible entity_id (facultatif)",[].concat(c.tap_action.target?.entity_id||[]).join(","),v=>{c.tap_action.target={...(c.tap_action.target||{}),entity_id:v.split(",").map(x=>x.trim()).filter(Boolean)};this.mark();},{list:"hatv-entities"}));
      }
      if(action==="navigate")grid.append(this.input("Destination",c.tap_action.navigation_path||"",v=>{c.tap_action.navigation_path=v;this.mark();},{options:[["","Choisir…"],["home","Accueil"],["dashboard","Maison"],["applications","Applications"]]}));
      if(action==="launch-app"||action==="url"){const k=action==="url"?"url_path":"package";grid.append(this.input(action==="url"?"Lien HTTPS":"Package Android",c.tap_action[k]||"",v=>{c.tap_action[k]=v;this.mark();}));}
      grid.append(this.input("Demander confirmation au clic",c.tap_action?.confirmation||false,v=>{c.tap_action={...(c.tap_action||{action:"more-info"}),confirmation:v};this.mark();},{type:"checkbox"}));
      editor.append(grid,this.node("div",{class:"toolbar"},this.button("Dupliquer",async()=>{this.config.cards.push({...clone(c),id:crypto.randomUUID(),name:(c.name||"Carte")+" (copie)"});this.editIndex=this.config.cards.length-1;this.mark();}),this.button("Supprimer",async()=>{if(window.confirm(this.t("Supprimer cette carte du brouillon ?"))){this.config.cards.splice(this.editIndex,1);this.editIndex=-1;this.mark();}},"danger")));
    }else editor.append(this.node("p",{},"Choisissez une carte à modifier, ou ajoutez-en une."));
    split.append(list,editor);s.append(split);
  }
  newHaCard(){return {id:crypto.randomUUID(),type:"ha_card",name:"Carte Home Assistant",interactive:true,ha_sizing:"zone",lovelace:{type:"markdown",content:"Votre carte Home Assistant"},source_yaml:"type: markdown\ncontent: Votre carte Home Assistant\n"};}
  async importHaCard(c,raw){
    const result=await this.ws("parse_card",{yaml:raw});
    const kept={id:c.id||crypto.randomUUID(),type:"ha_card",name:c.name||result.lovelace.name||"Carte HA",enabled:c.enabled!==false,interactive:c.interactive!==false,lovelace:result.lovelace,source_yaml:raw};
    if(c.placement)kept.placement=clone(c.placement);
    if(c.ha_sizing!==undefined)kept.ha_sizing=c.ha_sizing;
    for(const k of Object.keys(c))delete c[k];Object.assign(c,kept);
    this.haDrafts?.delete(c.id);this.mark();this.message="Carte HA validée. Enregistrez pour l’envoyer à la TV.";
  }
  haSizingControl(c){return this.input("Dimensionnement de la carte",c.ha_sizing||"yaml",v=>{c.ha_sizing=v;this.mark();},{options:[["zone","Adapter à la zone"],["yaml","Dimensions du YAML"]]});}
  renderHaCard(editor,c){
    this.haDrafts??=new Map();
    editor.append(this.input("Titre dans l’éditeur",c.name||"",v=>{c.name=v;this.mark();}),this.input("Visible",c.enabled!==false,v=>{c.enabled=v;this.mark();},{type:"checkbox"}),this.input("Interactive (sinon affichage seul)",c.interactive!==false,v=>{c.interactive=v;this.mark();},{type:"checkbox"}));
    editor.append(this.haSizingControl(c),this.node("p",{class:"muted"},"Adapter à la zone remplit l’emplacement. Si celui-ci est trop petit, texte et icônes diminuent ensemble, sans déformation. Le YAML original reste intact. Les cartes natives suivent déjà leur emplacement."));
    editor.append(this.node("p",{class:"muted"},"La carte conserve son vrai rendu HA. Ses couleurs, tailles et actions se règlent dans ce YAML. Aucune conversion en bouton natif. Les modèles JavaScript des cartes installées s’exécutent dans le navigateur HA authentifié : n’importez que du YAML de confiance."));
    const raw=this.haDrafts.get(c.id)??c.source_yaml??JSON.stringify(c.lovelace,null,2);
    editor.append(this.node("textarea",{class:"code",value:raw,spellcheck:false,"aria-label":"YAML de la carte Home Assistant",oninput:e=>{this.haDrafts.set(c.id,e.target.value);this.mark();}}));
    editor.append(this.button("Valider et appliquer le YAML de la carte",async()=>{await this.importHaCard(c,this.haDrafts.get(c.id)??raw);},"primary"));
    editor.append(this.node("div",{class:"toolbar"},this.button(c.placement?"Modifier le placement libre":"Placer sur Pour vous",async()=>{if(!c.placement)this.placeCard(c);this.placementIndex=this.editIndex;this.settingsSections.Disposition="Éléments libres";this.tab="Disposition";this.schedulePreview(0);}),this.button("Mettre dans la rangée Maison",async()=>{delete c.placement;this.mark();}),this.button("Dupliquer",async()=>{this.config.cards.push({...clone(c),id:crypto.randomUUID(),name:(c.name||"Carte HA")+" (copie)"});this.editIndex=this.config.cards.length-1;this.mark();}),this.button("Supprimer",async()=>{if(window.confirm(this.t("Supprimer cette carte du brouillon ?"))){this.haDrafts.delete(c.id);this.config.cards.splice(this.editIndex,1);this.editIndex=-1;this.mark();}},"danger")));
  }
  renderYaml(s){
    s.append(this.node("p",{class:"muted"},"Format natif schema: 1, settings, cards et navigation (onglets optionnels). Aucun jeton HA ou mot de passe. Le YAML et l’éditeur visuel partagent le même brouillon ; les clés inconnues sont refusées sans modifier la TV."));
    s.append(this.node("textarea",{class:"code",value:this.yaml,spellcheck:false,"aria-label":"Configuration YAML",oninput:e=>{this.yaml=e.target.value;this.yamlDirty=true;this.mark();}}));
    s.append(this.node("div",{class:"toolbar"},this.button("Valider le YAML",async()=>{const r=await this.ws("validate",{yaml:this.yaml});this.config=r.config;this.yaml=r.yaml;this.yamlDirty=false;this.dirty=true;this.message="YAML valide, prêt à envoyer.";}),this.button("Exporter le brouillon",async()=>{await this.applyYaml();const r=await this.ws("validate",{config:this.config});const url=URL.createObjectURL(new Blob([r.yaml],{type:"application/yaml"}));const a=this.node("a",{href:url,download:`home-assistant-tv-${this.profile}.yaml`});a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}),this.button("Restaurer la version précédente",async()=>{if(!window.confirm(this.t("Restaurer et envoyer la version précédente aux TV de ce profil ? Le brouillon actuel sera abandonné.")))return;await this.ws("restore",{profile:this.profile,revision:this.revision});await this.load(this.profile);await this.refresh();this.message="Version précédente restaurée et envoyée.";})));
  }
}
if(!customElements.get("home-assistant-tv-panel"))customElements.define("home-assistant-tv-panel",HomeAssistantTVPanel);
