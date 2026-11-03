package fr.maison.tvlauncher;

import java.text.NumberFormat;
import java.util.*;
import java.util.regex.*;

/** Native equivalent of configurable heat-pump states; no JavaScript execution. */
final class HeatPumpState {
    static final String TYPE="heat_pump";
    static final String PRIORITY="sensor.heat_pump_mode";
    static final String FREQUENCY="sensor.heat_pump_frequency";
    static final String DEFROST="sensor.heat_pump_defrost";
    static final String PUMP="sensor.heat_pump_circulation";
    static final String HEATING="switch.heat_pump_heating_allowed";
    static final String[] KEYS={"frequency_entity","defrost_entity","pump_entity","heating_allowed_entity"};
    static final String[] DEFAULTS={FREQUENCY,DEFROST,PUMP,HEATING};
    static final Pattern FLOAT_PREFIX=Pattern.compile("^[\\s]*([+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");
    final String mode,label;final int color;final double frequency;final boolean running;
    HeatPumpState(String mode,String label,int color,double frequency){this.mode=mode;this.label=label;this.color=color;this.frequency=frequency;running=Double.isFinite(frequency)&&frequency>0;}
    static boolean isCard(Map<String,Object> c){return TYPE.equals(CardConfig.str(c,"type",""));}
    static String template(){return "type: heat_pump\nname: PAC\nentity: "+PRIORITY+"\nfrequency_entity: "+FREQUENCY+"\ndefrost_entity: "+DEFROST+"\npump_entity: "+PUMP+"\nheating_allowed_entity: "+HEATING+"\nanimate: true\nshow_state: true\ntap_action:\n  action: more-info\n";}
    static String raw(Map<String,String> states,String id){String value=states.get(id);return value==null?"":value;}
    static double parseFloat(String value){Matcher m=FLOAT_PREFIX.matcher(value==null?"":value);if(!m.find())return Double.NaN;try{return Double.parseDouble(m.group(1));}catch(Exception e){return Double.NaN;}}
    static boolean pumpOn(String s){String lower=s.toLowerCase(Locale.ROOT);double f=parseFloat(s);return lower.equals("on")||lower.equals("true")||Double.isFinite(f)&&f>0;}
    static String frequencyText(double f){if(!Double.isFinite(f))return "— Hz";NumberFormat n=NumberFormat.getNumberInstance(I18n.locale());n.setMaximumFractionDigits(1);return n.format(java.math.BigDecimal.valueOf(f).setScale(1,java.math.RoundingMode.HALF_UP))+" Hz";}
    static HeatPumpState evaluate(Map<String,Object> c,Map<String,String> states){
        String defrost=raw(states,CardConfig.str(c,"defrost_entity",DEFROST)).toUpperCase(Locale.ROOT);
        String key=raw(states,CardConfig.str(c,"entity",PRIORITY)).toUpperCase(Locale.ROOT),mode=key;
        switch(key){case "10":mode="OFF";break;case "20":mode="HOT WATER";break;case "30":mode="HEAT";break;case "40":mode="POOL";break;case "41":mode="POOL 2";break;case "50":mode="TRANSFER";break;case "60":mode="COOLING";break;}
        double f=parseFloat(raw(states,CardConfig.str(c,"frequency_entity",FREQUENCY)));
        if(defrost.equals("ACTIVE")||defrost.equals("1"))return new HeatPumpState(mode,I18n.t("Dégivrage actif"),0xff66bb6a,f);
        if(defrost.equals("PASSIVE")||defrost.equals("2"))return new HeatPumpState(mode,I18n.t("Dégivrage passif"),0xff66bb6a,f);
        boolean circulation=mode.equals("OFF")&&raw(states,CardConfig.str(c,"heating_allowed_entity",HEATING)).equals("on")&&pumpOn(raw(states,CardConfig.str(c,"pump_entity",PUMP)));
        if(circulation)return new HeatPumpState(mode,I18n.t("Chauffage · circulation"),0xffff9800,f);
        String label;int color;
        switch(mode){
            case "OFF":label=I18n.t("Arrêt");color=0xff9e9e9e;break;
            case "HOT WATER":label=I18n.t("ECS");color=0xffef5350;break;
            case "HEAT":label=I18n.t("Chauffage");color=0xffff9800;break;
            case "POOL":label=I18n.t("Piscine");color=0xff26c6da;break;
            case "POOL 2":label=I18n.t("Piscine 2");color=0xff26c6da;break;
            case "TRANSFER":label=I18n.t("Transfert");color=0xffba68c8;break;
            case "COOLING":label=I18n.t("Froid");color=0xff66bb6a;break;
            default:label=I18n.t("Inconnue");color=0xff9e9e9e;
        }
        return new HeatPumpState(mode,label+" · "+frequencyText(f),color,f);
    }
}
