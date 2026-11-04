"""Ephemeral, authenticated TV messages. No queue, replay, storage or device wake-up."""
import copy
import re
import time
import uuid

from .model import profile_id, require

POSITIONS = ("top_right", "top_left", "bottom_right", "bottom_left")


def notification(data, dismiss=False):
    """Keep notification text as data, never HTML, URLs, templates or commands."""
    allowed = {"device_ids", "tag"} if dismiss else {"device_ids", "tag", "title", "message", "duration", "position"}
    require(isinstance(data, dict) and not set(data) - allowed, "Options de notification invalides")
    devices = data.get("device_ids")
    require(isinstance(devices, list) and 1 <= len(devices) <= 64, "Choisissez entre 1 et 64 TV")
    require(all(isinstance(d, str) for d in devices), "Identifiant de TV invalide")
    for device in devices:
        profile_id(device)
    tag = data.get("tag", "" if dismiss else uuid.uuid4().hex)
    require(isinstance(tag, str) and 1 <= len(tag) <= 64 and
            all(c.isascii() and (c.isalnum() or c in "_-") for c in tag), "Identifiant de notification invalide")
    payload = {"kind": "notification_dismiss" if dismiss else "notification", "tag": tag}
    if not dismiss:
        for key, default, maximum in (("title", "TV Assistant", 80), ("message", "", 600)):
            value = data.get(key, default)
            require(isinstance(value, str) and 1 <= len(value.strip()) <= maximum and
                    not any(ord(c) < 32 and c not in "\n\t" for c in value), "Texte de notification invalide")
            payload[key] = value.strip()
        duration = data.get("duration", 8)
        require(type(duration) is int and 3 <= duration <= 60, "Durée : 3 à 60 secondes")
        require(data.get("position", "top_right") in POSITIONS, "Position invalide")
        payload.update(duration=duration, position=data.get("position", "top_right"))
    return list(dict.fromkeys(devices)), payload


class NotificationHub:
    def __init__(self, repository):
        self.repository = repository
        self.listeners = {}
        self.ready = {}
        self.sessions = {}
        self.last_sent = {}

    def authorized(self, identity, owner, admin=False):
        device = self.repository.data["devices"].get(identity)
        return bool(device and device.get("approved", True) and
                    (admin or device.get("owner") == owner))

    def subscribe(self, key, identity, owner, send, ready, session):
        require(self.authorized(identity, owner), "TV inconnue ou non autorisée")
        require(isinstance(session, str) and re.fullmatch(r"[a-f0-9]{32}", session), "Session invalide")
        require(not any(k[0] is key[0] for k in self.listeners), "Un abonnement notifications par connexion")
        # One receiver per TV; do not duplicate banners after a reconnect.
        for old_key, (device, old_send) in list(self.listeners.items()):
            if device == identity:
                self.remove(old_key)
                old_send({"kind": "notifications_closed"})
        self.listeners[key] = (identity, send)
        self.ready[key] = bool(ready)
        self.sessions[key] = session

    def remove(self, key):
        self.listeners.pop(key, None)
        self.ready.pop(key, None)
        self.sessions.pop(key, None)

    def state(self, key, ready, session):
        require(key in self.listeners, "Abonnement notifications absent")
        require(isinstance(session, str) and re.fullmatch(r"[a-f0-9]{32}", session), "Session invalide")
        self.ready[key] = bool(ready)
        self.sessions[key] = session

    def status(self, identity):
        keys = [k for k, (device, _) in self.listeners.items() if device == identity]
        return "ready" if any(self.ready.get(k) for k in keys) else "paused" if keys else "disconnected"

    def send(self, data, owner=None, admin=False, dismiss=False):
        devices, payload = notification(data, dismiss)
        # Validate every target before delivering anything.
        require(all(self.authorized(d, owner, admin) for d in devices), "TV inconnue ou non autorisée")
        current = time.monotonic()
        if not dismiss:
            require(all(current - self.last_sent.get(d, -10) >= 1 for d in devices),
                    "Patientez une seconde entre les notifications")
        sent = 0
        for identity in devices:
            for key, (device, send) in list(self.listeners.items()):
                if device != identity or not self.ready.get(key):
                    continue
                try:
                    send(copy.deepcopy(payload) | {"session": self.sessions[key]})
                    sent += 1
                    if not dismiss:
                        self.last_sent[identity] = current
                except Exception:
                    self.remove(key)
        # Sent means handed to a live socket, not guaranteed displayed by Android.
        return {"sent": sent, "skipped": len(devices) - sent}

    def forget(self, identity):
        detached = []
        for key, (device, send) in list(self.listeners.items()):
            if device == identity:
                self.remove(key)
                detached.append(key)
                try:
                    send({"kind": "notifications_closed"})
                except Exception:
                    pass
        self.last_sent.pop(identity, None)
        return detached

    def close(self):
        for identity in {v[0] for v in self.listeners.values()}:
            self.forget(identity)
