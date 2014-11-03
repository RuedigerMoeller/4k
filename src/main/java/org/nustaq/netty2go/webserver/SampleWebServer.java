package org.nustaq.netty2go.webserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.nustaq.netty2go.NettyWSHttpServer;

import java.io.File;

/**
 * Simple WebSocket + Http 1.1 (GET only) netty based webserver
 */
public class SampleWebServer extends WebSocketHttpServer {

    static class MySession implements ClientSession {
        // your per connection stuff here
    }

    public SampleWebServer(File contentRoot) {
        super(contentRoot);
    }

    @Override public void onClose(ChannelHandlerContext ctx) {
        // example on how to obtain websocket connection session
        MySession sess = (MySession) getSession(ctx);
        // clean up ..
    }

    @Override public void onTextMessage(ChannelHandlerContext ctx, String text) {
        sendWSTextMessage(ctx, "Hello "+text); // just send back
    }

    @Override public void onBinaryMessage(ChannelHandlerContext ctx, byte[] buffer) {  }

    @Override public void onHttpRequest(
            ChannelHandlerContext ctx,
            FullHttpRequest req, NettyWSHttpServer.HttpResponseSender sender) {
        // default is static file serving from contentRoot given in constructor
        super.onHttpRequest(ctx, req, sender);
    }

    @Override protected ClientSession createNewSession() {
        return new MySession();
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8887;
        }
        new NettyWSHttpServer(port, new SampleWebServer(new File(".") )).run();
    }

}
