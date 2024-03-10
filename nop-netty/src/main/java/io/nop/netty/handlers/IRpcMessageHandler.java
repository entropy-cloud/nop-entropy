/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.handlers;

import io.netty.channel.ChannelHandler;

import java.util.concurrent.CompletableFuture;

public interface IRpcMessageHandler extends ChannelHandler {
    void send(Object msg, long timeout, CompletableFuture<Object> ret);
}
