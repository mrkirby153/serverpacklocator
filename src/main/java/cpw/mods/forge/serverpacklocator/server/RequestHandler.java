package cpw.mods.forge.serverpacklocator.server;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.security.x509.X500Name;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.net.ssl.SSLException;

class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final ServerSidedPackHandler serverSidedPackHandler;
    private static final Logger LOGGER = LogManager.getLogger();

    RequestHandler(final ServerSidedPackHandler serverSidedPackHandler) {
        this.serverSidedPackHandler = serverSidedPackHandler;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        if (Objects.equals(HttpMethod.GET, msg.method())) {
            handleGet(ctx, msg);
        } else {
            buildReply(ctx, msg, HttpResponseStatus.BAD_REQUEST, "text/plain", "Bad request");
        }
    }
    private void handleGet(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        if (Objects.equals("/servermanifest.json", msg.uri())) {
            LOGGER.info("Manifest request for client {}", ctx.channel().remoteAddress());
            final String s = serverSidedPackHandler.getFileManager().buildManifest();
            buildReply(ctx, msg, HttpResponseStatus.OK, "application/json", s);
        } else if (msg.uri().startsWith("/files/")) {
            String fileName = LamdbaExceptionUtils.uncheck(()->URLDecoder.decode(msg.uri().substring(7), StandardCharsets.UTF_8.name()));

            boolean client = msg.headers().get("X-Mod-Type", "common").equals("client");

            byte[] file = serverSidedPackHandler.getFileManager().findFile(fileName, client);
            if (file == null) {
                LOGGER.debug("Requested file {} not found", fileName);
                build404(ctx, msg);
            } else {
                buildFileReply(ctx, msg, fileName, file);
            }
        } else {
            LOGGER.debug("Failed to understand message {}", msg);
            build404(ctx, msg);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            if (((SslHandshakeCompletionEvent) evt).isSuccess()) {
                SslHandler sslhandler = (SslHandler) ctx.channel().pipeline().get("ssl");
                try {
                    X500Name name = (X500Name) sslhandler.engine().getSession().getPeerCertificateChain()[0].getSubjectDN();
                    LOGGER.debug("Connection from {} @ {}", name.getCommonName(), ctx.channel().remoteAddress());
                    if (!WhitelistValidator.validate(name.getCommonName())) {
                        LOGGER.warn("Disconnecting connection from non-whitelisted player {}", name.getCommonName());
                        ctx.close();
                    }
                } catch (IOException e) {
                    LOGGER.warn("Illegal state in connection", e);
                    ctx.close();
                }
            } else {
                LOGGER.warn("Disconnected unauthenticated peer at {} : {}", ctx.channel().remoteAddress(), ((SslHandshakeCompletionEvent) evt).cause().getMessage());
            }
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (!(cause.getCause() instanceof SSLException)) {
            LOGGER.warn("Error in request handler code", cause);
        } else {
            LOGGER.trace("SSL error in handling code", cause.getCause());
        }
    }

    private void build404(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        buildReply(ctx, msg, HttpResponseStatus.NOT_FOUND, "text/plain", "Not Found");
    }

    private void buildReply(final ChannelHandlerContext ctx, final FullHttpRequest msg, final HttpResponseStatus status, final String contentType, final String message) {
        final ByteBuf content = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        HttpUtil.setKeepAlive(resp, HttpUtil.isKeepAlive(msg));
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        HttpUtil.setContentLength(resp, content.writerIndex());
        ctx.writeAndFlush(resp);
    }

    private void buildFileReply(final ChannelHandlerContext ctx, final FullHttpRequest msg, final String fileName, final byte[] file) {
        final ByteBuf content = Unpooled.copiedBuffer(file);
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
        HttpUtil.setKeepAlive(resp, HttpUtil.isKeepAlive(msg));
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        resp.headers().set("filename", fileName);
        HttpUtil.setContentLength(resp, content.writerIndex());
        ctx.writeAndFlush(resp);
    }
}
