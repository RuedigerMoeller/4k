package org.nustaq.netty2go.webserver;

import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.nustaq.netty2go.NettyWSHttpServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.function.Function;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by ruedi on 25.05.14.
 */
public class WebSocketHttpServer {

    protected final AttributeKey<ClientSession> session = AttributeKey.valueOf("session");
    public static Logger logger = Logger.getLogger(WebSocketHttpServer.class.getName());

    protected File contentRoot;

	Function<File,File> fileMapper;
	Function<File,byte[]> virtualfileMapper;

    private boolean autoFlush = true;

    public WebSocketHttpServer(File contentRoot) {
        this.contentRoot = contentRoot;
    }

    public boolean isAutoFlush() {
        return autoFlush;
    }

    /**
     * if set to true, a flsh is triggered after each call to sendMessage. Default is true.
     * @param autoFlush
     */
    public void setAutoFlush(boolean autoFlush) {
        this.autoFlush = autoFlush;
    }

	public Function<File, File> getFileMapper() {
		return fileMapper;
	}

	public void setFileMapper(Function<File, File> fileMapper) {
		this.fileMapper = fileMapper;
	}

    public void setVirtualfileMapper(Function<File, byte[]> virtualfileMapper) {
        this.virtualfileMapper = virtualfileMapper;
    }

//////////////////////////////////////////////////////////////////////////

    // callbacks from netty. FIXME: make interface

    public void onOpen(ChannelHandlerContext ctx) {
        ctx.attr(session).set(createNewSession());
        logger.info("onOpen: " + ctx.attr(session).get());
    }

    public void onClose(ChannelHandlerContext ctx) {
        logger.info("close session on:" + ctx.attr(session).get() + " closed.");
    }

