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
            String message = current.getMessage();
            if (message != null && message.contains("debug_subscription_request")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
