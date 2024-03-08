package io.nop.netty.handlers;

import io.netty.channel.ChannelHandler;

import java.util.concurrent.CompletableFuture;

public interface IRpcMessageHandler extends ChannelHandler {
    void send(Object msg, long timeout, CompletableFuture<Object> ret);
}
