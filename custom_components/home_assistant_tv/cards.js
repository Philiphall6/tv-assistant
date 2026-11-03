/* Persistent, authenticated HA card surface. No polling or periodic page reload. */
import {languageOf, translate} from "/home_assistant_tv/localize.js?v=0.7.1";
const VERSION = "0.7.1";
const loadedResources = new Map();
let helpersPromise;

function findDeep(root, selector) {
  const direct = root.querySelector(selector);
  if (direct) return direct;
  for (const el of root.querySelectorAll("*")) {
    const found = el.shadowRoot && findDeep(el.shadowRoot, selector);
    if (found) return found;
  }
  return null;
}

async function helpers() {
  if (!helpersPromise) helpersPromise = (async () => {
    if (!window.loadCardHelpers) {
      // Load HA's own Lovelace module WITHOUT mounting a dashboard or its cards.
      // This router hook is internal: guard it and fail visibly if a HA update changes it.
      const resolver = findDeep(document, "partial-panel-resolver");
      const route = Object.values(resolver?.routerOptions?.routes || {})
        .find(r => r?.tag === "ha-panel-lovelace" && typeof r.load === "function");
      if (!route) throw Error("Le moteur Lovelace de cette version HA n’est pas accessible. Ouvrez un tableau de bord HA puis réessayez.");
      await route.load();
    }
    if (!window.loadCardHelpers) throw Error("Moteur Lovelace indisponible");
    return window.loadCardHelpers();
  })().catch(e => { helpersPromise = null; throw e; });
  return helpersPromise;
}

async function resources(hass) {
  const entries = await hass.callWS({type: "lovelace/resources"});
  await Promise.all(entries.map(entry => {
    const url = new URL(entry.url, location.origin);
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password)
      throw Error("Ressource HA non prise en charge");
    const key = entry.type + ":" + url.href;
    if (!loadedResources.has(key)) {
      const promise = entry.type === "module" ? import(url.href) : new Promise((resolve, reject) => {
        const element = document.createElement(entry.type === "css" ? "link" : "script");
        if (entry.type === "css") { element.rel = "stylesheet"; element.href = url.href; }
        else if (entry.type === "js") element.src = url.href;
        else { reject(Error("Type de ressource HA inconnu : " + entry.type)); return; }
        element.onload = resolve; element.onerror = () => reject(Error("Ressource HA indisponible : " + url.pathname));
        document.head.append(element);
      });
      loadedResources.set(key, promise.catch(e => { loadedResources.delete(key); throw e; }));
    }
    return loadedResources.get(key);
  }));
}

// Reversible sizing. Small slots uniformly shrink the content, never stretch
// text/SVGs in one axis. Browser hit testing handles the same transform.
// The saved Lovelace YAML is untouched.
class ZoneSizing {
  constructor(element, slot, content) {
    this.element = element; this.slot = slot; this.content = content;
    this.styles = new Map(); this.enabled = false;
    this.observer = new MutationObserver(() => this.queue());
    this.resize = new ResizeObserver(() => this.queue());
  }
  set(enabled) {
    if (enabled === this.enabled) return;
    this.clear(); this.enabled = enabled;
    if (!enabled || !this.element) return;
    this.observer.observe(this.element, {childList:true, subtree:true});
    this.resize.observe(this.slot);
    this.queue();
    // Lit may attach its shadow root only on its first asynchronous update.
    Promise.resolve(this.element.updateComplete).then(() => this.queue(), () => {});
  }
  queue() {
    if (!this.enabled || this.frame) return;
    this.frame = requestAnimationFrame(() => { this.frame = 0; this.apply(); });
  }
  apply() {
    if (!this.enabled || !this.element.isConnected) return;
    const root = this.element.shadowRoot;
    if (!root) return;
    if (!this.natural) {
      // Measure the original card before applying any sizing override. This
      // happens within one animation frame, so the natural size is never painted.
      this.slot.classList.remove("zone");
      const rect = this.element.getBoundingClientRect(), card = findDeep(root, "ha-card");
      this.natural = {width:Math.max(1, rect.width, this.element.scrollWidth),
        height:Math.max(1, rect.height, this.element.scrollHeight, card?.scrollHeight || 0)};
      this.slot.classList.add("zone");
    }
    const scale = Math.min(1, this.slot.clientWidth / this.natural.width, this.slot.clientHeight / this.natural.height);
    this.content.style.setProperty("--hatv-content-scale", String(Math.max(.001, scale)));
    let style = this.styles.get(root);
    if (!style) {
      style = document.createElement("style");
      style.dataset.hatvZoneSizing = "";
      style.textContent = `
        :host { display:block!important; width:100%!important; height:100%!important;
          min-width:0!important; min-height:0!important; max-width:none!important;
          max-height:none!important; margin:0!important; box-sizing:border-box!important; }
        ha-card { width:100%!important; height:100%!important; min-width:0!important;
          min-height:0!important; max-width:none!important; max-height:none!important;
          margin:0!important; box-sizing:border-box!important; }
      `;
      this.styles.set(root, style);
      this.observer.observe(root, {childList:true, subtree:true});
    }
    if (style.parentNode !== root) root.append(style);
  }
  clear() {
    this.enabled = false; this.observer.disconnect(); this.resize.disconnect();
    if (this.frame) cancelAnimationFrame(this.frame);
    this.frame = 0;
    for (const style of this.styles.values()) style.remove();
    this.styles.clear(); this.natural = null;
    this.content.style.removeProperty("--hatv-content-scale");
  }
}

