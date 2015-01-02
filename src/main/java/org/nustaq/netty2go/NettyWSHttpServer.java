package org.nustaq.netty2go;

import org.nustaq.netty2go.webserver.WebSocketHttpServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;


public class NettyWSHttpServer {

    public static int MAX_REQ_CONTENT_LENGTH = 1024*1024;
    public static Logger logger = Logger.getLogger(WebSocketServerHandler.class.getName());
    public static String WEBSOCKET_PATH = "/websocket";

    private int port;
    private WebSocketHttpServer httpReceiver;

    public NettyWSHttpServer(WebSocketHttpServer receiver) {
        httpReceiver = receiver;
    }

    public NettyWSHttpServer(int port, WebSocketHttpServer receiver) {
        this.port = port;
        httpReceiver = receiver;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public WebSocketHttpServer getHttpReceiver() {
        return httpReceiver;
    }

    public void setHttpReceiver(WebSocketHttpServer httpReceiver) {
        this.httpReceiver = httpReceiver;
    }

    public void run() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebSocketServerInitializer());

            Channel ch = b.bind(port).sync().channel();
            logger.info("Web socket server started at port " + port + '.');

            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("codec-http", new HttpServerCodec());
            pipeline.addLast("aggregator", new HttpObjectAggregator(MAX_REQ_CONTENT_LENGTH));
            WebSocketServerHandler handler = new WebSocketServerHandler();
            pipeline.addLast("handler", handler);
        }
    }

    public static interface HttpResponseSender {
        public void sendHttpResponse(
                ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res);

    }

    public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> implements HttpResponseSender {

        private WebSocketServerHandshaker handshaker;

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpRequest) {
                handleHttpRequest(ctx, (FullHttpRequest) msg);
            } else
            if (msg instanceof WebSocketFrame) {
                handleWebSocketFrame(ctx, (WebSocketFrame) msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        private void handleHttpRequest(final ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            // FIXME: 'maekitalo' missing release (see ReferenceCountUtils.release)
            // Handle a bad request.
            if (!req.getDecoderResult().isSuccess()) {
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
                return;
            }

            if (!WEBSOCKET_PATH.equals(req.getUri())) {
                httpReceiver.onHttpRequest(ctx,req, this);
                return;
            }

            // Handshake
            WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                    getWebSocketLocation(req), null, false);
            handshaker = wsFactory.newHandshaker(req);
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            } else {
                handshaker.handshake(ctx.channel(), req).addListener(
                    new ChannelFutureListener() {
                        public void operationComplete(ChannelFuture future) {
                            httpReceiver.onOpen(ctx);
                        }
                    }
                );
            }
        }

        ConcurrentHashMap<ChannelHandlerContext,byte[]> fragemntedBufs = new ConcurrentHashMap<>();
        private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            // Check for closing frame
            if (frame instanceof CloseWebSocketFrame) {
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
                httpReceiver.onClose(ctx);
                return;
            }
            if (frame instanceof PingWebSocketFrame) {
                ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
                return;
            }
            if (frame instanceof PongWebSocketFrame) {
                httpReceiver.onPong(ctx);
                return;
            }
            if (frame instanceof BinaryWebSocketFrame || frame instanceof ContinuationWebSocketFrame) {
                ByteBuf rawMessage = frame.content(); // fixme: add bytebuf based handlers as well to avoid alloc+copy
                int size = rawMessage.readableBytes();
                byte[] buffer = new byte[size];
                rawMessage.readBytes(buffer);
                if ( ! frame.isFinalFragment() ) {
                    System.out.println("nonfinal binary frame ----------------------------------------------------------------");
                    byte[] bytes = fragemntedBufs.get(ctx);
                    if (bytes!=null) {
                        byte combined[] = new byte[bytes.length+buffer.length];
                        System.arraycopy(bytes,0,combined,0,bytes.length);
                        System.arraycopy(buffer,0,combined,bytes.length,buffer.length);
                        fragemntedBufs.put(ctx,combined);
                    } else
                        fragemntedBufs.put(ctx,buffer);
                } else {
                    byte[] bytes = fragemntedBufs.get(ctx);
                    if ( bytes != null ) {
                        byte combined[] = new byte[bytes.length+buffer.length];
                        System.arraycopy(bytes,0,combined,0,bytes.length);
                        System.arraycopy(buffer,0,combined,bytes.length,buffer.length);
                        fragemntedBufs.remove(ctx);
                        System.out.println("continued binary frame finished ----------------------------------------------------------------");
                        httpReceiver.onBinaryMessage(ctx, combined);
                    } else
                        httpReceiver.onBinaryMessage(ctx, buffer);
                }
                return;
            }
            if (frame instanceof TextWebSocketFrame) {
                httpReceiver.onTextMessage(ctx, ((TextWebSocketFrame) frame).text());
                return;
            }
            // Fixme: ContinuationWebSocketFrame needs to be handled as well (BinaryWebSocketFrame has flag)
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
                    .getName()));
        }

        public void sendHttpResponse(
            ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
            // Generate an error page if response getStatus code is not OK (200).
            if (res.getStatus().code() != 200) {
                ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
                res.content().writeBytes(buf);
                buf.release();
                setContentLength(res, res.content().readableBytes());
            }

            // Send the response and close the connection if necessary.
            ChannelFuture f = ctx.channel().writeAndFlush(res);
            if (!isKeepAlive(req) || res.getStatus().code() != 200) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }

        private String getWebSocketLocation(FullHttpRequest req) {
            return "ws://" + req.headers().get(HOST) + WEBSOCKET_PATH;
        }
    }

}
