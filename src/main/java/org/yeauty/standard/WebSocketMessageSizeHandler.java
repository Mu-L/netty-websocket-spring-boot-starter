package org.yeauty.standard;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.util.ReferenceCountUtil;

/** Checks decoded frames, including unfragmented messages that bypass aggregation. */
final class WebSocketMessageSizeHandler extends ChannelInboundHandlerAdapter {

    private final int maxLength;

    WebSocketMessageSizeHandler(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        boolean dataFrame = message instanceof TextWebSocketFrame || message instanceof BinaryWebSocketFrame
                || message instanceof ContinuationWebSocketFrame;
        if (dataFrame && ((WebSocketFrame) message).content().readableBytes() > maxLength) {
            ReferenceCountUtil.release(message);
            throw new TooLongFrameException("Decoded WebSocket message exceeds " + maxLength + " bytes");
        }
        ctx.fireChannelRead(message);
    }
}
