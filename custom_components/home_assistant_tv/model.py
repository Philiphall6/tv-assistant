"""Data-only profile validation; no templates, Python tags or JavaScript execution."""
import copy
import json
import math
import re
from pathlib import Path
from urllib.parse import urlsplit

import yaml
from .navigation import validate_navigation

MAX_BYTES = 262144
SCHEMA = json.loads(Path(__file__).with_name("schema.json").read_text())
LAYOUT_PRESETS = json.loads(Path(__file__).with_name("layout_presets.json").read_text())
ENTITY = r"[a-z0-9_]+\.[a-z0-9_]+"
PACKAGE = r"[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+"
PAC_KEYS = {"frequency_entity", "defrost_entity", "pump_entity", "heating_allowed_entity"}
CARD_KEYS = {"id", "type", "name", "entity", "icon", "content", "color", "enabled", "show_state", "tap_action", "hold_action", "actions", "animate", "placement", "interactive", "package", "show_icon", "background", "background_opacity", "title_size", "body_size", "lovelace", "source_yaml", "ha_sizing"} | PAC_KEYS
ACTION_KEYS = {"action", "name", "perform_action", "service", "data", "target", "confirmation", "navigation_path", "url_path", "package"}
CARD_KEYS.add("conditions")


class ProfileError(ValueError):
    pass


def require(ok, message):
    if not ok:
        raise ProfileError(message)


def keys(value, allowed, location):
    require(isinstance(value, dict), f"{location} : objet attendu")
    require(all(isinstance(k, str) for k in value), f"{location} : clés texte attendues")
    unknown = set(value) - set(allowed)
    require(not unknown, f"{location} : options inconnues {', '.join(sorted(unknown))}")


def data_only(value, depth=0):
    require(depth <= 20, "Configuration trop imbriquée")
    if isinstance(value, dict):
        require(all(isinstance(k, str) for k in value), "Les clés doivent être du texte")
        for v in value.values():
            data_only(v, depth + 1)
    elif isinstance(value, list):
        for v in value:
            data_only(v, depth + 1)
    else:
        require(value is None or isinstance(value, (str, bool, int, float)), "Valeur non prise en charge")
        if isinstance(value, (int, float)):
            require(math.isfinite(value), "Nombre non fini")


class StrictLoader(yaml.SafeLoader):
    def compose_node(self, parent, index):
        require(not self.check_event(yaml.AliasEvent), "Les alias YAML ne sont pas acceptés")
        depth = getattr(self, "profile_depth", 0)
        require(depth <= 20, "Configuration trop imbriquée")
        self.profile_depth = depth + 1
        try:
            return super().compose_node(parent, index)
        finally:
            self.profile_depth = depth

    def construct_mapping(self, node, deep=False):
        result = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            require(isinstance(key, str), "Les clés doivent être du texte")
            require(key not in result, f"Clé en double : {key}")
            result[key] = self.construct_object(value_node, deep=deep)
        return result


def parse_yaml(raw):
    require(isinstance(raw, str) and len(raw.encode()) <= MAX_BYTES, "YAML limité à 256 Ko")
    try:
        return validate(yaml.load(raw, Loader=StrictLoader))
    except (yaml.YAMLError, RecursionError) as err:
        raise ProfileError("YAML invalide : vérifiez les types et l’indentation") from err


def validate_lovelace(value):
    """Keep frontend configuration as data. Only HA's frontend evaluates its templates."""
    data_only(value)
    require(isinstance(value, dict) and isinstance(value.get("type"), str) and
            0 < len(value["type"]) <= 200, "Carte HA : type Lovelace requis")
    require(len(json.dumps(value, ensure_ascii=False).encode()) <= MAX_BYTES, "Carte HA limitée à 256 Ko")
    return value


def parse_card_yaml(raw):
    require(isinstance(raw, str) and len(raw.encode()) <= MAX_BYTES, "YAML limité à 256 Ko")
    try:
        return validate_lovelace(yaml.load(raw, Loader=StrictLoader))
    except (yaml.YAMLError, RecursionError) as err:
        raise ProfileError("YAML de carte invalide : vérifiez les types et l’indentation") from err


def album_url(value):
    if not value:
        return
    u = urlsplit(value)
    require(u.scheme == "https" and u.port in (None, 443) and not u.username and not u.password and not u.fragment,
            "Album : lien HTTPS sans identifiants attendu")
    require((u.hostname == "photos.app.goo.gl" and re.fullmatch(r"/[A-Za-z0-9_-]+/?", u.path)) or
            (u.hostname == "photos.google.com" and re.fullmatch(r"/share/[A-Za-z0-9_-]+/?", u.path)), "Lien d’album Google Photos partagé attendu")


