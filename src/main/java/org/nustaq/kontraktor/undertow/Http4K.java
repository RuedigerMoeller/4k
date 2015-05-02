package org.nustaq.kontraktor.undertow;

import org.nustaq.kontraktor.Actor;
import org.nustaq.kontraktor.remoting.http.HttpObjectSocket;
import org.nustaq.kontraktor.remoting.http.RestActorClient;
import org.nustaq.kontraktor.remoting.http.RestActorServer;
import org.nustaq.kontraktor.undertow.http.KUndertowHttpServerAdapter;

import java.util.HashMap;
import java.util.function.Function;

/**
 * Created by ruedi on 02.05.2015.
 */
public class Http4K {

    protected HashMap<Integer,Knode> serverMap = new HashMap<>();

    public Function<Integer,Knode> serverFactory = port -> {
        Knode k = new Knode();
        k.mainStub(new String[] {"-p",""+port});
        return k;
    };

    public synchronized Knode getServer( int port ) {
        if (serverMap.get(port) == null) {
            Knode server = serverFactory.apply(port);
            serverMap.put(port,server);
            return server;
        }
        return serverMap.get(port);
    }

    public synchronized Knode getExistingServer( int port ) {
        return serverMap.get(port);
    }

    public void publishHttp( String prefix, String actorName, Actor actor, int port ) {
        publishHttp(prefix,actorName,actor,getServer(port));
    }

    public <T extends Actor> T connectHttp(Class<T> actorClass, String hostName, String path, int port ) {
        RestActorClient<T> cl = new RestActorClient<>(hostName,8080,path,actorClass);
        cl.connect();
        return cl.getFacadeProxy();
    }

    protected RestActorServer installRestActorServer(String pathPrefix, Knode knode) {
        KUndertowHttpServerAdapter sAdapt = new KUndertowHttpServerAdapter(
            knode.getServer(),
            knode.getPathHandler()
        );

        HttpObjectSocket.DUMP_REQUESTS = false;
        RestActorServer restActorServer = new RestActorServer();
        restActorServer.joinServer(pathPrefix, sAdapt);
        return restActorServer;
    }

    protected void publishHttp( String pathPrefix, String actorName, Actor act, Knode knode ) {
        RestActorServer restActorServer = installRestActorServer(pathPrefix, knode);
        restActorServer.publish(actorName,act);
    }

}
