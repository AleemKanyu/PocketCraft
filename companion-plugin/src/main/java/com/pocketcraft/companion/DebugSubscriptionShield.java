package com.pocketcraft.companion;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

final class DebugSubscriptionShield extends ChannelInboundHandlerAdapter {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isDebugSubscriptionDecodeFailure(cause)) {
            return;
        }
        ctx.fireExceptionCaught(cause);
    }

    private static boolean isDebugSubscriptionDecodeFailure(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current.getClass().getName().contains("DecoderException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lowerMsg = message.toLowerCase();
                if (lowerMsg.contains("debug_subscription_request") ||
                    lowerMsg.contains("accept_teleportation") ||
                    lowerMsg.contains("was larger than i expected") ||
                    lowerMsg.contains("bytes extra") ||
                    lowerMsg.contains("decoderexception") ||
                    lowerMsg.contains("book") ||
                    lowerMsg.contains("slot")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
