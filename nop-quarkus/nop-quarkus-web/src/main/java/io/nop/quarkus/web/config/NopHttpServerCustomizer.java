/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.config;

import io.quarkus.vertx.http.HttpServerConfigCustomizer;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.net.LogConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class NopHttpServerCustomizer implements HttpServerConfigCustomizer {
    static final Logger LOG = LoggerFactory.getLogger(NopHttpServerCustomizer.class);
    @ConfigProperty(name = "nop.http.netty-server.enable-log", defaultValue = "false")
    boolean enableLog;

    // Vert.x 5 / Q4:HttpServerOptions 定制面改为 HttpServerConfig + LogConfig(plan 2431 WI5)
    @Override
    public void customizeHttpServer(HttpServerConfig config) {
        doLog(config);
        config.setLogConfig(new LogConfig().setEnabled(enableLog));
    }

    void doLog(HttpServerConfig config) {
        LOG.info("nop.run-http-server-customizer:host={},port={},enableLog={}",
                config.getTcpHost(), config.getTcpPort(), enableLog);
    }

    @Override
    public void customizeHttpsServer(HttpServerConfig config, io.vertx.core.net.ServerSSLOptions sslOptions) {
        config.setLogConfig(new LogConfig().setEnabled(enableLog));
    }

    @Override
    public void customizeDomainSocketServer(HttpServerConfig config) {
        config.setLogConfig(new LogConfig().setEnabled(enableLog));
    }
}
