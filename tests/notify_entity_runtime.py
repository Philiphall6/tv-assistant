"""Exercise real HA notify entity/platform/service/registry code, without a real home."""
import asyncio
from datetime import timedelta
import logging
from pathlib import Path
import sys
import tempfile
from types import MappingProxyType, SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from custom_components.home_assistant_tv import notify as platform_module
from custom_components.home_assistant_tv.repository import Repository
from homeassistant.components import notify
from homeassistant import auth, bootstrap, loader
from homeassistant.config_entries import ConfigEntry, ConfigEntries
from homeassistant.core import Context, HomeAssistant
from homeassistant.exceptions import HomeAssistantError, ServiceValidationError, Unauthorized
from homeassistant.helpers import device_registry as dr, entity_registry as er
from homeassistant.helpers.entity_platform import EntityPlatform
from homeassistant.setup import async_setup_component


class NotifyEntityTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.hass = HomeAssistant(self.tmp.name)
        self.hass.config_entries = ConfigEntries(self.hass, {})
        self.hass.config.language = "en"
        loader.async_setup(self.hass)
        await bootstrap.async_load_base_functionality(self.hass)
        self.hass.auth = await auth.auth_manager_from_config(self.hass, [{"type":"homeassistant"}], [])
        self.entry = ConfigEntry(domain="home_assistant_tv", title="TV Assistant", data={},
            options={}, version=1, minor_version=1, source="user", unique_id="fixture-entry",
            discovery_keys=MappingProxyType({}), subentries_data=None)
        self.hass.config_entries._entries[self.entry.entry_id] = self.entry
        self.repo = Repository(SimpleNamespace(async_save=AsyncMock()))
        self.hass.data[platform_module.DOMAIN] = self.repo
        self.config = {"schema":1,"settings":{},"cards":[]}
        await self.repo.save("shared", "Test profile", self.config, 0)
        await self.register("fixture-tv", "owner")
        await self.repo.assign("fixture-tv", "shared", "Test TV")
        self.events = []
        self.key = (object(), 1)
        self.repo.notifications.subscribe(self.key, "fixture-tv", "owner", self.events.append, True, "a"*32)
        # Use HA's real notify component and entity service dispatcher, with local translations.
        self.translation = patch("homeassistant.helpers.translation.async_get_translations",
            new_callable=AsyncMock, return_value={
                "component.home_assistant_tv.entity.notify.tv_notification.name":"Notifications"})
        self.translation.start()
        self.assertTrue(await async_setup_component(self.hass, "notify", {}))
        self.platform = EntityPlatform(hass=self.hass, logger=logging.getLogger(__name__), domain="notify",
            platform_name="home_assistant_tv", platform=platform_module,
            scan_interval=timedelta(seconds=30), entity_namespace=None)
        notify_component = self.hass.data[notify.DATA_COMPONENT]
        notify_component._platforms[self.entry.entry_id] = self.platform
        self.assertTrue(await self.platform.async_setup_entry(self.entry))
        await self.hass.async_block_till_done()

    async def asyncTearDown(self):
        await self.entry._async_process_on_unload(self.hass)
        await self.platform.async_reset()
        self.repo.close()
        await self.hass.async_stop()
        self.translation.stop()
        self.tmp.cleanup()

    async def register(self, identity, owner):
        await self.repo.register(identity, "Test TV", "shared", owner, self.config, [])

    def entity(self, identity="fixture-tv"):
        entity_id = er.async_get(self.hass).async_get_entity_id("notify", "home_assistant_tv", identity+"_notifications")
        return self.platform.entities[entity_id]

    async def send(self, identity="fixture-tv", user_id=None, **kwargs):
        self.repo.notifications.last_sent.clear()
        await self.hass.services.async_call("notify", "send_message",
            {"message":"Fixture", **kwargs}, target={"entity_id":self.entity(identity).entity_id},
            blocking=True, context=Context(user_id=user_id))
        await self.hass.async_block_till_done()

    async def test_real_service_title_target_and_no_sensitive_state(self):
        entity = self.entity()
        self.assertTrue(entity.available)
        self.assertEqual(entity.unique_id,"fixture-tv_notifications")
        self.assertIn("Test TV", self.hass.states.get(entity.entity_id).attributes["friendly_name"])
        registered=er.async_get(self.hass).async_get(entity.entity_id)
        self.assertIsNotNone(dr.async_get(self.hass).async_get(registered.device_id))
        await self.send(title="Fixture title")
        self.assertEqual(len(self.events),1)
        self.assertEqual(self.events[0]["title"],"Fixture title")
        self.assertEqual(self.events[0]["duration"],8)
        self.assertEqual(self.events[0]["position"],"top_right")
        self.assertNotEqual(self.hass.states.get(entity.entity_id).state,"unknown")
        state=str(self.hass.states.get(entity.entity_id).attributes)
        for private in ("owner","Fixture title","Fixture","token","profile","http"):
            self.assertNotIn(private,state)
        before=len(self.events)
        with self.assertRaises(ServiceValidationError):await self.send(message="x"*601)
        self.assertEqual(len(self.events),before)

    async def test_ready_independent_of_editor_sleep_drop_reconnect_no_replay(self):
        entity=self.entity()
        self.assertFalse(self.repo.overview()["devices"][0]["online"])
        self.repo.notifications.state(self.key,False,"b"*32)
        await self.hass.async_block_till_done()
        self.assertEqual(self.hass.states.get(entity.entity_id).state,"unavailable")
        await self.send()
        self.assertEqual(self.events,[])
        with self.assertRaises(HomeAssistantError):await entity.async_send_message("Race")
        self.repo.notifications.remove(self.key)
        await self.hass.async_block_till_done()
        self.assertEqual(entity.extra_state_attributes["reception"],"disconnected")
        self.repo.notifications.subscribe(self.key,"fixture-tv","owner",self.events.append,True,"c"*32)
        await self.hass.async_block_till_done()
        self.assertEqual(self.events,[])
        self.assertEqual(self.hass.states.get(entity.entity_id).state,"unknown")
        await self.send()
        self.assertEqual(len(self.events),1)

    async def test_user_context_permissions_enforced_by_real_notify_action(self):
        # HA's entity permission check and our owner check both see the initiating user.
        permissions=SimpleNamespace(check_entity=lambda *args:True)
        for user_id,active,admin,allowed in (("owner",True,False,True),("outsider",True,False,False),
                ("admin",True,True,True),("disabled",False,True,False),("missing",True,False,False)):
            user=None if user_id=="missing" else SimpleNamespace(id=user_id,is_active=active,is_admin=admin,permissions=permissions)
            with patch.object(self.hass.auth,"async_get_user",new_callable=AsyncMock,return_value=user):
                if allowed:await self.send(user_id=user_id)
                else:
                    with self.assertRaises((Unauthorized,ServiceValidationError)):await self.send(user_id=user_id)
        self.assertEqual(len(self.events),2)

    async def test_approval_dynamic_add_rename_delete_reapprove_and_registry_identity(self):
        registry=er.async_get(self.hass)
        original=self.entity().entity_id
        await self.repo.assign("fixture-tv","shared","Renamed TV")
        await self.hass.async_block_till_done()
        self.assertEqual(self.entity().entity_id,original)
        device=dr.async_get(self.hass).async_get(registry.async_get(original).device_id)
        self.assertEqual(device.name,"Renamed TV")
        await self.register("second-tv","another-owner")
        await self.hass.async_block_till_done()
        self.assertEqual(len(self.platform.entities),1,"Unapproved TVs get no entity")
        await self.repo.assign("second-tv","shared","Second TV")
        await self.hass.async_block_till_done()
        self.assertEqual(len(self.platform.entities),2)
        self.assertFalse(self.entity("second-tv").available)
        other_events=[]
        self.repo.notifications.subscribe((object(),2),"second-tv","another-owner",other_events.append,True,"d"*32)
        await self.hass.async_block_till_done()
        await self.send("second-tv")
        self.assertEqual(len(other_events),1)
        self.assertEqual(self.events,[],"A shared profile is never a notification target")
        await self.repo.forget_device("fixture-tv")
        await self.hass.async_block_till_done()
        self.assertIsNone(registry.async_get(original))
        self.assertIsNone(self.hass.states.get(original))
        self.assertNotIn("fixture-tv",self.repo.notification_entity_ids)
        self.assertEqual(len(self.platform.entities),1)
        await self.register("fixture-tv","owner")
        await self.hass.async_block_till_done()
        self.assertEqual(len(self.platform.entities),1)
        await self.repo.assign("fixture-tv","shared","Renamed TV")
        await self.hass.async_block_till_done()
        self.assertEqual(self.entity().unique_id,"fixture-tv_notifications")
        self.assertEqual(len(self.platform.entities),2)

    async def test_unload_cancels_observers_and_preserves_registry(self):
        original=self.entity().entity_id
        await self.entry._async_process_on_unload(self.hass)
        self.assertFalse(self.repo.device_observers)
        await self.platform.async_reset()
        self.assertIsNotNone(er.async_get(self.hass).async_get(original))
        self.assertTrue(await self.platform.async_setup_entry(self.entry))
        await self.hass.async_block_till_done()
        self.assertEqual(self.entity().entity_id,original)
        self.assertEqual(len(self.platform.entities),1)

    async def test_entity_registry_rename_updates_editor_reference(self):
        original=self.entity().entity_id
        er.async_get(self.hass).async_update_entity(original,new_entity_id="notify.custom_tv_name")
        await self.hass.async_block_till_done()
        self.assertEqual(self.entity().entity_id,"notify.custom_tv_name")
        self.assertEqual(self.repo.notification_entity_ids["fixture-tv"],"notify.custom_tv_name")
        await self.send()
        self.assertEqual(len(self.events),1)

    async def test_rapid_approval_forget_and_reregistration_leaves_one_entity(self):
        await self.register("fast-tv","owner")
        await self.repo.assign("fast-tv","shared","Fast TV")
        await self.repo.forget_device("fast-tv")
        await self.register("fast-tv","owner")
        await self.repo.assign("fast-tv","shared","Fast TV")
        await self.hass.async_block_till_done()
        entries=er.async_entries_for_config_entry(er.async_get(self.hass),self.entry.entry_id)
        self.assertEqual(sum(e.unique_id=="fast-tv_notifications" for e in entries),1)
        self.assertEqual(len(self.platform.entities),2)


if __name__=="__main__":unittest.main(verbosity=2)
