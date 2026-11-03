"""TV Assistant: authenticated editor and native push configuration."""
from pathlib import Path

try:
    import probatio as vol
except ImportError:  # Older HA versions before its schema-library migration.
    import voluptuous as vol
from homeassistant.components import frontend, panel_custom, websocket_api
from homeassistant.components.http import StaticPathConfig
from homeassistant.core import callback
from homeassistant.helpers.storage import Store
from homeassistant.helpers import config_validation as cv

from .model import SCHEMA, ProfileError, as_yaml, parse_yaml, parse_card_yaml, profile_id, require, validate
from .repository import Repository

DOMAIN = "home_assistant_tv"
PANEL = "home-assistant-tv"
CARDS_PANEL = "home-assistant-tv-cards"
CONFIG_SCHEMA = cv.config_entry_only_config_schema(DOMAIN)


async def async_setup(hass, config):
    return True


async def async_setup_entry(hass, entry):
    store = Store(hass, 1, DOMAIN)
    repository = Repository(store, await store.async_load())
    hass.data[DOMAIN] = repository
    if not hass.data.get(DOMAIN + "_registered"):
        await hass.http.async_register_static_paths([
            StaticPathConfig("/home_assistant_tv/panel.js", str(Path(__file__).with_name("panel.js")), False),
            StaticPathConfig("/home_assistant_tv/cards.js", str(Path(__file__).with_name("cards.js")), False),
            StaticPathConfig("/home_assistant_tv/localize.js", str(Path(__file__).with_name("localize.js")), False),
            StaticPathConfig("/home_assistant_tv/ui-catalog.js", str(Path(__file__).with_name("ui-catalog.js")), False)])
        for command in (ws_list, ws_get, ws_validate, ws_save, ws_restore, ws_assign, ws_subscribe, ws_ack, ws_preview, ws_preview_result, ws_parse_card, ws_render_subscribe):
            websocket_api.async_register_command(hass, command)
        hass.data[DOMAIN + "_registered"] = True
    await panel_custom.async_register_panel(hass, PANEL, "home-assistant-tv-panel",
        sidebar_title="TV Assistant", sidebar_icon="mdi:television-dashboard",
        module_url="/home_assistant_tv/panel.js?v=0.7.1", require_admin=True)
    await panel_custom.async_register_panel(hass, CARDS_PANEL, "home-assistant-tv-cards",
        module_url="/home_assistant_tv/cards.js?v=0.7.1", require_admin=False)
    return True


async def async_unload_entry(hass, entry):
    repository = hass.data.pop(DOMAIN, None)
    if repository:
        repository.close()
    frontend.async_remove_panel(hass, PANEL)
    frontend.async_remove_panel(hass, CARDS_PANEL)
    return True


def repo(hass):
    require(DOMAIN in hass.data, "L’intégration TV Assistant est désactivée")
    return hass.data[DOMAIN]


def result(connection, msg, value):
    connection.send_result(msg["id"], value)


def failure(connection, msg, error):
    connection.send_error(msg["id"], "invalid_profile", str(error))


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/parse_card", vol.Required("yaml"): str})
@websocket_api.require_admin
@callback
def ws_parse_card(hass, connection, msg):
    try:
        card = parse_card_yaml(msg["yaml"])
        result(connection, msg, {"lovelace": card, "yaml": as_yaml(card)})
    except (ValueError, TypeError, OverflowError) as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/render_subscribe", vol.Required("device_id"): str})
@callback
def ws_render_subscribe(hass, connection, msg):
    try:
        r = repo(hass)
        device = r.data["devices"].get(msg["device_id"])
        require(device is not None and (connection.user.is_admin or device["owner"] == connection.user.id),
                "TV inconnue ou non autorisée pour ce compte HA")
        require(not any(k[0] is connection for k in r.render_listeners), "Une vue TV par connexion")
        key = (connection, msg["id"])
        @callback
        def send(snapshot):
            connection.send_event(msg["id"], snapshot)
        @callback
        def unsubscribe():
            r.render_listeners.pop(key, None)
        connection.subscriptions[msg["id"]] = unsubscribe
        r.render_listeners[key] = (msg["device_id"], send)
        result(connection, msg, None)
        send(r.render_snapshot(msg["device_id"]))
    except (ValueError, TypeError, OverflowError) as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/list"})
@websocket_api.require_admin
@callback
def ws_list(hass, connection, msg):
    try:
        result(connection, msg, {**repo(hass).overview(), "schema": SCHEMA})
    except ProfileError as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/get", vol.Required("profile"): str})
