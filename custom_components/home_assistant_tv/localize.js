import {EN} from "./ui-catalog.js?v=0.8.1";

// User data is explicitly marked at the rendering boundary, never translated.
export const raw = value => Object.freeze({literal: String(value ?? "")});
export const isRaw = value => value && typeof value === "object" && Object.hasOwn(value, "literal");
export function languageOf(hass) {
  const language = hass?.locale?.language || hass?.language || document.documentElement.lang || navigator.language || "en";
  return String(language).toLowerCase().replaceAll("_", "-").split("-")[0] === "fr" ? "fr" : "en";
}
export function translate(source, language) {
  if (isRaw(source)) return source.literal;
  if (typeof source !== "string" || language === "fr") return source;
  return Object.hasOwn(EN, source) ? EN[source] : source;
}
