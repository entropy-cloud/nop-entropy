package io.nop.netty.ssl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.nop.netty.config.SslConfig;

import javax.net.ssl.SSLEngine;

public interface ISslEngineFactory {
    SslContext getSslContext(SslConfig sslConfig, boolean clientMode);

    default SSLEngine newSslEngine(SslConfig sslConfig, boolean clientMode) {
        return getSslContext(sslConfig, clientMode).newEngine(ByteBufAllocator.DEFAULT);
    }
}