    public void onHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req, NettyWSHttpServer.HttpResponseSender sender) {
        logger.info("request on:" + ctx.attr(session));

        if ( req.getMethod() != HttpMethod.GET ) {
            sender.sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
        }

        serveFile(ctx, req, sender);
    }

    public void onTextMessage(ChannelHandlerContext ctx, String text) {
        logger.info("text on:" + ctx.attr(session) + " '" + text + "'");
        sendWSTextMessage(ctx, text);
    }

    public void onBinaryMessage(ChannelHandlerContext ctx, byte[] buffer) {
        logger.info("binary on:" + ctx.attr(session));
    }

    public void onPong(ChannelHandlerContext ctx) {
        logger.info("pong on:" + ctx.attr(session));
    }


    // end callback from netty
    /////////////////////////////////////////////////////////////////////////////

    // rudimentary session management

    protected ClientSession getSession(ChannelHandlerContext ctx) {
        return ctx.attr(session).get();
    }

    protected ClientSession createNewSession() {
        ClientSession clientSession = new ClientSession(){};
        logger.info("created session "+clientSession);
        return clientSession;
    }

    // utils

    public void sendWSTextMessage( ChannelHandlerContext ctx, String s ) {
        ctx.channel().write(new TextWebSocketFrame(s));
        if (autoFlush) {
            ctx.channel().flush();
        }
    }

    public void sendWSBinaryMessage(ChannelHandlerContext ctx, byte b[], int off, int len) {
        ctx.channel().write(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(b, off, len)));
        if (autoFlush) {
            ctx.channel().flush();
        }
    }

    public void sendWSPingMessage(ChannelHandlerContext ctx) {
        ctx.channel().writeAndFlush(new PingWebSocketFrame());
    }

    public void sendWSBinaryMessage(ChannelHandlerContext ctx, byte b[]) {
        sendWSBinaryMessage(ctx, b, 0, b.length);
    }

    public void sendHttpGetResponse(ChannelHandlerContext ctx, FullHttpRequest req, NettyWSHttpServer.HttpResponseSender sender, String response) {
        ByteBuf content = Unpooled.copiedBuffer(response, CharsetUtil.US_ASCII);

        FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
        res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        setContentLength(res, content.readableBytes());

        sender.sendHttpResponse(ctx, req, res);
    }

    public void serveFile(ChannelHandlerContext ctx, FullHttpRequest req, NettyWSHttpServer.HttpResponseSender sender) {
        String resource = req.getUri().toString();
        if (resource==null||resource.trim().length()==0||resource.trim().equals("/"))
            resource = "/index.html";
        File target = new File(contentRoot, File.separator + resource);
	    target = mapFileName(target);
        byte b[] = readVirtualFile(target);
        if ( b != null || (target.exists() && target.isFile()) ) {
            FileChannel inChannel = null;
            RandomAccessFile aFile = null;
            try {
                FullHttpResponse res;
                ByteBuf content;
                if ( b == null ) {
                    aFile = new RandomAccessFile(target, "r");
                    inChannel = aFile.getChannel();
                    long fileSize = inChannel.size();
                    ByteBuffer buffer = ByteBuffer.allocate((int) fileSize); // FIXME: reuse ..
                    while (inChannel.read(buffer) > 0) {
                    }
                    buffer.flip();
                    content = Unpooled.wrappedBuffer(buffer);
                    res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
                } else {
                    ByteBuffer buffer = ByteBuffer.allocate(b.length);
                    buffer.flip();
                    content = Unpooled.wrappedBuffer(b);
                    res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
                }
                String uri = req.getUri().toLowerCase();
                if ( uri.endsWith(".html") )
                    res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
                else if ( uri.endsWith(".js") )
                    res.headers().set(CONTENT_TYPE, "application/x-javascript; charset=UTF-8");
                else if ( uri.endsWith(".gif") )
                    res.headers().set(CONTENT_TYPE, "image/gif");
                else if ( uri.endsWith(".jpg") )
                    res.headers().set(CONTENT_TYPE, "image/jpeg");
                else if ( uri.endsWith(".jpeg") )
                    res.headers().set(CONTENT_TYPE, "image/jpeg");
                else if ( uri.endsWith(".png") )
                    res.headers().set(CONTENT_TYPE, "image/png");
                else if ( uri.endsWith(".css") )
                    res.headers().set(CONTENT_TYPE, "text/css; charset=UTF-8");


//                String accept = req.headers().get("Accept");
//                if ( accept.indexOf("text/html") >= 0 )
//                    res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
//                else {
//                    String[] split = accept.split(";");
//                    boolean exit = false;
//                    for (int i = 0; i < split.length; i++) {
//                        String s = split[i];
//                        String ss[] = s.split(",");
//                        for (int j = 0; j < ss.length; j++) {
//                            String s1 = ss[j];
//                            if ( s1.indexOf('*') < 0 ) {
//                                res.headers().set(CONTENT_TYPE, s1+"; charset=UTF-8");
//                                exit = true;
//                                break;
//                            }
//                        }
//                        if ( exit )
//                            break;
//                    }
//                }
                setContentLength(res, content.readableBytes());
                sender.sendHttpResponse(ctx, req, res);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if ( inChannel != null )
                        inChannel.close();
                    if ( aFile != null )
                            aFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
            sender.sendHttpResponse(ctx, req, res);
        } else {
            FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
            sender.sendHttpResponse(ctx, req, res);
        }
    }

    /**
     * hook for file generation
     * @param target
     * @return
     */
    protected byte[] readVirtualFile(File target) {
        if ( virtualfileMapper != null ) {
            return virtualfileMapper.apply(target);
        }
        return null;
    }

    /**
	 * tweak path (can be helpful for development to avoid copying js/css stuff)
	 * @param target
	 * @return
	 */
	protected File mapFileName(File target) {
		if ( fileMapper != null ) {
			return fileMapper.apply(target);
		}
		return target;
	}

	public void removeSession(ChannelHandlerContext ctx) {
        ctx.attr(session).remove();
    }

    // main

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8887;
        }
        new NettyWSHttpServer(port, new WebSocketHttpServer(new File(".") )).run();
    }

}
