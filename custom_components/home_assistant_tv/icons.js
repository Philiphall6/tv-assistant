/* TV Assistant sidebar mark. Local, theme-aware, no network or user data.
 * Monochrome TV with the connected branch cut out, matching the packaged logo.
 * A single nonzero-winding path works with Home Assistant's native ha-svg-icon.
 */
const logo = {
  viewBox: "0 0 24 24",
  path: "M4 3H20Q23 3 23 6V16Q23 19 20 19H4Q1 19 1 16V6Q1 3 4 3Z " +
    "M4 20H7L6 22H2.5Q2 22 2.3 21.5Z M17 20H20L21.7 21.5Q22 22 21.5 22H18Z " +
    "M11.45 17.5H12.55V13.6L15.65 10.65A1.3 1.3 0 1 0 14.75 9.75L12.55 11.9V7.67" +
    "A1.3 1.3 0 1 0 11.45 7.67V14.05L9.25 11.85A1.3 1.3 0 1 0 8.15 12.95L11.45 16.25Z",
};

window.customIcons = window.customIcons || {};
window.customIcons["tv-assistant"] = {
  getIcon: async name => name === "logo" ? {...logo} : {path: ""},
  getIconList: async () => [{name: "logo", keywords: ["tv", "assistant", "television"]}],
};

// HA can render the sidebar before extra modules finish loading. In that case
// ha-icon keeps its legacy (iron-icon) renderer even after the set is registered.
// Refresh only our already-rendered icons, using the public icon property. Do not
// patch HA prototypes, other icon sets, user themes, or install a persistent observer.
customElements.whenDefined("ha-icon").then(() => {
  const visit = root => {
    for (const element of root.querySelectorAll("*")) {
      if (element.localName === "ha-icon" && element.icon === "tv-assistant:logo"
          && element.shadowRoot?.querySelector("iron-icon")) {
        element.icon = "mdi:television";
        Promise.resolve(element.updateComplete).then(() => {
          if (element.isConnected && element.icon === "mdi:television") element.icon = "tv-assistant:logo";
        });
      } else if (element.shadowRoot) visit(element.shadowRoot);
    }
  };
  visit(document);
});
