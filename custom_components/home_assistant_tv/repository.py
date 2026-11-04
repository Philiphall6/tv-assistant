"""Transactional profiles, device assignments and per-connection subscriptions."""
import asyncio
import copy
import base64
import binascii
import logging
import time
import uuid
from datetime import datetime, timezone

from .model import ProfileError, profile_id, require, validate
from .notifications import NotificationHub

_LOGGER = logging.getLogger(__name__)


def now():
    return datetime.now(timezone.utc).isoformat()


class Repository:
    def __init__(self, store, data=None):
        self.store = store
        self.data = data or {"profiles": {}, "devices": {}}
        self.lock = asyncio.Lock()
        self.listeners = {}
        self.render_listeners = {}
        self.previews = {}
        self.preview_requested = {}
        self.device_observers = set()
        self.notification_entity_ids = {}
        self.notifications = NotificationHub(self)

    def observe_devices(self, listener):
        """Observe metadata only; no notification text or credentials are emitted."""
        self.device_observers.add(listener)
        return lambda: self.device_observers.discard(listener)

    def device_changed(self, identity):
        for listener in tuple(self.device_observers):
            listener(identity)

    async def preview(self, identity, timeout=8):
        require(identity in self.data["devices"], "TV inconnue")
        senders = [send for device, send in self.listeners.values() if device == identity]
        require(bool(senders), "TV hors ligne : ouvrez le launcher pour obtenir une vue réelle. Aucun réveil automatique.")
        require(self.data["devices"][identity].get("preview_supported"), "Mettez ce launcher à jour vers la version 2.7.1 pour l’aperçu réel.")
        require(identity not in self.previews, "Une capture est déjà en cours pour cette TV")
        current = time.monotonic()
        require(current - self.preview_requested.get(identity, -10) >= 2, "Patientez deux secondes entre les captures")
        self.preview_requested[identity] = current
        future = asyncio.get_running_loop().create_future()
        request = uuid.uuid4().hex
        self.previews[identity] = (request, future)
        try:
            senders[-1]({"kind":"preview_request", "request_id":request})
            async with asyncio.timeout(timeout):
                return await future
        except TimeoutError as err:
            raise ProfileError("La TV n’a pas répondu à la capture. Réessayez depuis l’accueil du launcher.") from err
        finally:
            self.previews.pop(identity, None)

    def preview_result(self, identity, request, image, width, height, error):
        pending = self.previews.get(identity)
        require(pending is not None and pending[0] == request, "Capture non demandée ou expirée")
        future = pending[1]
        require(not future.done(), "Capture déjà reçue")
        if error:
            future.set_exception(ProfileError("Capture indisponible : le launcher doit être visible et actif."))
            return
        require(isinstance(width, int) and not isinstance(width, bool) and 1 <= width <= 1280 and
                isinstance(height, int) and not isinstance(height, bool) and 1 <= height <= 720, "Dimensions de capture invalides")
        require(isinstance(image, str) and 0 < len(image) <= 700000, "Capture trop volumineuse")
        try:
            raw = base64.b64decode(image, validate=True)
        except (ValueError, binascii.Error) as err:
            raise ProfileError("Capture invalide") from err
        require(raw.startswith(b"\xff\xd8\xff") and raw.endswith(b"\xff\xd9"), "Capture JPEG attendue")
        record = self.data["devices"][identity]
        future.set_result({"device_id":identity, "image":image, "width":width, "height":height,
                           "captured_at":now(), "profile":record["profile"], "revision":record.get("applied_revision")})

    def close(self):
        self.device_observers.clear()
        self.notifications.close()
        for _, future in self.previews.values():
            if not future.done():future.set_exception(ProfileError("Intégration en cours de rechargement"))
        self.listeners.clear()
        self.render_listeners.clear()

    def render_snapshot(self, device):
        snap = self.device_snapshot(device)
        return {k: snap[k] for k in ("device_id", "profile", "revision")} | {
            "cards": [c for c in (snap.get("config") or {}).get("cards", [])
                      if c.get("type") == "ha_card" and c.get("enabled", True)]}

    def snapshot(self, profile):
        record = self.data["profiles"].get(profile)
        return copy.deepcopy({"profile": profile, **record}) if record else {
            "profile": profile, "title": profile, "revision": 0, "config": None}

    def device_snapshot(self, device):
        record = self.data["devices"][device]
        if record.get("approved", True) is False:
            return {"profile": record["profile"], "title": record["name"], "revision": 0,
                    "config": None, "device_id": device, "device_name": record["name"], "approval_required": True}
        snapshot = self.snapshot(record["profile"])
        snapshot.pop("previous", None)
        return {**snapshot, "device_id": device, "device_name": record["name"]}

    def overview(self):
        devices = []
        for identity, record in self.data["devices"].items():
            devices.append({k: copy.deepcopy(v) for k, v in record.items() if k != "owner"} | {
                "id": identity, "online": any(v[0] == identity for v in self.listeners.values()),
                "notifications": self.notifications.status(identity),
                "notification_entity": self.notification_entity_ids.get(identity)})
        return {"profiles": [{"id": k, "title": v["title"], "revision": v["revision"]} for k, v in self.data["profiles"].items()], "devices": devices}

    def notify(self, devices=None):
        for identity, callback in list(self.listeners.values()):
            if devices is None or identity in devices:
                callback(self.device_snapshot(identity))
        for identity, callback in list(self.render_listeners.values()):
            if devices is None or identity in devices:
                callback(self.render_snapshot(identity))

    async def save(self, profile, title, config, expected):
        profile_id(profile)
        require(isinstance(title, str) and 0 < len(title) <= 80, "Titre : 1 à 80 caractères")
        config = validate(config)
        async with self.lock:
            old = self.data["profiles"].get(profile)
            require(expected == (old["revision"] if old else 0), "Conflit : ce profil a changé. Rechargez-le avant d’enregistrer.")
            require(old is not None or len(self.data["profiles"]) < 100, "Limite de 100 profils atteinte")
            update = copy.deepcopy(self.data)
            update["profiles"][profile] = {"title": title, "revision": expected + 1, "config": config,
                                          "previous": old["config"] if old else None, "updated": now()}
            await self.store.async_save(update)
            self.data = update
        self.notify({d for d, v in self.data["devices"].items() if v["profile"] == profile})
        return self.snapshot(profile)

    async def register(self, identity, name, requested_profile, owner, config, apps, preview_supported=False):
        profile_id(identity)
        profile_id(requested_profile)
        require(isinstance(name, str) and 0 < len(name) <= 80, "Nom de TV invalide")
        config = validate(config)
        require(isinstance(apps, list) and len(apps) <= 500, "Liste d’applications trop grande")
        import re
        from .model import PACKAGE
        for app in apps:
            require(isinstance(app, dict) and set(app) == {"package", "label"}, "Application invalide")
            require(isinstance(app["package"], str) and re.fullmatch(PACKAGE, app["package"]), "Package invalide")
            require(isinstance(app["label"], str) and len(app["label"]) <= 160, "Nom d’application invalide")
        async with self.lock:
            old = self.data["devices"].get(identity, {})
            require(not old or old["owner"] == owner, "Identifiant de TV déjà utilisé par un autre compte")
            require(old or len(self.data["devices"]) < 64, "Limite de 64 TV atteinte")
            update = copy.deepcopy(self.data)
            # Changing a profile from HA stays authoritative after a TV reconnects.
            profile = old.get("profile", requested_profile)
            update["devices"][identity] = {**old, "name": old.get("name", name), "profile": profile,
                "owner": owner, "last_seen": now(), "reported_config": config, "apps": apps,
                "approved": old.get("approved", True) if old else False,
                "preview_supported": bool(preview_supported)}
            await self.store.async_save(update)
            self.data = update

        self.device_changed(identity)

    async def delete_profile(self, profile, expected):
        profile_id(profile)
        async with self.lock:
            old = self.data["profiles"].get(profile)
            require(old is not None, "Profil introuvable. Actualisez la liste.")
            require(type(expected) is int and expected == old["revision"],
                    "Conflit : ce profil a changé. Rechargez-le avant de le supprimer.")
            require(not any(d["profile"] == profile and d.get("approved", True)
                            for d in self.data["devices"].values()),
                    "Ce profil est encore affecté à une TV. Réaffectez cette TV ou supprimez sa connexion avant de supprimer le profil.")
            update = copy.deepcopy(self.data)
            del update["profiles"][profile]
            await self.store.async_save(update)
            self.data = update

    async def forget_device(self, identity):
        profile_id(identity)
        async with self.lock:
            old = self.data["devices"].get(identity)
            require(old is not None, "TV inconnue")
            update = copy.deepcopy(self.data)
            del update["devices"][identity]
            await self.store.async_save(update)
            self.data = update
        # Stop both profile and real-card streams. Other HA subscriptions and
        # the TV's credentials are not touched. Reconnection requires approval.
        detached, notifications = self.notifications.forget(identity), []
        snapshot = {"device_id": identity, "device_name": old["name"],
                    "profile": old["profile"], "revision": 0, "config": None,
                    "approval_required": True, "connection_removed": True}
        for listeners, payload in ((self.listeners, snapshot), (self.render_listeners,
                {"device_id": identity, "profile": old["profile"], "revision": 0, "cards": []})):
            for key, (device, send) in list(listeners.items()):
                if device == identity:
                    listeners.pop(key)
                    detached.append(key)
                    notifications.append((send, copy.deepcopy(payload)))
        pending = self.previews.get(identity)
        if pending and not pending[1].done():
            pending[1].set_exception(ProfileError("Connexion TV supprimée. Capture annulée."))
        self.preview_requested.pop(identity, None)
        self.device_changed(identity)
        for send, payload in notifications:
            try:
                send(payload)
            except Exception:  # A closed client must not keep other streams authorized.
                _LOGGER.warning("Unable to notify a detached TV client; its subscription was removed")
        return detached

    async def assign(self, identity, profile, name):
        profile_id(profile)
        require(isinstance(name, str) and 0 < len(name) <= 80, "Nom de TV invalide")
        async with self.lock:
            require(identity in self.data["devices"], "TV inconnue")
            require(profile in self.data["profiles"], "Créez d’abord ce profil")
            update = copy.deepcopy(self.data)
            update["devices"][identity].update(profile=profile, name=name, approved=True)
            await self.store.async_save(update)
            self.data = update
        self.notify({identity})
        self.device_changed(identity)

    async def ack(self, identity, profile, revision, error):
        async with self.lock:
            old = self.data["devices"].get(identity)
            if not old or old.get("approved", True) is False or old["profile"] != profile:
                return
            update = copy.deepcopy(self.data)
            update["devices"][identity].update(error=error[:240], last_seen=now())
            if not error:
                update["devices"][identity]["applied_revision"] = revision
            await self.store.async_save(update)
            self.data = update
