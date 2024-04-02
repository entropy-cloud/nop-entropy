/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.utils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.nop.netty.NopNettyConstants;
import io.nop.netty.config.NettyBaseConfig;
import io.nop.netty.handlers.TestFragmentChannelHandler;
import io.nop.netty.ssl.ISslEngineFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

public class NettyChannelHelper {
    public static void addCommonChannelHandler(Channel channel,
                                               NettyBaseConfig config, boolean clientMode,
                                               ISslEngineFactory sslEngineFactory) {
        ChannelPipeline pipeline = channel.pipeline();
        if (config.isUseSsl() && config.getSsl() != null) {
            SSLEngine sslEngine = sslEngineFactory.newSslEngine(config.getSsl());
            sslEngine.setUseClientMode(clientMode);
            pipeline.addLast(NopNettyConstants.HANDLER_SSL, new SslHandler(sslEngine));
        }

        if (config.getLogLevel() != null) {
            pipeline.addLast(NopNettyConstants.HANDLER_LOG, new LoggingHandler(config.getLogLevel()));
        }


        if (config.getReadIdleTimeout() > 0) {
            pipeline.addLast(NopNettyConstants.HANDLER_READ_IDLE_TIMEOUT,
                    new ReadTimeoutHandler(config.getReadIdleTimeout(), TimeUnit.MILLISECONDS));
        }

        if (config.getWriteIdleTimeout() > 0) {
            pipeline.addLast(NopNettyConstants.HANDLER_WRITE_IDLE_TIMEOUT,
                    new WriteTimeoutHandler(config.getWriteIdleTimeout(), TimeUnit.MILLISECONDS));
        }

        if (config.isTestFragment()) {
            pipeline.addLast(NopNettyConstants.HANDLER_TEST_FRAGMENT, TestFragmentChannelHandler.INSTANCE);
        }
    }
}
