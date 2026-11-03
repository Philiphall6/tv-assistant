package fr.maison.tvlauncher;

import java.util.*;

public final class CardConfigTest {
    static int checks;
    public static void main(String[] args){
        require(CardConfig.parse(CardConfig.EMPTY).isEmpty(),"empty dashboard");
        require(CardConfig.parse("").isEmpty(),"empty text");
        Map<String,Object> c=CardConfig.parse(CardConfig.EXAMPLE).get(0);require("Ma carte".equals(c.get("name")),"example");
        String yaml="cards:\n  - id: lounge\n    type: button\n    name: Salon\n    entity: light.salon\n    icon: mdi:lightbulb\n    color: '#FFE099'\n    content: 'État : {state}'\n    tap_action:\n      action: perform-action\n      perform_action: light.turn_on\n      target:\n        area_id: [salon, cuisine]\n      data:\n        brightness_pct: 50\n        rgb_color: [255, 180, 90]\n      confirmation: false\n    hold_action:\n      action: more-info\n    actions:\n      - name: Éteindre\n        action: perform-action\n        perform_action: light.turn_off\n  - type: text\n    name: Note\n    content: Bonjour\n    enabled: false\n";
        List<Map<String,Object>> all=CardConfig.parse(yaml);require(all.size()==2,"multiple cards");require(CardConfig.parse(CardConfig.deck(all)).equals(all),"lossless yaml roundtrip");
        require(CardConfig.parse("{\"cards\":[{\"name\":\"JSON\",\"type\":\"text\"}]}").size()==1,"JSON input");
        require(CardConfig.parse("type: entity\nentity: sensor.temperature\n").size()==1,"entity only");
        require(CardConfig.parse("type: text\ncontent: Bonjour\n").size()==1,"content only");
        String ha="type: ha_card\nid: pac\nlovelace:\n  type: custom:button-card\n  state_display: '[[[ return hass.states[\"sensor.test\"].state; ]]]'\n  styles:\n    card:\n      - border-radius: 24px\n";
        List<Map<String,Object>> real=CardConfig.parse(ha);require(CardConfig.parse(CardConfig.deck(real)).equals(real),"true HA config lossless roundtrip");
        for(String sizing:Arrays.asList("zone","yaml")){List<Map<String,Object>> sized=CardConfig.parse(ha+"ha_sizing: "+sizing);require(CardConfig.parse(CardConfig.deck(sized)).equals(sized),"sizing roundtrip "+sizing);}
        for(String sizing:Arrays.asList("true","1","null","[]","{}","stretch","ZONE"))reject(ha+"ha_sizing: "+sizing,"invalid sizing "+sizing);
        reject("name: Native\ntype: text\nha_sizing: zone","sizing is HA only");
        reject(ha+"tap_action: {action: more-info}","HA actions only inside lovelace");
        reject(ha.replace("id: pac","id: ../bad"),"safe real card id");
        reject(ha+"source_yaml: 'type: button'","source/config mismatch");
        reject("name: Test\nlovelace: {type: button}","explicit opt-in required");
        for(String action:Arrays.asList("more-info","none","toggle"))require(CardConfig.parse("name: Test\nentity: light.salon\ntap_action:\n  action: "+action).size()==1,"action "+action);
        require(CardConfig.parse("name: Test\ntap_action:\n  action: call-service\n  service: script.turn_on\n  target:\n    entity_id: script.test").size()==1,"service alias");
        for(String path:Arrays.asList("home","dashboard","applications"))require(CardConfig.parse("name: Test\ntap_action:\n  action: navigate\n  navigation_path: "+path).size()==1,"native navigation");
        require(CardConfig.parse("name: Test\ntap_action:\n  action: url\n  url_path: https://example.com/info").size()==1,"explicit web link");
        require(CardConfig.parse("name: Test\ntap_action:\n  action: launch-app\n  package: org.videolan.vlc").size()==1,"application link");
        reject("name: A\nname: B","duplicate keys");reject("!!java.lang.Runtime {}","unsafe object");reject("cards: &a [*a]","recursive aliases");reject("cards: [ {name: A, tap_action: &a {action: none}}, {name: B, tap_action: *a}]","collection aliases");
        reject("cards: {}","not a list");reject("cards: [null]","null card");reject("type: custom:button-card\nname: A","HACS not silently ignored");reject("name: A\nstyles: []","unsupported option");reject("type: button","no label/entity/content");
        reject("name: A\ncolor: red","invalid color");reject("name: A\nentity: bad","invalid entity");reject("name: A\nshow_state: 'yes'","boolean type");reject("name: 12","text type");
        reject("name: A\ntap_action:\n  action: toggle","toggle without entity");reject("name: A\ntap_action:\n  action: perform-action\n  perform_action: turn_on","invalid service");reject("name: A\ntap_action:\n  action: evil","unsupported action");
        reject("name: A\ntap_action:\n  action: navigate\n  navigation_path: /lovelace/0","no web navigation disguised as native");
        reject("name: A\ntap_action:\n  action: url\n  url_path: javascript:alert(1)","no script URL");reject("name: A\ntap_action:\n  action: url\n  url_path: https://secret@example.com/","no URL credentials");
        reject("name: A\ntap_action:\n  action: none\n  target: {entity_id: 12}","invalid target");reject("name: A\ntap_action:\n  action: none\n  data: []","data map");
        reject("cards: [{id: same, name: A},{id: same, name: B}]","duplicate id");reject("name: A\ncontent: 2026-01-01","dates require quoting");reject("name: A\ntap_action:\n  action: none\n  data: {v: .nan}","finite numbers");
        reject("name: A\ncontent: "+new String(new char[CardConfig.MAX_LENGTH]).replace('\0','x'),"size limit");
        List<Map<String,Object>> many=new ArrayList<>();for(int i=0;i<200;i++)many.add(Collections.singletonMap("name","Carte "+i));require(CardConfig.parse(CardConfig.deck(many)).size()==200,"scrolling dashboard not limited to a handful of cards");
        System.out.println("PASS: "+checks+" native-card configuration checks");
    }
    static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void reject(String yaml,String message){boolean rejected=false;try{CardConfig.parse(yaml);}catch(Exception e){rejected=true;}require(rejected,message);}
}
