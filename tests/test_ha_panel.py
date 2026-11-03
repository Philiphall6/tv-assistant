"""Pure backend regression tests; no connection to a real home."""
import asyncio
import copy
import base64
import importlib
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1]
package = types.ModuleType("hatv_test")
package.__path__ = [str(ROOT / "custom_components/home_assistant_tv")]
sys.modules[package.__name__] = package
m = importlib.import_module("hatv_test.model")
r = importlib.import_module("hatv_test.repository")


class ValidationTests(unittest.TestCase):
    def test_home_information_voice_and_greeting(self):
        cfg={"schema":1,"settings":{"nav_clock_size":40,"nav_status_size":28,"voice_provider":1,"voice_agent_id":"conversation.test","voice_language":"fr","voice_confirm":True,"scene_movie":"scene.test","scene_off":"scene.off"},
             "navigation":[{"id":"voice","type":"voice","label":"Recherche","icon":"mdi:microphone","display":1}],
             "cards":[{"id":"greeting","type":"text","name":"{greeting}","interactive":False,"background_opacity":0,"title_size":64,"tap_action":{"action":"none"}}]}
        self.assertEqual(m.parse_yaml(m.as_yaml(cfg)),cfg)
        for key,value in (("nav_clock_size",41),("nav_status_size",29),("voice_provider",4),("voice_confirm","yes")):
            bad=copy.deepcopy(cfg);bad["settings"][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):m.validate(bad)

    def test_custom_voice_destinations(self):
        cfg={"schema":1,"settings":{"voice_provider":3,"voice_custom_package":"com.example.assistant","voice_custom_url":"https://search.example/find?q={query}","voice_custom_name":"Mon assistant","voice_custom_action":2}}
        self.assertEqual(m.validate(cfg)["settings"],cfg["settings"])
        for url in ("http://example.org/?q={query}","https://u:p@example.org/?q={query}","javascript:alert(1)","intent://x","https://{query}.example.org/","https://example.org/#{query}","https://example.org/?q={query}\\n","https://example.org/?q={other}","https://example.org:99999/?q={query}"):
            with self.subTest(url=url),self.assertRaises(ValueError):m.validate({"schema":1,"settings":{"voice_custom_url":url}})
        for pkg in ("fr.maison.tvlauncher","bad package","com.test/.Main"):
            with self.subTest(pkg=pkg),self.assertRaises(ValueError):m.validate({"schema":1,"settings":{"voice_custom_package":pkg}})

    def test_solid_wallpaper_colors(self):
        for color in ("#000000","#ffffff","#03A9F4","#123456"):
            cfg={"schema":1,"settings":{"wallpaper_mode":5,"wallpaper_color":color},"cards":[]}
            self.assertEqual(m.parse_yaml(m.as_yaml(cfg)),cfg)
        for color in (None,1,True,"red","#fff","#00112233","url(https://invalid)"):
            with self.subTest(color=color),self.assertRaises(ValueError):m.validate({"schema":1,"settings":{"wallpaper_color":color}})
        with self.assertRaises(ValueError):m.validate({"schema":1,"settings":{"wallpaper_mode":6}})

    def test_icon_shapes_roundtrip(self):
        for shape in range(6):
            cfg={"schema":1,"settings":{"app_icon_shape":shape},"cards":[]}
            self.assertEqual(m.parse_yaml(m.as_yaml(cfg)),cfg)
        for shape in (-1,6,True,"3",None,3.5):
            with self.subTest(shape=shape),self.assertRaises(ValueError):
                m.validate({"schema":1,"settings":{"app_icon_shape":shape}})

    def test_ha_sizing_roundtrip_and_scope(self):
        raw = "type: custom:button-card\nstyles:\n  card:\n    - height: 72px\n"
        card = {"id":"pac", "type":"ha_card", "lovelace":m.parse_card_yaml(raw), "source_yaml":raw}
        for mode in ("zone", "yaml"):
            cfg = {"schema":1,"settings":{},"cards":[card | {"ha_sizing":mode}]}
            self.assertEqual(m.parse_yaml(m.as_yaml(cfg)),cfg)
            self.assertEqual(cfg["cards"][0]["source_yaml"],raw)
        for mode in (True, 1, None, [], {}, "stretch", "ZONE"):
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                m.validate({"schema":1,"cards":[card | {"ha_sizing":mode}]})
        with self.assertRaises(ValueError):
            m.validate({"schema":1,"cards":[{"name":"Native","type":"text","ha_sizing":"zone"}]})

    def test_real_ha_card_preserves_frontend_yaml_without_evaluating_it(self):
        raw = "type: custom:button-card\nname: PAC\nstate_display: '[[[ return hass.states[\"sensor.test\"].state; ]]]'\nstyles:\n  card:\n    - border-radius: 24px\ncustom_fields:\n  pac: '<svg></svg>'\n"
        lovelace = m.parse_card_yaml(raw)
        card = {"id":"pac", "type":"ha_card", "name":"PAC", "lovelace":lovelace, "source_yaml":raw,
                "placement":{"x":7,"y":4,"width":30,"height":20}}
        cfg = {"schema":1,"settings":{},"cards":[card]}
        self.assertEqual(m.parse_yaml(m.as_yaml(cfg)), cfg)
        self.assertIn("[[[", lovelace["state_display"])
        for patch in ({"id":"../bad"}, {"lovelace":{}}, {"source_yaml":"type: button"},
                      {"tap_action":{"action":"toggle"}}, {"type":"button"}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):m.validate({"schema":1,"cards":[card|patch]})
        for raw in ("!!python/object:os.system {}", "type: button\ntype: entity", "type: button\na: &x []\nb: *x",
                    "type: button\nvalue: .inf", "[]", "type: 12", "type: button\nx: "+"["*25+"]"*25):
            with self.subTest(raw=raw[:40]), self.assertRaises(ValueError):m.parse_card_yaml(raw)

    def test_free_placement_and_web_dashboard(self):
        cards = [{"name":"Affichage", "type":"text", "interactive":False, "show_icon":False, "background_opacity":0,
                  "title_size":40, "body_size":30, "placement":{"x":5,"y":20,"width":30,"height":20}},
                 {"name":"App", "type":"app", "package":"org.example.app", "placement":{"x":80,"y":80,"width":20,"height":20}}]
        cfg={"schema":1,"settings":{"nav_background":False,"nav_width":60,"nav_tab_gap":32,"dashboard_web_path":"/dashboard-tv/tv"},"cards":cards}
        self.assertEqual(m.parse_yaml(m.as_yaml(cfg)),cfg)
        for patch in ({"placement":{"x":90,"y":0,"width":20,"height":10}}, {"placement":{"x":0,"y":0,"width":0,"height":10}},
                      {"placement":{"x":False,"y":0,"width":10,"height":10}}, {"placement":{"x":0.5,"y":0,"width":10,"height":10}},
                      {"placement":{"x":0,"y":0,"width":10,"height":10,"script":"bad"}}, {"interactive":"false"},
                      {"background_opacity":101}, {"title_size":True}, {"package":"org.example.app"}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):m.validate({"schema":1,"cards":[cards[0]|patch]})
        for path in ("https://other.invalid/", "//other.invalid/a", "/../a", "/%2f%2fevil", "/a?token=secret", "/a\\b"):
            with self.subTest(path=path), self.assertRaises(ValueError):m.validate({"schema":1,"settings":{"dashboard_web_path":path}})
        with self.assertRaises(ValueError):m.validate({"schema":1,"cards":[{"type":"app","name":"Bad","package":"org.example.app"}]})

    def test_navigation_roundtrip_and_boundaries(self):
        item={"id":"apps","type":"applications","label":"Mes apps","icon":"mdi:apps","display":2,"color":"#112233","icon_size":32}
        config={"schema":1,"settings":{},"cards":[],"navigation":[item,{"id":"web","type":"ha_dashboard","label":"TV","path":"/dashboard-tv/tv"},{"id":"app","type":"app","label":"Appli","package":"org.example.app"}]}
        self.assertEqual(m.parse_yaml(m.as_yaml(config)),config)
        self.assertEqual(m.validate({"schema":1,"navigation":[]})["navigation"],[])
        self.assertNotIn("navigation",m.validate({"schema":1}))
        for patch in ({"type":[]},{"label":"x"*61},{"text_size":True},{"icon":"javascript:x"},{"color":"red"},{"package":"a.b"},{"path":"/foo"},{"enabled":1},{"display":3},{"id":"../bad"},{"text_size":9}):
            with self.subTest(patch=patch),self.assertRaises(ValueError):m.validate({"schema":1,"navigation":[item|patch]})
        for items in ([item,item],[item,item|{"id":"second"}],[{"id":"web","type":"ha_dashboard","label":"Bad","path":"https://evil.invalid"}],[item]*41):
            with self.assertRaises(ValueError):m.validate({"schema":1,"navigation":items})

    def test_every_setting_default(self):
        cfg = {"schema": 1, "settings": {k: v["default"] for k, v in m.SCHEMA.items()}, "cards": []}
        self.assertEqual(m.validate(cfg), m.parse_yaml(m.as_yaml(cfg)))

    def test_types_limits_and_no_secrets(self):
        for setting in ({"ha_token": "secret"}, {"ha_url": "http://x"}, {"custom_wallpaper_uri": "file:///x"},
                        {"greeting_size": True}, {"greeting_size": 999}, {"show_home": "yes"},
                        {"favorite_apps_order": "a.b\na.b"}, {"home_group_entity": "bad"},
                        {"photo_album_url": "https://photos.app.goo.gl.evil.invalid/xx"},
                        {"photo_album_url": "https://u:p@photos.app.goo.gl/xx"}):
            with self.subTest(setting=list(setting)), self.assertRaises(ValueError):
                m.validate({"schema": 1, "settings": setting, "cards": []})

    def test_no_unsafe_yaml(self):
        for raw in ("schema: 1\nschema: 1", "schema: 1\nsettings: &a {}\ncards: [*a]", "!!python/object:os.system {}",
                    "schema: true", "schema: 1\nsettings: {greeting_size: .inf}", "schema: 1\ncards: [" + "[" * 25 + "]" * 25 + "]"):
            with self.subTest(raw=raw[:40]), self.assertRaises(ValueError):
                m.parse_yaml(raw)

    def test_cards_roundtrip_and_unknown(self):
        cfg = {"schema": 1, "cards": [{"type": "button", "name": "Lampe", "entity": "light.test", "tap_action": {"action": "toggle"}},
             {"type": "text", "content": "{state}", "entity": "sensor.test"}], "settings": {}}
        self.assertEqual(m.parse_yaml(m.as_yaml(cfg)), cfg)
        for card in ({"type":"custom:button-card", "name":"x"}, {"name":"x", "tap_action":{"action":"toggle"}},
                     {"name":"x", "tap_action":{"action":"url", "url_path":"javascript:alert(1)"}},
                     {"name":"x", "styles":{}}, {"name":"x", "color":"red"}):
            with self.assertRaises(ValueError):
                m.validate({"schema":1,"cards":[card]})
        with self.assertRaises(ValueError):
            m.validate({"schema":1,"cards":[{"name":"x","id":"same"},{"name":"y","id":"same"}]})

    def test_all_native_actions(self):
        for action in ({"action":"none"}, {"action":"more-info"}, {"action":"toggle"},
                       {"action":"perform-action","perform_action":"light.turn_on","target":{"entity_id":["light.test"]},"data":{"brightness":120}},
                       {"action":"navigate","navigation_path":"home"}, {"action":"url","url_path":"https://example.com"},
                       {"action":"launch-app","package":"org.example.app"}):
            m.validate({"schema":1,"cards":[{"entity":"light.test","tap_action":action}]})

    def test_empty_profile_and_limits(self):
        self.assertEqual(m.validate({"schema":1}), {"schema":1,"settings":{},"cards":[]})
        with self.assertRaises(ValueError):
            m.parse_yaml(" " * (m.MAX_BYTES + 1))
        for bad in ("../salon", "SALON", "", "x"*41):
            with self.assertRaises(ValueError):m.profile_id(bad)


