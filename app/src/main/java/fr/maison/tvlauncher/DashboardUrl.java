package fr.maison.tvlauncher;

import java.net.URI;

/** Only a path on the configured HA origin, never tokens, arbitrary schemes or credentials. */
final class DashboardUrl {
    static boolean validPath(String path){return path!=null&&path.matches("/[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*/?");}
    static String resolve(String base,String path){
        if(!validPath(path))throw new IllegalArgumentException("Chemin HA invalide");
        URI u=URI.create(ServerAddress.normalize(base));
        return u.resolve(path).toString();
    }
    static boolean http(URI u){return ("https".equals(u.getScheme())||"http".equals(u.getScheme()))&&u.getHost()!=null;}
    static int port(URI u){return u.getPort()!=-1?u.getPort():"https".equals(u.getScheme())?443:80;}
    static boolean sameOrigin(String origin,String target){try{URI a=URI.create(origin),b=URI.create(target);return http(b)&&b.getUserInfo()==null&&a.getScheme().equals(b.getScheme())&&a.getHost().equalsIgnoreCase(b.getHost())&&port(a)==port(b);}catch(Exception e){return false;}}
}
