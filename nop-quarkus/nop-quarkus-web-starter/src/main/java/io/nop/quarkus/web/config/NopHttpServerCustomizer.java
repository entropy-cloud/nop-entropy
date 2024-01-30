package io.nop.quarkus.web.config;

import io.quarkus.vertx.http.HttpServerOptionsCustomizer;
import io.vertx.core.http.HttpServerOptions;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class NopHttpServerCustomizer implements HttpServerOptionsCustomizer {
    static final Logger LOG = LoggerFactory.getLogger(NopHttpServerCustomizer.class);
    @ConfigProperty(name = "nop.http.netty-server.enable-log", defaultValue = "false")
    boolean enableLog;

    @Override
    public void customizeHttpServer(HttpServerOptions options) {
        doLog(options);
        options.setLogActivity(enableLog);
    }

    void doLog(HttpServerOptions options) {
        LOG.info("nop.run-http-server-customizer:host={},port={},enableLog={}",
                options.getHost(), options.getPort(), enableLog);
    }

    @Override
    public void customizeHttpsServer(HttpServerOptions options) {
        options.setLogActivity(enableLog);
    }

    @Override
    public void customizeDomainSocketServer(HttpServerOptions options) {
        options.setLogActivity(enableLog);
    }
}