class Store:
    def __init__(self):self.saved=None;self.fail=False
    async def async_save(self,value):
        if self.fail:raise OSError("test disk full")
        await asyncio.sleep(0)
        self.saved=copy.deepcopy(value)


class RepositoryTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.store=Store();self.r=r.Repository(self.store);self.cfg={"schema":1,"settings":{"hero_subtitle":"Original"},"cards":[]}

    async def test_profile_isolation_broadcast_and_shared_profile(self):
        for profile in ("salon","chambre"):
            await self.r.save(profile,profile,self.cfg,0)
        for device,profile in (("tv1","salon"),("tv2","chambre"),("tv3","salon")):
            await self.r.register(device,device,profile,"owner",self.cfg,[])
            await self.r.assign(device,profile,device)
        messages={k:[] for k in ("tv1","tv2","tv3")}
        for k in messages:self.r.listeners[k]=(k,messages[k].append)
        new=copy.deepcopy(self.cfg);new["settings"]["hero_subtitle"]="Updated"
        await self.r.save("salon","Salon",new,1)
        self.assertEqual([len(messages[k]) for k in messages],[1,0,1])
        self.assertEqual(self.r.device_snapshot("tv2")["config"],self.cfg)
        await self.r.assign("tv2","salon","Chambre")
        self.assertEqual(messages["tv2"][-1]["revision"],2)
        await self.r.register("tv2","Old","chambre","owner",self.cfg,[])
        self.assertEqual(self.r.data["devices"]["tv2"]["profile"],"salon")

    async def test_atomic_save_and_optimistic_concurrency(self):
        await self.r.save("salon","Salon",self.cfg,0)
        results=await asyncio.gather(self.r.save("salon","A",self.cfg,1),self.r.save("salon","B",self.cfg,1),return_exceptions=True)
        self.assertEqual(sum(isinstance(x,ValueError) for x in results),1)
        old=copy.deepcopy(self.r.data);self.store.fail=True
        with self.assertRaises(OSError):await self.r.save("salon","Fail",self.cfg,2)
        self.assertEqual(old,self.r.data)

    async def test_rejected_updates_do_not_change_data(self):
        await self.r.save("salon","Salon",self.cfg,0);old=copy.deepcopy(self.r.data)
        with self.assertRaises(ValueError):await self.r.save("salon","Fail",{"schema":1,"settings":{"ha_token":"secret"}},1)
        self.assertEqual(old,self.r.data)

    async def test_offline_restore_and_ack(self):
        await self.r.register("tv1","TV","salon","owner",self.cfg,[])
        self.assertEqual(self.r.device_snapshot("tv1")["revision"],0)
        await self.r.save("salon","Salon",self.cfg,0)
        await self.r.assign("tv1","salon","TV")
        newer={"schema":1,"settings":{},"cards":[]};await self.r.save("salon","Salon",newer,1)
        restored=r.Repository(self.store,self.store.saved)
        self.assertEqual(restored.device_snapshot("tv1")["revision"],2)
        self.assertFalse(restored.overview()["devices"][0]["online"])
        self.assertEqual(restored.snapshot("salon")["previous"],self.cfg)
        await restored.ack("tv1","salon",2,"")
        await restored.ack("tv1","salon",3,"rejected")
        self.assertEqual(restored.data["devices"]["tv1"]["applied_revision"],2)
        self.assertNotIn("owner",restored.overview()["devices"][0])

    async def test_new_tv_cannot_claim_existing_profile(self):
        private={"schema":1,"settings":{"hero_subtitle":"Private configuration"},"cards":[{"type":"ha_card","id":"private","lovelace":{"type":"markdown","content":"Private"}}]}
        await self.r.save("existing","Existing",private,0)
        await self.r.register("new-tv","New TV","existing","other-owner",self.cfg,[])
        self.assertIsNone(self.r.device_snapshot("new-tv")["config"])
        self.assertEqual(self.r.render_snapshot("new-tv")["cards"],[])
        await self.r.ack("new-tv","existing",1,"")
        self.assertNotIn("applied_revision",self.r.data["devices"]["new-tv"])
        await self.r.register("new-tv","New TV","existing","other-owner",self.cfg,[])
        self.assertFalse(self.r.data["devices"]["new-tv"]["approved"])
        await self.r.assign("new-tv","existing","Approved by administrator")
        self.assertEqual(self.r.device_snapshot("new-tv")["config"],private)

    async def test_device_ownership_and_no_commands(self):
        await self.r.register("tv1","TV","salon","owner",self.cfg,[])
        with self.assertRaises(ValueError):await self.r.register("tv1","TV","salon","other",self.cfg,[])
        with self.assertRaises(ValueError):await self.r.assign("missing","salon","TV")
        with self.assertRaises(ValueError):await self.r.register("tv2","TV","salon","owner",self.cfg,[{"package":"x.y","label":"x","command":"reboot"}])

    async def test_real_preview_is_scoped_ephemeral_and_rate_limited(self):
        jpeg=base64.b64encode(b"\xff\xd8\xff\xe0test\xff\xd9").decode()
        messages={"tv1":[],"tv2":[]}
        for device in messages:
            await self.r.register(device,device,"salon","owner",self.cfg,[],True)
            self.r.listeners[device]=(device,messages[device].append)
        before=copy.deepcopy(self.r.data)
        capture=asyncio.create_task(self.r.preview("tv1"));await asyncio.sleep(0)
        self.assertEqual(len(messages["tv1"]),1);self.assertFalse(messages["tv2"])
        request=messages["tv1"][0]["request_id"]
        with self.assertRaises(ValueError):self.r.preview_result("tv2",request,jpeg,1280,720,"")
        for image,width,height in (("<svg/>",1280,720),(jpeg,9999,720),("A"*700001,1280,720)):
            with self.assertRaises(ValueError):self.r.preview_result("tv1",request,image,width,height,"")
        self.r.preview_result("tv1",request,jpeg,1280,720,"")
        result=await capture;self.assertEqual(result["device_id"],"tv1");self.assertEqual(result["image"],jpeg)
        self.assertEqual(before,self.r.data);self.assertFalse(self.r.previews)
        with self.assertRaises(ValueError):await self.r.preview("tv1")
        with self.assertRaises(ValueError):self.r.preview_result("tv1",request,jpeg,1280,720,"")

    async def test_preview_offline_old_timeout_and_close(self):
        await self.r.register("tv","TV","salon","owner",self.cfg,[])
        with self.assertRaisesRegex(ValueError,"hors ligne"):await self.r.preview("tv")
        self.r.listeners["tv"]=("tv",lambda frame:None)
        with self.assertRaisesRegex(ValueError,"2.7.1"):await self.r.preview("tv")
        self.r.data["devices"]["tv"]["preview_supported"]=True
        with self.assertRaisesRegex(ValueError,"répondu"):await self.r.preview("tv",timeout=.01)
        self.assertFalse(self.r.previews)
        self.r.preview_requested.clear()
        capture=asyncio.create_task(self.r.preview("tv"));await asyncio.sleep(0);self.r.close()
        with self.assertRaisesRegex(ValueError,"rechargement"):await capture
        self.assertFalse(self.r.previews)


if __name__=="__main__":unittest.main(verbosity=2)