def validate_action(action, entity):
    keys(action, ACTION_KEYS, "Action")
    kind = action.get("action", "none")
    require(kind in ("none", "more-info", "toggle", "perform-action", "call-service", "navigate", "url", "launch-app"), "Action non native")
    for key in ACTION_KEYS - {"data", "target", "confirmation"}:
        if key in action:
            require(isinstance(action[key], str), f"{key} : texte attendu")
    if "confirmation" in action:
        require(type(action["confirmation"]) is bool, "confirmation : booléen attendu")
    if kind == "toggle":
        require(bool(entity), "toggle nécessite une entité")
    if kind in ("perform-action", "call-service"):
        require(re.fullmatch(ENTITY, action.get("perform_action", action.get("service", ""))), "Service domaine.action attendu")
    if "data" in action:
        require(isinstance(action["data"], dict), "data : objet attendu")
    if "target" in action:
        keys(action["target"], {"entity_id", "device_id", "area_id", "floor_id", "label_id"}, "target")
        for value in action["target"].values():
            require(isinstance(value, str) or isinstance(value, list) and all(isinstance(v, str) for v in value), "Cible : texte ou liste de textes")
    if kind == "navigate":
        require(action.get("navigation_path") in ("home", "dashboard", "applications"), "Destination native inconnue")
    if kind == "launch-app":
        require(re.fullmatch(PACKAGE, action.get("package", "")), "Package Android invalide")
    if kind == "url":
        u = urlsplit(action.get("url_path", ""))
        require(u.scheme in ("http", "https") and u.hostname and not u.username and not u.password, "Lien HTTP(S) sans identifiants attendu")


