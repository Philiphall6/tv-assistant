package fr.maison.tvlauncher;

import java.util.*;

public final class HeatPumpStateTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        Map<String,Object> card=CardConfig.parse(HeatPumpState.template()).get(0);Map<String,String> states=new HashMap<>();
        String[] codes={"10","20","30","40","41","50","60"};String[] modes={"OFF","HOT WATER","HEAT","POOL","POOL 2","TRANSFER","COOLING"};String[] labels={"Arrêt","ECS","Chauffage","Piscine","Piscine 2","Transfert","Froid"};int[] colors={0xff9e9e9e,0xffef5350,0xffff9800,0xff26c6da,0xff26c6da,0xffba68c8,0xff66bb6a};
        states.put(HeatPumpState.FREQUENCY,"42.36");states.put(HeatPumpState.DEFROST,"0");
        for(int i=0;i<codes.length;i++)for(String key:new String[]{codes[i],modes[i],modes[i].toLowerCase(Locale.ROOT)}){states.put(HeatPumpState.PRIORITY,key);HeatPumpState s=HeatPumpState.evaluate(card,states);check(s.label.equals(labels[i]+" · 42,4 Hz"),"mode label "+key);check(s.color==colors[i],"mode color "+key);check(s.running,"rotor when frequency positive");}
        states.put(HeatPumpState.PRIORITY,"10");states.put(HeatPumpState.HEATING,"on");
        for(String pump:new String[]{"on","true","TRUE","1","25.2"," 3.1 %"}){states.put(HeatPumpState.PUMP,pump);HeatPumpState s=HeatPumpState.evaluate(card,states);check(s.label.equals("Chauffage · circulation"),"circulation "+pump);check(s.color==0xffff9800,"circulation orange");}
        for(String pump:new String[]{"off","false","0","-5","unknown","unavailable",""}){states.put(HeatPumpState.PUMP,pump);check(HeatPumpState.evaluate(card,states).label.startsWith("Arrêt"),"pump stopped "+pump);}
        states.put(HeatPumpState.PUMP,"on");states.put(HeatPumpState.HEATING,"off");check(HeatPumpState.evaluate(card,states).label.startsWith("Arrêt"),"heating permission required");
        states.put(HeatPumpState.HEATING,"on");states.put(HeatPumpState.PRIORITY,"20");check(HeatPumpState.evaluate(card,states).label.startsWith("ECS"),"circulation only overrides OFF");
        states.put(HeatPumpState.PRIORITY,"10");
        for(String defrost:new String[]{"ACTIVE","active","1","PASSIVE","passive","2"}){states.put(HeatPumpState.DEFROST,defrost);HeatPumpState s=HeatPumpState.evaluate(card,states);check(s.label.equals(defrost.equals("1")||defrost.equalsIgnoreCase("ACTIVE")?"Dégivrage actif":"Dégivrage passif"),"defrost precedes circulation "+defrost);check(s.color==0xff66bb6a,"defrost green");}
        states.put(HeatPumpState.DEFROST,"0");states.put(HeatPumpState.PRIORITY,"30");
        for(String frequency:new String[]{"unavailable","unknown","NaN","Infinity","-Infinity","","0","-1"}){states.put(HeatPumpState.FREQUENCY,frequency);check(!HeatPumpState.evaluate(card,states).running,"stationary rotor "+frequency);}
        states.remove(HeatPumpState.FREQUENCY);check(HeatPumpState.evaluate(card,states).label.equals("Chauffage · — Hz"),"unknown frequency not fabricated");
        check(HeatPumpState.evaluate(card,Collections.emptyMap()).label.equals("Inconnue · — Hz"),"missing data not shown as off");
        check(HeatPumpState.parseFloat(" 12.25 Hz")==12.25,"JS parseFloat numeric prefix");check(HeatPumpState.parseFloat("1e2")==100,"exponent");
        check(HeatPumpState.frequencyText(42.25).equals("42,3 Hz"),"French Intl-style half-up rounding");check(HeatPumpState.frequencyText(1.15).equals("1,2 Hz"),"decimal halfway rounding");
        Map<String,Object> custom=new LinkedHashMap<>(card);custom.put("entity","sensor.custom_mode");custom.put("frequency_entity","sensor.custom_hz");Map<String,String> alternate=new HashMap<>();alternate.put("sensor.custom_mode","60");alternate.put("sensor.custom_hz","1");check(HeatPumpState.evaluate(custom,alternate).label.equals("Froid · 1 Hz"),"editable entity mappings");
        check(CardConfig.parse(CardConfig.dump(card)).get(0).equals(card),"YAML retains PAC options");
        for(String bad:new String[]{"frequency_entity","pump_entity","defrost_entity","heating_allowed_entity"}){Map<String,Object> invalid=new LinkedHashMap<>(card);invalid.put(bad,"invalid");boolean rejected=false;try{CardConfig.validate(invalid);}catch(Exception e){rejected=true;}check(rejected,"invalid PAC entity "+bad);}
        Map<String,Object> badAnimate=new LinkedHashMap<>(card);badAnimate.put("animate","yes");boolean rejected=false;try{CardConfig.validate(badAnimate);}catch(Exception e){rejected=true;}check(rejected,"animation boolean validation");
        System.out.println("PASS: "+checks+" PAC mapping/priority/configuration checks");
    }
}