@websocket_api.require_admin
@callback
def ws_get(hass, connection, msg):
    try:
        snap = repo(hass).snapshot(profile_id(msg["profile"]))
        result(connection, msg, {**snap, "yaml": as_yaml(snap["config"]) if snap["config"] else ""})
    except ProfileError as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/validate", vol.Optional("yaml"): str, vol.Optional("config"): dict})
@websocket_api.require_admin
@callback
def ws_validate(hass, connection, msg):
    try:
        config = parse_yaml(msg["yaml"]) if "yaml" in msg else validate(msg.get("config"))
        result(connection, msg, {"config": config, "yaml": as_yaml(config)})
    except (ValueError, TypeError, OverflowError) as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/save", vol.Required("profile"): str,
    vol.Required("title"): str, vol.Required("revision"): int, vol.Required("config"): dict})
@websocket_api.require_admin
@websocket_api.async_response
async def ws_save(hass, connection, msg):
    try:
        snap = await repo(hass).save(msg["profile"], msg["title"], msg["config"], msg["revision"])
        result(connection, msg, {**snap, "yaml": as_yaml(snap["config"])})
    except (ValueError, TypeError, OverflowError) as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/restore", vol.Required("profile"): str, vol.Required("revision"): int})
@websocket_api.require_admin
@websocket_api.async_response
async def ws_restore(hass, connection, msg):
    try:
        r = repo(hass)
        snap = r.snapshot(profile_id(msg["profile"]))
        require(snap.get("previous") is not None, "Aucune version précédente")
        restored = await r.save(msg["profile"], snap["title"], snap["previous"], msg["revision"])
        result(connection, msg, {**restored, "yaml": as_yaml(restored["config"])})
    except (ValueError, TypeError, OverflowError) as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/assign", vol.Required("device_id"): str,
    vol.Required("profile"): str, vol.Required("name"): str})
@websocket_api.require_admin
@websocket_api.async_response
async def ws_assign(hass, connection, msg):
    try:
        await repo(hass).assign(msg["device_id"], msg["profile"], msg["name"])
        result(connection, msg, None)
    except ProfileError as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/subscribe", vol.Required("device_id"): str,
    vol.Required("name"): str, vol.Required("profile"): str, vol.Required("config"): dict, vol.Required("apps"): list,
    vol.Optional("preview_supported", default=False): bool})
@websocket_api.async_response
async def ws_subscribe(hass, connection, msg):
    try:
        r = repo(hass)
        require(not any(key[0] is connection for key in r.listeners), "Une seule inscription TV par connexion")
        await r.register(msg["device_id"], msg["name"], msg["profile"], connection.user.id, msg["config"], msg["apps"], msg.get("preview_supported", False))
        key = (connection, msg["id"])
        @callback
        def send(snapshot):
            connection.send_event(msg["id"], snapshot)
        @callback
        def unsubscribe():
            r.listeners.pop(key, None)
        connection.subscriptions[msg["id"]] = unsubscribe
        r.listeners[key] = (msg["device_id"], send)
        result(connection, msg, None)
        send(r.device_snapshot(msg["device_id"]))
    except (ValueError, TypeError, OverflowError) as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/ack", vol.Required("profile"): str,
    vol.Required("revision"): int, vol.Optional("error", default=""): str})
@websocket_api.async_response
async def ws_ack(hass, connection, msg):
    try:
        r = repo(hass)
        identity = next((v[0] for k, v in r.listeners.items() if k[0] is connection), None)
        require(identity is not None, "TV non inscrite")
        await r.ack(identity, msg["profile"], msg["revision"], msg["error"])
        result(connection, msg, None)
    except ProfileError as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/preview", vol.Required("device_id"): str})
@websocket_api.require_admin
@websocket_api.async_response
async def ws_preview(hass, connection, msg):
    try:
        result(connection, msg, await repo(hass).preview(msg["device_id"]))
    except ProfileError as err:
        failure(connection, msg, err)


@websocket_api.websocket_command({vol.Required("type"): DOMAIN + "/preview_result", vol.Required("request_id"): str,
    vol.Optional("image", default=""): str, vol.Optional("width", default=0): int,
    vol.Optional("height", default=0): int, vol.Optional("error", default=""): str})
@callback
def ws_preview_result(hass, connection, msg):
    try:
        r = repo(hass)
        identity = next((v[0] for k, v in r.listeners.items() if k[0] is connection), None)
        require(identity is not None, "TV non inscrite")
        r.preview_result(identity, msg["request_id"], msg.get("image", ""), msg.get("width", 0), msg.get("height", 0), msg.get("error", ""))
        result(connection, msg, None)
    except ProfileError as err:
        failure(connection, msg, err)
