"""Run inside the matching official HA image, with no network and no real config."""
import asyncio
import copy
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from custom_components import home_assistant_tv as component
from custom_components.home_assistant_tv.repository import Repository
from homeassistant.exceptions import Unauthorized


class Connection:
    def __init__(self,admin=True):
        self.user=SimpleNamespace(id="test-admin" if admin else "test-user",is_admin=admin)
        self.results=[];self.errors=[];self.events=[];self.subscriptions={}
    def send_result(self,ident,value):self.results.append((ident,value))
    def send_error(self,ident,code,message):self.errors.append((ident,code,message))
    def send_event(self,ident,event):self.events.append((ident,event))
    def async_handle_exception(self,msg,error):self.errors.append((msg["id"],type(error).__name__,str(error)))


class RuntimeTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tasks=[]
        def schedule(coro,*args,**kwargs):
            task=asyncio.create_task(coro);self.tasks.append(task);return task
        self.store=SimpleNamespace(async_save=AsyncMock(),async_load=AsyncMock(return_value=None))
        self.repo=Repository(self.store)
        self.hass=SimpleNamespace(data={component.DOMAIN:self.repo},async_create_background_task=schedule,
                                  http=SimpleNamespace(async_register_static_paths=AsyncMock()))
        self.cfg={"schema":1,"settings":{"hero_subtitle":"Test"},"cards":[]}

    async def call(self,handler,conn,**msg):
        handler(self.hass,conn,{"id":1,**msg})
        while self.tasks:
            tasks=self.tasks[:];self.tasks.clear();await asyncio.gather(*tasks)

    async def test_actual_decorators_deny_non_admin_writes(self):
        for handler in (component.ws_save,component.ws_restore,component.ws_assign,component.ws_list,component.ws_get,component.ws_validate,component.ws_preview,component.ws_parse_card):
            with self.subTest(handler=handler.__name__),self.assertRaises(Unauthorized):
                handler(self.hass,Connection(False),{"id":1})
        self.assertFalse(self.repo.data["profiles"])

    async def test_authenticated_read_subscription_save_push_ack_unsubscribe(self):
        tv=Connection(False)
        await self.call(component.ws_subscribe,tv,device_id="tv1",name="Emulator",profile="salon",config=self.cfg,apps=[])
        self.assertFalse(tv.errors,tv.errors);self.assertEqual(tv.events[-1][1]["revision"],0)
        admin=Connection();await self.call(component.ws_save,admin,profile="salon",title="Salon",revision=0,config=self.cfg)
        self.assertFalse(admin.errors,admin.errors);self.assertEqual(tv.events[-1][1]["revision"],0)
        self.assertTrue(tv.events[-1][1]["approval_required"])
        await self.call(component.ws_assign,admin,device_id="tv1",profile="salon",name="Test TV")
        self.assertEqual(tv.events[-1][1]["revision"],1)
        await self.call(component.ws_ack,tv,profile="salon",revision=1,error="")
        self.assertEqual(self.repo.overview()["devices"][0]["applied_revision"],1)
        tv.subscriptions[1]();self.assertFalse(self.repo.overview()["devices"][0]["online"])

    async def test_no_public_profile_data_and_no_unauthorized_ack(self):
        conn=Connection(False)
        await self.call(component.ws_ack,conn,profile="salon",revision=999,error="")
        self.assertTrue(conn.errors)
        admin=Connection();await self.call(component.ws_validate,admin,config={"schema":1,"settings":{"ha_token":"secret"}})
        self.assertTrue(admin.errors);self.assertFalse(self.repo.data["profiles"])

    async def test_setup_static_panel_registration_and_reload(self):
        commands=[]
        with patch.object(component,"Store",return_value=self.store),patch.object(component.websocket_api,"async_register_command",side_effect=lambda h,c:commands.append(c)),patch.object(component.panel_custom,"async_register_panel",new_callable=AsyncMock) as panel,patch.object(component.frontend,"async_remove_panel"):
            self.assertTrue(await component.async_setup_entry(self.hass,SimpleNamespace()))
            self.assertEqual(len(commands),12)
            self.assertTrue(panel.call_args_list[0].kwargs["require_admin"])
            self.assertFalse(panel.call_args_list[1].kwargs["require_admin"])
            self.assertNotIn("sidebar_title",panel.call_args_list[1].kwargs)
            self.assertEqual(len(self.hass.http.async_register_static_paths.call_args.args[0]),4)
            self.assertTrue(await component.async_unload_entry(self.hass,None))
            self.assertTrue(await component.async_setup_entry(self.hass,None))
            self.assertEqual(len(commands),12)

    async def test_renderer_owner_or_admin_and_no_native_ack_or_online_status(self):
        await self.repo.register("tv1","TV","salon","test-user",self.cfg,[])
        outsider=Connection(False);outsider.user.id="someone-else"
        await self.call(component.ws_render_subscribe,outsider,device_id="tv1")
        self.assertTrue(outsider.errors);self.assertFalse(outsider.events)
        browser=Connection(False)
        await self.call(component.ws_render_subscribe,browser,device_id="tv1")
        self.assertFalse(browser.errors);self.assertEqual(browser.events[-1][1]["cards"],[])
        self.assertFalse(self.repo.overview()["devices"][0]["online"])
        await self.call(component.ws_ack,browser,profile="salon",revision=999)
        self.assertTrue(browser.errors)
        card={"type":"ha_card","id":"pac","lovelace":{"type":"custom:button-card"}}
        await self.repo.save("salon","Salon",self.cfg|{"cards":[card,{"type":"text","name":"Private native card"}]},0)
        self.assertEqual(browser.events[-1][1]["cards"],[])
        await self.repo.assign("tv1","salon","Test TV")
        self.assertEqual(browser.events[-1][1]["cards"],[card])
        self.assertNotIn("settings",browser.events[-1][1]);self.assertNotIn("previous",browser.events[-1][1])
        browser.subscriptions[1]();self.assertFalse(self.repo.render_listeners)
        admin=Connection();await self.call(component.ws_render_subscribe,admin,device_id="tv1")
        self.assertFalse(admin.errors);self.repo.close();self.assertFalse(self.repo.render_listeners)

    async def test_real_card_parser_preserves_script_as_data(self):
        admin=Connection()
        await self.call(component.ws_parse_card,admin,yaml="type: custom:button-card\nname: PAC\nstate_display: '[[[ return 42; ]]]'")
        self.assertFalse(admin.errors)
        self.assertEqual(admin.results[-1][1]["lovelace"]["state_display"],"[[[ return 42; ]]]")

    async def test_preview_requires_registered_tv_and_admin(self):
        await self.call(component.ws_preview_result,Connection(False),request_id="x",image="",width=0,height=0,error="unavailable")
        admin=Connection();await self.call(component.ws_preview,admin,device_id="missing")
        self.assertTrue(admin.errors)


if __name__=="__main__":unittest.main(verbosity=2)
