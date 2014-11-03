package org.nustaq.netty2go;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;

import java.net.URI;

/**
 * Created by ruedi on 29.08.2014.
 *
 * mostly built up from a netty example ..
 *
 */
public class WebSocketClient {


    URI uri;
    EventLoopGroup group;
    Channel channel;
    private volatile boolean connected = false;

    public void connect(String url) throws Exception {
        this.uri = new URI(url);

        String scheme = uri.getScheme() == null? "http" : uri.getScheme();
        final int port;
        if (uri.getPort() == -1) {
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                throw new RuntimeException("ssl currently not supported");
            } else
                port = -1;
        } else {
            port = uri.getPort();
        }

        if (!"ws".equalsIgnoreCase(scheme)) {
            System.err.println("Only WS is supported.");
            return;
        }

        group = new NioEventLoopGroup();
        try {
            final WebSocketClientHandler handler =
                new WebSocketClientHandler(
                    WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()));

            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(
                         new HttpClientCodec(),
                         new HttpObjectAggregator(8192),
                         handler);
                 }
             });

            channel = b.connect(uri.getHost(), port).sync().channel();
            handler.handshakeFuture().sync();
            connected = true;
        } catch (Exception e) {
            if ( group != null )
                close();
            throw e;
        }
    }

    public void sendText(String text) {
        channel.write(new TextWebSocketFrame(text));
    }

//    public void sendPing() {
//        channel.write( new PingWebSocketFrame() );
//    }

    public void sendPong() {
        channel.write( new PongWebSocketFrame() );
    }

    public void sendBinary( byte b[], int off, int len ) {
        channel.write(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(b, off, len)));
    }

    public void flush() {
        channel.flush();
    }

    public void onClose(ChannelHandlerContext ctx) {
    }

    public void onTextMessage(ChannelHandlerContext ctx, String text) {
    }

    public void onBinaryMessage(ChannelHandlerContext ctx, byte[] buffer) {
    }

    public void onPing(ChannelHandlerContext ctx) {
        sendPong();
    }

    public void onPong(ChannelHandlerContext ctx) {
        System.out.println("pong from server"); // afaik should never happpen
    }

    public boolean isConnected() {
        return connected;
    }

    public void close() {
        if ( isConnected() ) {
            sendClose();
        }
        connected = false;
        group.shutdownGracefully();
    }

    public void sendClose() {
        channel.write( new CloseWebSocketFrame() );
    }

    public static void main( String a[]) throws Exception {
        WebSocketClient client = new WebSocketClient();
        client.connect("ws://127.0.0.1:8887/websocket");
    }



    class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        public ChannelFuture handshakeFuture() {
            return handshakeFuture;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            handshakeFuture = ctx.newPromise();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            onClose(ctx);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                System.out.println("WebSocket Client connected!");
                handshakeFuture.setSuccess();
                return;
            }

            if (msg instanceof WebSocketFrame == false) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException( "Unexpected message " + msg );
            }

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                onTextMessage(ctx, textFrame.text());
            } else if (frame instanceof BinaryWebSocketFrame) {
                ByteBuf rawMessage = frame.content();
                int size = rawMessage.readableBytes();
                byte[] buffer = new byte[size];
                rawMessage.readBytes(buffer);
                onBinaryMessage(ctx, buffer );
            } else if (frame instanceof PingWebSocketFrame) {
                onPing(ctx);
            } else if (frame instanceof PongWebSocketFrame) { // should not happen afaik as server sends only ping
                onPong(ctx);
            } else if (frame instanceof CloseWebSocketFrame) {
                onClose(ctx);
                ch.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            if (!handshakeFuture.isDone()) {
                handshakeFuture.setFailure(cause);
            }
            ctx.close();
        }
    }
}
