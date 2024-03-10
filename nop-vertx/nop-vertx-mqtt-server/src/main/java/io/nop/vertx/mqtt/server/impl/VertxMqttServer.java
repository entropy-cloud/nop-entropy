/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.vertx.commons.NopVertx;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.nop.vertx.mqtt.server.auth.IMqttAuthChecker;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;

public class VertxMqttServer extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(VertxMqttServer.class);

    private MqttServer mqttServer;

    private MqttServerOptions serverOptions;

    private Future<MqttServer> listenFuture;

    private IMqttAuthChecker authChecker;

    private IMqttHandler mqttHandler;

    private final MqttSessionManager sessionManager = new MqttSessionManager();

    public void setServerOptions(MqttServerOptions serverOptions) {
        this.serverOptions = serverOptions;
    }

    @Inject
    public void setAuthChecker(IMqttAuthChecker authChecker) {
        this.authChecker = authChecker;
    }

    @Inject
    public void setMqttHandler(IMqttHandler mqttHandler) {
        this.mqttHandler = mqttHandler;
    }

    @Override
    protected void doStart() {
        Guard.notNull(serverOptions, "serverOptions");

        mqttServer = MqttServer.create(NopVertx.instance(), serverOptions);
        mqttServer.exceptionHandler(err -> {
            LOG.error("nop.vertx.mqtt.server.start-fail", err);
        }).endpointHandler(this::handleEndpoint);
        listenFuture = mqttServer.listen();
    }

    private void handleEndpoint(MqttEndpoint endpoint) {
        sessionManager.addConnection(new MqttConnection(endpoint, mqttHandler));
    }

    @Override
    protected void doStop() {
        if (mqttServer != null) {
            mqttServer.close(res -> {
                if (res.failed()) {
                    LOG.error("nop.vertx.mqtt.server.stop-fail", res.cause());
                } else {
                    LOG.info("nop.vertx.mqtt.server.stopped:port={}", mqttServer.actualPort());
                }
            });
            mqttServer = null;
            listenFuture = null;
        }
    }
}
