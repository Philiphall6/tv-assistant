"""One standard Home Assistant notification entity per approved TV."""
import asyncio

from homeassistant.components.notify import NotifyEntity, NotifyEntityFeature
from homeassistant.core import callback
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers import device_registry as dr, entity_registry as er
from homeassistant.helpers.entity_platform import async_get_current_platform

from .notification_service import async_send_notification

DOMAIN = "home_assistant_tv"
PARALLEL_UPDATES = 1


async def async_setup_entry(hass, entry, async_add_entities):
    manager = NotificationEntities(hass, entry, hass.data[DOMAIN], async_get_current_platform())
    manager.repository.notification_entities = manager
    # Await additions/removals to serialize rapid approval, forgetting and re-approval.
    entry.async_on_unload(manager.async_stop)
    manager.start()
    await manager.async_sync()


class NotificationEntities:
    def __init__(self, hass, entry, repository, platform):
        self.hass, self.entry, self.repository, self.platform = hass, entry, repository, platform
        self.entities = {}
        self.lock = asyncio.Lock()
        self.tasks = set()
        self.unsubscribe = None
        self.unsubscribe_registry = None
        self.stopped = False

    @callback
    def start(self):
        self.stopped = False
        self.unsubscribe = self.repository.observe_devices(self.changed)
        self.unsubscribe_registry = self.hass.bus.async_listen(er.EVENT_ENTITY_REGISTRY_UPDATED, self.registry_changed)

    async def async_stop(self):
        self.stopped = True
        if self.unsubscribe:
            self.unsubscribe()
            self.unsubscribe = None
        if self.unsubscribe_registry:
            self.unsubscribe_registry()
            self.unsubscribe_registry = None
        pending = tuple(self.tasks)
        for task in pending:
            task.cancel()
        if pending:
            await asyncio.gather(*pending, return_exceptions=True)

    @callback
    def registry_changed(self, event):
        registered = er.async_get(self.hass).async_get(event.data["entity_id"])
        if ((registered and registered.config_entry_id == self.entry.entry_id)
                or event.data["entity_id"] in self.repository.notification_entity_ids.values()):
            self.changed(None)

    @callback
    def changed(self, identity):
        if not self.stopped:
            task = self.hass.async_create_task(self.async_sync(), "TV Assistant notify entities")
            self.tasks.add(task)
            task.add_done_callback(self.tasks.discard)

    async def async_sync(self):
        async with self.lock:
            if self.stopped:
                return
            registry, devices = er.async_get(self.hass), dr.async_get(self.hass)
            # Delete only our notification entries for forgotten/unapproved TVs.
            approved = {identity for identity, data in self.repository.data["devices"].items()
                        if data.get("approved", True)}
            for identity in set(self.entities) - approved:
                entity = self.entities.pop(identity)
                if entity.entity_id in self.platform.entities:
                    await self.platform.async_remove_entity(entity.entity_id)
            valid_unique_ids = {f"{identity}_notifications" for identity in approved}
            for registered in er.async_entries_for_config_entry(registry, self.entry.entry_id):
                if (registered.domain == "notify" and registered.platform == DOMAIN
                        and registered.unique_id not in valid_unique_ids):
                    registry.async_remove(registered.entity_id)
                    if (registered.device_id and not er.async_entries_for_device(registry, registered.device_id, include_disabled_entities=True)
                            and (device := devices.async_get(registered.device_id))):
                        # Never remove another integration's device association.
                        owner_entry = getattr(device, "config_entry_id", None)
                        if owner_entry == self.entry.entry_id or (owner_entry is None
                                and device.config_entries == {self.entry.entry_id}):
                            devices.async_remove_device(device.id)
            # Re-read after awaits: registration may have changed while removing an entity.
            new_entities = []
            for identity, data in self.repository.data["devices"].items():
                if not data.get("approved", True):
                    continue
                if identity not in self.entities:
                    entity = TVNotificationEntity(self.repository, identity)
                    self.entities[identity] = entity
                    new_entities.append(entity)
                else:
                    entity = self.entities[identity]
                    registered = registry.async_get(entity.entity_id) if entity.entity_id else None
                    if registered and registered.device_id and (device := devices.async_get(registered.device_id)):
                        if device.name != data["name"]:
                            devices.async_update_device(device.id, name=data["name"])
                    if entity.hass and entity.entity_id in self.platform.entities:
                        entity.async_write_ha_state()
            if new_entities:
                await self.platform.async_add_entities(new_entities)
            self.repository.notification_entity_ids = {
                identity: entity_id for identity in self.entities
                if (entity_id := registry.async_get_entity_id("notify", DOMAIN, f"{identity}_notifications"))
            }


class TVNotificationEntity(NotifyEntity):
    _attr_has_entity_name = True
    _attr_translation_key = "tv_notification"
    _attr_supported_features = NotifyEntityFeature.TITLE
    _attr_icon = "mdi:television"

    def __init__(self, repository, identity):
        self.repository, self.identity = repository, identity
        self._attr_unique_id = f"{identity}_notifications"
        self._attr_device_info = dr.DeviceInfo(
            identifiers={(DOMAIN, identity)}, name=repository.data["devices"][identity]["name"],
            manufacturer="TV Assistant", model="TV launcher")

    @property
    def available(self):
        device = self.repository.data["devices"].get(self.identity)
        return bool(device and device.get("approved", True)
                    and self.repository.notifications.status(self.identity) == "ready")

    @property
    def extra_state_attributes(self):
        # No token, owner, profile, IP address, message or title in entity state.
        return {"reception": self.repository.notifications.status(self.identity)}

    async def async_send_message(self, message, title=None):
        result = await async_send_notification(self.hass, self.repository, {
            "device_ids": [self.identity], "message": message,
            "title": "TV Assistant" if title is None else title,
        }, self._context)
        if not result["sent"]:
            # A sleep/disconnect race must not record a successful send or queue anything.
            raise HomeAssistantError(translation_domain=DOMAIN, translation_key="notification_not_ready")
