package io.nop.netty.ssl;

import io.nop.netty.config.SslConfig;

import javax.net.ssl.SSLEngine;

public interface ISslEngineFactory {
    SSLEngine newSslEngine(SslConfig sslConfig);
}