class HomeAssistantTvCards extends HTMLElement {
  constructor() {
    super(); this.attachShadow({mode: "open"}); this.nodes = new Map(); this.cards = [];
    this.geometry = []; this.generation = 0; this.status = {ready: false, rendered: 0, version: VERSION};
    this.shadowRoot.innerHTML = `<style>
      :host{display:block;position:fixed;inset:0;z-index:1;color:var(--primary-text-color);background:transparent!important}
      .stage{position:absolute;inset:0;overflow:hidden;pointer-events:none}
      .slot{position:absolute;box-sizing:border-box;overflow:auto;pointer-events:auto;scrollbar-width:none}
      .slot.zone>.card-content{width:calc(100% / var(--hatv-content-scale,1));height:calc(100% / var(--hatv-content-scale,1));transform:scale(var(--hatv-content-scale,1));transform-origin:top left}
      .slot.zone>.card-content> :not(.error){display:block;width:100%!important;height:100%!important;min-width:0!important;min-height:0!important;max-width:none!important;max-height:none!important;box-sizing:border-box!important;margin:0!important}
      .slot.readonly{pointer-events:none}
      .error{font:14px sans-serif;color:#fff;background:#452d32;border:1px solid #e78989;border-radius:12px;padding:12px;line-height:1.4}
      .notice{position:absolute;top:12px;left:12px;color:white;background:#18232c;border-radius:10px;padding:12px;font:14px sans-serif}
    </style><div class="stage"></div>`;
    this.stage = this.shadowRoot.querySelector(".stage");
    this.rebuild = () => this.renderCards();
    this.addEventListener("ll-rebuild", this.rebuild);
  }
  set hass(value) {
    this._hass = value;
    for (const {element} of this.nodes.values()) if (element) element.hass = value;
    if (this.isConnected) this.start();
  }
  get hass() { return this._hass; }
  set route(value) { this._route = value; if (this.isConnected) this.start(); }
  connectedCallback() {
    window.hatvCards = {
      status: this.status,
      layout: positions => { if (!Array.isArray(positions)) return; this.geometry = positions; this.renderCards(); },
      retry: () => { this.engine = null; this.renderCards(); },
    };
    this.removeChrome(); this.start();
  }
  disconnectedCallback() {
    this.generation++; this.subscription?.then(unsubscribe => unsubscribe()).catch(() => {});
    this.subscription = null; this.device = null;
    for (const restore of [...(this.restores || [])].reverse()) restore();
    for (const record of this.nodes.values()) record.sizing.clear();
    this.nodes.clear();
    if (window.hatvCards?.status === this.status) delete window.hatvCards;
  }
  removeChrome() {
    // Scoped to this hidden route, reversible, no saved theme/sidebar changes.
    this.restores = [];
    const style = (el, property, value) => {
      if (!el) return;
      const old = el.style.getPropertyValue(property), priority = el.style.getPropertyPriority(property);
      el.style.setProperty(property, value, "important");
      this.restores.push(() => old ? el.style.setProperty(property, old, priority) : el.style.removeProperty(property));
    };
    for (const el of [document.documentElement, document.body]) style(el, "background", "transparent");
    let el = this;
    while (el) {
      style(el, "background", "transparent"); style(el, "--ha-panel-background", "transparent");
      style(el, "--ha-sidebar-width", "0px");
      if (el.shadowRoot) {
        style(el.shadowRoot.querySelector("ha-sidebar"), "display", "none");
        const drawer = el.shadowRoot.querySelector("ha-drawer");
        if (drawer) { const oldType = drawer.type; drawer.type = "modal"; this.restores.push(() => drawer.type = oldType); }
      }
      el = el.parentElement || el.getRootNode()?.host;
    }
  }
  start() {
    const device = (this._route?.path || "").split("/").filter(Boolean)[0];
    if (!this._hass || !/^[A-Za-z0-9_-]{1,40}$/.test(device || "") || this.device === device) return;
    this.subscription?.then(unsubscribe => unsubscribe()).catch(() => {});
    this.device = device; const generation = ++this.generation;
    this.received = false;
    this.subscription = this._hass.connection.subscribeMessage(snapshot => {
      if (generation !== this.generation) return;
      this.received = true;
      this.cards = snapshot.cards; this.status.revision = snapshot.revision;
      this.status.profile = snapshot.profile; this.renderCards();
    }, {type: "home_assistant_tv/render_subscribe", device_id: device});
    this.subscription.catch(e => { if (generation === this.generation) { this.device = null; this.fail(e); } });
  }
  fail(error) {
    this.status.ready = false; this.status.rendered = 0; this.status.error = String(error.message || error);
    for (const record of this.nodes.values()) record.sizing.clear();
    this.stage.replaceChildren(); this.nodes.clear();
    const notice = document.createElement("div"); notice.className = "notice error";
    notice.textContent = translate(this.status.error, languageOf(this._hass)); this.stage.append(notice);
  }
  async renderCards() {
    if (!this._hass || !this.cards || !this.isConnected || !this.received) return;
    const visible = this.geometry.filter(g => this.cards.some(c => c.id === g.id));
    const ids = new Set(visible.map(g => g.id));
    for (const [id, node] of this.nodes) if (!ids.has(id)) { node.sizing.clear(); node.slot.remove(); this.nodes.delete(id); }
    if (!visible.length) { this.status.rendered = 0; this.status.ready = true; delete this.status.error; return; }
    this.status.ready = false;
    try {
      if (!this.engine) this.engine = Promise.all([helpers(), resources(this._hass)]).then(([helper]) => helper)
        .catch(e => { this.engine = null; throw e; });
      const helper = await this.engine;
      if (!this.isConnected) return;
      // Use the current geometry/config after asynchronous module loading, never a stale snapshot.
      for (const g of this.geometry) {
        const card = this.cards.find(c => c.id === g.id); if (!card) continue;
        if (![g.x, g.y, g.width, g.height].every(Number.isFinite)) continue;
        const config = JSON.stringify(card.lovelace);
        let record = this.nodes.get(card.id);
        if (!record || record.config !== config) {
          record?.sizing.clear(); record?.slot.remove();
          const slot = document.createElement("div"); slot.className = "slot";
          const content = document.createElement("div"); content.className = "card-content"; slot.append(content);
          let element;
          try { element = helper.createCardElement(card.lovelace); element.hass = this._hass; content.append(element); }
          catch (e) { const err = document.createElement("div"); err.className = "error"; err.textContent = "Carte HA : " + e.message; content.append(err); }
          record = {slot, element, config, sizing:new ZoneSizing(element,slot,content)}; this.nodes.set(card.id, record); this.stage.append(slot);
        }
        record.slot.classList.toggle("readonly", card.interactive === false);
        record.slot.classList.toggle("zone", card.ha_sizing === "zone");
        record.sizing.set(card.ha_sizing === "zone");
        Object.assign(record.slot.style, {left:g.x+"%", top:g.y+"%", width:g.width+"%", height:g.height+"%"});
      }
      this.status.rendered = this.nodes.size; this.status.ready = true; delete this.status.error;
    } catch (e) { this.fail(e); }
  }
}
if(!customElements.get("home-assistant-tv-cards"))customElements.define("home-assistant-tv-cards", HomeAssistantTvCards);