def validate_card(card):
    if "conditions" in card:
        conditions=card["conditions"]
        require(isinstance(conditions,list) and len(conditions)<=8,"conditions : 8 maximum")
        for condition in conditions:
            keys(condition,{"entity","operator","value"},"Condition")
            require(isinstance(condition.get("entity"),str) and re.fullmatch(ENTITY,condition["entity"]),"Entité de condition invalide")
            op=condition.get("operator");value=condition.get("value")
            require(op in ("state","not_state","above","below"),"Opérateur de condition invalide")
            require((isinstance(value,str) and 0<len(value)<=255) if op in ("state","not_state") else (type(value) in (int,float) and math.isfinite(value)),"Valeur de condition invalide")
    keys(card, CARD_KEYS, "Carte")
    kind = card.get("type", "button")
    require(kind in ("button", "entity", "text", "heat_pump", "app", "ha_card"), "Type inconnu : utilisez ha_card pour une vraie carte Home Assistant")
    if kind == "ha_card":
        require(card.get("ha_sizing", "yaml") in ("yaml", "zone"), "Dimensionnement HA : yaml ou zone attendu")
        require(isinstance(card.get("id"), str) and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", card["id"]), "Carte HA : identifiant requis")
        validate_lovelace(card.get("lovelace"))
        require(not ({"tap_action", "hold_action", "actions"} & set(card)), "Définissez les actions dans lovelace pour une carte HA")
        if "source_yaml" in card:
            require(isinstance(card["source_yaml"], str), "source_yaml : texte attendu")
            require(parse_card_yaml(card["source_yaml"]) == card["lovelace"], "Le YAML source et la carte HA ne correspondent pas")
    else:
        require(not ({"lovelace", "source_yaml", "ha_sizing"} & set(card)), "lovelace, source_yaml et ha_sizing sont réservés au type ha_card")
    for k in ("id", "name", "entity", "icon", "content", "color", "package", "background"):
        if k in card:
            require(isinstance(card[k], str), f"Carte {k} : texte attendu")
    for k in ("enabled", "show_state", "animate", "interactive", "show_icon"):
        if k in card:
            require(type(card[k]) is bool, f"Carte {k} : booléen attendu")
    entity = card.get("entity", "")
    require(not entity or re.fullmatch(ENTITY, entity), "Entité invalide")
    require(kind == "ha_card" or card.get("name") or entity or card.get("content"), "Carte : ajoutez un nom, une entité ou du texte")
    require(not card.get("color") or re.fullmatch(r"#[0-9a-fA-F]{6}", card["color"]), "Couleur '#RRGGBB' attendue")
    require(not card.get("background") or re.fullmatch(r"#[0-9a-fA-F]{6}", card["background"]), "Fond '#RRGGBB' attendu")
    for key, low, high in (("background_opacity", 0, 100), ("title_size", 8, 100), ("body_size", 8, 100)):
        if key in card:
            require(type(card[key]) is int and low <= card[key] <= high, f"{key} : entier entre {low} et {high}")
    if kind == "app":
        require(bool(re.fullmatch(PACKAGE, card.get("package", ""))), "Application : package Android requis")
        require("placement" in card, "Application : placement libre requis")
    else:
        require("package" not in card, "package est réservé au type app")
    if "placement" in card:
        place = card["placement"]
        keys(place, {"x", "y", "width", "height"}, "Placement")
        for key in ("x", "y", "width", "height"):
            low, high = (0, 99) if key in ("x", "y") else (1, 100)
            require(type(place.get(key)) is int and low <= place[key] <= high, f"Placement {key} : entier entre {low} et {high}")
        require(place["x"] + place["width"] <= 100 and place["y"] + place["height"] <= 100, "Le placement doit rester dans la zone de contenu")
    if kind == "heat_pump":
        require(entity, "PAC : entité de priorité requise")
        for k in PAC_KEYS:
            require(isinstance(card.get(k), str) and re.fullmatch(ENTITY, card[k]), f"PAC : {k} invalide")
    else:
        require(not (PAC_KEYS & set(card)), "Capteurs PAC réservés au type heat_pump")
    for k in ("tap_action", "hold_action"):
        if k in card:
            validate_action(card[k], entity)
    if "actions" in card:
        require(isinstance(card["actions"], list), "actions : liste attendue")
        for a in card["actions"]:
            validate_action(a, entity)


def validate(profile):
    data_only(profile)
    require(len(json.dumps(profile, ensure_ascii=False).encode()) <= MAX_BYTES, "Profil limité à 256 Ko")
    keys(profile, {"schema", "settings", "cards", "navigation"}, "Profil")
    require(type(profile.get("schema")) is int and profile["schema"] == 1, "schema: 1 attendu")
    settings = profile.get("settings", {})
    keys(settings, SCHEMA, "Réglages")
    for k, value in settings.items():
        rule = SCHEMA[k]
        kind = rule["type"]
        if kind == "boolean":
            require(type(value) is bool, f"{k} : booléen attendu")
        elif kind == "integer":
            require(type(value) is int and rule["min"] <= value <= rule["max"], f"{k} : entier entre {rule['min']} et {rule['max']}")
        else:
            require(isinstance(value, str) and len(value) <= rule["max"], f"{k} : texte trop long ou invalide")
            fmt = rule.get("format")
            if fmt == "dashboard_path":
                require(bool(re.fullmatch(r"/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*/?", value)), "Chemin HA local attendu, ex. /dashboard-tv/tv")
            if fmt == "color":
                require(bool(re.fullmatch(r"#[0-9a-fA-F]{6}", value)), "Couleur attendue : #RRGGBB")
            if fmt == "entity":
                require(not value or re.fullmatch(ENTITY, value), f"{k} : entité invalide")
            if fmt in ("entities", "packages"):
                lines = value.splitlines()
                require(all(re.fullmatch(ENTITY if fmt == "entities" else PACKAGE, s) for s in lines), f"{k} : un identifiant valide par ligne, sans espaces")
                require(len(lines) == len(set(lines)), f"{k} : doublons")
            if fmt == "album":
                album_url(value)
            if fmt == "voice_package":
                require(not value or (re.fullmatch(PACKAGE, value) and value != "fr.maison.tvlauncher"), "Paquet de l’application vocale invalide")
            if fmt == "voice_url" and value:
                try:
                    uri = urlsplit(value.replace("{query}", "hatvquery"))
                    valid = (uri.scheme == "https" and value.startswith("https://") and uri.hostname and not uri.username and not uri.password
                             and not uri.fragment and "#" not in value and "@" not in uri.netloc and "{query}" in value and "hatvquery" not in uri.netloc
                             and not any(ord(c) <= 32 or ord(c) == 127 for c in value) and "\\" not in value
                             and "{" not in value.replace("{query}", "") and "}" not in value.replace("{query}", "")
                             and (uri.port is None or 0 <= uri.port <= 65535))
                except ValueError:
                    valid = False
                require(valid, "Adresse HTTPS avec {query} dans le chemin ou la recherche, sans identifiant ni fragment")
    cards = profile.get("cards", [])
    require(isinstance(cards, list), "cards : liste attendue")
    ids = set()
    for card in cards:
        validate_card(card)
        if card.get("id"):
            require(card["id"] not in ids, "Identifiant de carte en double")
            ids.add(card["id"])
    out = {"schema": 1, "settings": settings, "cards": cards}
    if "navigation" in profile:
        validate_navigation(profile["navigation"], require, keys)
        out["navigation"] = profile["navigation"]
    return copy.deepcopy(out)


def profile_id(value):
    require(isinstance(value, str) and re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,39}", value), "Identifiant : 1 à 40 lettres minuscules, chiffres, tirets")
    return value


def as_yaml(profile):
    return yaml.safe_dump(profile, allow_unicode=True, sort_keys=False)
