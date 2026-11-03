package fr.maison.tvlauncher;

import java.net.URI;

/** Credential-bearing transports must use a verified HTTPS origin, never user-info or redirects. */
final class ServerAddress {
    static String normalize(String raw){
        try{
            URI uri=new URI(raw.trim());
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getPort()>65535||uri.getPort()==0||!(uri.getPath().isEmpty()||"/".equals(uri.getPath())))throw new Exception();
            return new URI("https",null,uri.getHost().toLowerCase(java.util.Locale.ROOT),uri.getPort()==443?-1:uri.getPort(),null,null,null).toString();
        }catch(Exception e){throw new IllegalArgumentException("Adresse HTTPS du serveur requise, avec un certificat valide, sans chemin, identifiant ni paramètre.");}
    }
}
