/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.impl;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.vertx.commons.NopVertx;
import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.nop.vertx.mqtt.server.MqttErrors;
import io.nop.vertx.mqtt.server.auth.IMqttAuthChecker;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import jakarta.inject.Inject;

public class VertxMqttServer extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(VertxMqttServer.class);

    private MqttServer mqttServer;

    private MqttServerOptions serverOptions;

    private Future<MqttServer> listenFuture;

    // MA-14（4.3-MA-14 / plan 2560 裁定 G）：bean 级 Vertx 注入缝——测试可注入
    // 受控 Vertx（如 listen 必败的 stub）；缺省回退 NopVertx.instance()。
    // 不改 NopVertx 全局静态（registerInstance 已存在，勿造全局污染面）。
    private volatile Vertx vertx;

    private IMqttAuthChecker authChecker;

    private IMqttHandler mqttHandler;

    private final MqttSessionManager sessionManager = new MqttSessionManager();

    // 入站 publish 监听（MqttServerMessageService 的 subscribe 在此分发）
    private final List<BiConsumer<MqttPublishMessage, IMqttConnection>> publishListeners = new CopyOnWriteArrayList<>();

    public void setServerOptions(MqttServerOptions serverOptions) {
        this.serverOptions = serverOptions;
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
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

        // MA-14（4.3-MA-14 / plan 2560 裁定 G）：bean 级注入缝优先，缺省回退全局实例
        Vertx vertx = this.vertx != null ? this.vertx : NopVertx.instance();
        mqttServer = MqttServer.create(vertx, serverOptions);
        mqttServer.exceptionHandler(err -> {
            LOG.error("nop.vertx.mqtt.server.start-fail", err);
        }).endpointHandler(this::handleEndpoint);
        listenFuture = mqttServer.listen();
        // C6-028 / MA-14：listenFuture 不得只记录不观察——listen 失败时 doStart 必须抛出
        // （fail-fast），并先关闭已创建的 MqttServer、触发 stop 收敛半启动状态。
        CountDownLatch listenLatch = new CountDownLatch(1);
        AtomicReference<Throwable> listenError = new AtomicReference<>();
        listenFuture.<Void>onFailure(listenError::set).onComplete(res -> listenLatch.countDown());
        try {
            if (!listenLatch.await(30, TimeUnit.SECONDS)) {
                listenError.set(new NopException(MqttErrors.ERR_MQTT_LISTEN_FAIL)
                        .param(MqttErrors.ARG_PORT, serverOptions.getPort())
                        .description("listen timeout"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listenError.set(e);
        }
        Throwable error = listenError.get();
        if (error != null) {
            LOG.error("nop.vertx.mqtt.server.listen-fail", error);
            try {
                mqttServer.close();
            } catch (Exception closeErr) {
                LOG.error("nop.vertx.mqtt.server.close-fail", closeErr);
            }
            doStop();
            throw new NopException(MqttErrors.ERR_MQTT_LISTEN_FAIL, error)
                    .param(MqttErrors.ARG_PORT, serverOptions.getPort());
        }
    }

    private void handleEndpoint(MqttEndpoint endpoint) {
        if (authChecker != null) {
            MqttAuth auth = endpoint.auth();
            String userName = auth != null ? auth.getUsername() : null;
            String password = auth != null ? auth.getPassword() : null;
            authChecker.checkAuthAsync(endpoint.clientIdentifier(), userName, password, null).whenComplete((ok, err) -> {
                if (err != null || !Boolean.TRUE.equals(ok)) {
                    if (err != null) {
                        LOG.error("nop.vertx.mqtt.auth-check-fail", err);
                    } else {
                        LOG.warn("nop.vertx.mqtt.auth-rejected:clientId={}", endpoint.clientIdentifier());
                    }
                    endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USERNAME_OR_PASSWORD);
                } else {
                    acceptEndpoint(endpoint);
                }
            });
        } else {
            // F-N2-1：无 authChecker 时曾 fail-open 全放行。改为拒绝——需要
            // 匿名 MQTT 的部署必须显式装配放行 checker（如 SimpleMqttAuthChecker
            // 配置为全放行），不得默认信任所有连接。
            LOG.warn("nop.vertx.mqtt.no-auth-checker:clientId={}; rejecting connection "
                    + "(configure an IMqttAuthChecker bean to enable MQTT auth)", endpoint.clientIdentifier());
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        }
    }

    public void addPublishListener(BiConsumer<MqttPublishMessage, IMqttConnection> listener) {
        publishListeners.add(listener);
    }

    public void removePublishListener(BiConsumer<MqttPublishMessage, IMqttConnection> listener) {
        publishListeners.remove(listener);
    }

    public MqttSessionManager getSessionManager() {
        return sessionManager;
    }

    private void acceptEndpoint(MqttEndpoint endpoint) {
        MqttConnection connection = new MqttConnection(endpoint, wrapHandler(mqttHandler), sessionManager);
        // 断连后清理会话（含订阅登记），否则会话表随时间无限膨胀
        connection.setOnCloseCallback(() -> sessionManager.removeConnection(connection));

        IMqttConnection old = sessionManager.addConnection(connection);
        if (old != null && old != connection) {
            LOG.info("nop.vertx.mqtt.session-takeover:clientId={}", connection.getClientId());
        }

        // C6-020 窗口①（plan 2523）：accept（CONNACK）先注册后发出——否则客户端在注册前
        // 收到 CONNACK 即发 SUBSCRIBE，订阅先落表、随后 addConnection 接管清空（订阅丢失）。
        // 窗口①修复与 C1-018 close 回调挂载面相邻（close 回调已在 accept 前挂载，不受影响）。
        connection.accept();
    }

    /**
     * 应用 handler 之外叠加进程内 publish 监听（消息总线 subscribe 的分发入口）。
     * 恒包装、分发时读实时列表：晚于连接建立的 subscribe 也能收到分发
     */
    private IMqttHandler wrapHandler(IMqttHandler delegate) {
        return new IMqttHandler() {
            @Override
            public void onClose(IMqttConnection conn) {
                delegate.onClose(conn);
            }

            @Override
            public void onPing() {
                delegate.onPing();
            }

            @Override
            public void onPing(IMqttConnection conn) {
                delegate.onPing(conn);
            }

            @Override
            public void onPublish(MqttPublishMessage msg, IMqttConnection conn) {
                delegate.onPublish(msg, conn);
                for (BiConsumer<MqttPublishMessage, IMqttConnection> listener : publishListeners) {
                    try {
                        listener.accept(msg, conn);
                    } catch (Exception e) {
                        // 单个监听器异常不中断其他监听器与业务 handler
                        LOG.error("nop.mqtt.publish-listener-fail", e);
                    }
                }
            }

            @Override
            public void onSubscribe(MqttSubscribeMessage msg, IMqttConnection conn) {
                delegate.onSubscribe(msg, conn);
            }

            @Override
            public void onUnsubscribe(MqttUnsubscribeMessage msg, IMqttConnection conn) {
                delegate.onUnsubscribe(msg, conn);
            }
        };
    }

    @Override
    protected void doStop() {
        MqttServer server = this.mqttServer;
        if (server != null) {
            // MA-14/C6-028（4.3-MA-14 / plan 2560 裁定 G）：stop 必须等待 close 完成——
            // CountDownLatch + close().onComplete(countDown) + 带超时 await（防永久挂起），
            // 完成后再置空引用并清空会话表（stop→start 不携带旧会话/订阅）。
            CountDownLatch latch = new CountDownLatch(1);
            int port = server.actualPort();
            server.close().onComplete(res -> {
                if (res.failed()) {
                    LOG.error("nop.vertx.mqtt.server.stop-fail", res.cause());
                } else {
                    LOG.info("nop.vertx.mqtt.server.stopped:port={}", port);
                }
                latch.countDown();
            });
            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    LOG.error("nop.vertx.mqtt.server.stop-timeout:port={}", port);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("nop.vertx.mqtt.server.stop-interrupted:port={}", port, e);
            }
            mqttServer = null;
            listenFuture = null;
            // 裁定 G：sessions + subscriptions 两表同清
            sessionManager.clear();
        }
    }
}
