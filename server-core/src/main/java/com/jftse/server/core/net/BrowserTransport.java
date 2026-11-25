package com.jftse.server.core.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

/** Optional local browser transport. The existing packet codecs and game handlers remain authoritative. */
public final class BrowserTransport {
    private static final AttributeKey<Boolean> BROWSER = AttributeKey.valueOf("ft.browser.transport");
    private BrowserTransport() {}

    public static boolean isBrowser(Channel channel) {
        return Boolean.TRUE.equals(channel.attr(BROWSER).get());
    }

    public static void bind(ServerBootstrap nativeBootstrap, ChannelInitializer<SocketChannel> nativeInitializer)
            throws InterruptedException {
        final int port = Integer.getInteger("ft.web.port", 0);
        if (port == 0) return;
        final String origin = System.getProperty("ft.web.origin", "http://127.0.0.1:4173");
        nativeBootstrap.clone().childHandler(new ChannelInitializer<SocketChannel>() {
            @Override protected void initChannel(SocketChannel channel) {
                channel.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(8192));
                channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override public void channelRead(ChannelHandlerContext ctx, Object message) {
                        if (message instanceof FullHttpRequest request &&
                                !origin.equals(request.headers().get(HttpHeaderNames.ORIGIN))) {
                            ReferenceCountUtil.release(message);
                            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.FORBIDDEN)).addListener(ChannelFutureListener.CLOSE);
                            return;
                        }
                        ctx.fireChannelRead(message);
                    }
                });
                channel.pipeline().addLast(new WebSocketServerProtocolHandler("/jftse", null, false, 65544));
                channel.pipeline().addLast(new WebSocketFrameAggregator(65544));
                channel.pipeline().addLast(new Frames());
                channel.pipeline().addLast(nativeInitializer);
            }
        }).bind("127.0.0.1", port).sync();
    }

    private static final class Frames extends ChannelDuplexHandler {
        private boolean active;
        @Override public void channelActive(ChannelHandlerContext ctx) {
            // Native channelActive sends the binary FT handshake. Wait until HTTP upgrade finishes.
        }
        @Override public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
            if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete && !active) {
                active = true;
                ctx.channel().attr(BROWSER).set(true);
                ctx.fireChannelActive();
            }
            ctx.fireUserEventTriggered(event);
        }
        @Override public void channelInactive(ChannelHandlerContext ctx) {
            if (active) ctx.fireChannelInactive();
        }
        @Override public void channelRead(ChannelHandlerContext ctx, Object message) {
            if (message instanceof BinaryWebSocketFrame frame && active) {
                ctx.fireChannelRead(frame.content().retain());
                frame.release();
            } else {
                ReferenceCountUtil.release(message);
                ctx.close();
            }
        }
        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
            ctx.write(message instanceof ByteBuf bytes ? new BinaryWebSocketFrame(bytes) : message, promise);
        }
        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { ctx.close(); }
    }
}
