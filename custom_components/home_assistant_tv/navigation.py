"""Data-only navigation, independent of HA services and branding."""
import re

TYPES = {"home", "applications", "house", "tv_settings", "launcher_settings", "voice", "app", "ha_dashboard"}


def validate_navigation(items, require, keys):
    require(isinstance(items, list) and len(items) <= 40, "navigation : liste de 0 à 40 onglets")
    ids, builtins = set(), set()
    for item in items:
        keys(item, {"id", "type", "label", "icon", "color", "icon_color", "text_size", "icon_size", "display", "enabled", "package", "path"}, "Onglet")
        identifier, kind = item.get("id"), item.get("type")
        require(isinstance(identifier, str) and re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,39}", identifier), "Identifiant d’onglet invalide")
        require(identifier not in ids, "Identifiant d’onglet en double"); ids.add(identifier)
        require(isinstance(kind, str) and kind in TYPES, "Destination d’onglet inconnue")
        if kind not in {"app", "ha_dashboard"}:
            require(kind not in builtins, "Destination native en double"); builtins.add(kind)
        require(isinstance(item.get("label"), str) and bool(item["label"].strip()) and len(item["label"]) <= 60, "Libellé d’onglet : 1 à 60 caractères")
        if "enabled" in item: require(type(item["enabled"]) is bool, "enabled : booléen attendu")
        if "icon" in item: require(isinstance(item["icon"], str) and re.fullmatch(r"mdi:[a-z0-9-]{1,80}", item["icon"]), "Icône mdi:… attendue")
        for key in ("color", "icon_color"):
            if key in item: require(isinstance(item[key], str) and re.fullmatch(r"#[0-9a-fA-F]{6}", item[key]), "Couleur #RRGGBB attendue")
        for key, low, high in (("text_size", 10, 40), ("icon_size", 16, 56), ("display", 0, 2)):
            if key in item: require(type(item[key]) is int and low <= item[key] <= high, f"{key} : entier entre {low} et {high}")
        if kind == "app": require(isinstance(item.get("package"), str) and re.fullmatch(r"[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+", item["package"]), "Package Android requis")
        else: require("package" not in item, "package est réservé à app")
        if kind == "ha_dashboard": require(isinstance(item.get("path"), str) and re.fullmatch(r"/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*/?", item["path"]), "Chemin de dashboard HA local requis")
        else: require("path" not in item, "path est réservé à ha_dashboard")
