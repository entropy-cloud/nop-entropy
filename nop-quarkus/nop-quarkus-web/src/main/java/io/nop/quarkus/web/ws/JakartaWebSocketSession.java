/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.ws;

import io.nop.graphql.core.ws.IWebSocketSession;
import jakarta.websocket.Session;

import java.io.IOException;

public class JakartaWebSocketSession implements IWebSocketSession {

    private final Session session;

    public JakartaWebSocketSession(Session session) {
        this.session = session;
        // 2.3-C8-009（plan 2551 裁定 J③，closure audit Major-3 接线闭环）：适配器层 idle 超时——
        // 与 handler 侧 keepAlive 扫描判定（nop.graphql.ws.idle-timeout）互补；jakarta WebSocket
        // 容器均支持 setMaxIdleTimeout。容器 0 值（永不超时）时显式设置平台键值，否则保持容器缺省
        // 并 WARN 留痕（无静默跳过）。
        long configuredMillis = io.nop.graphql.core.GraphQLConfigs
                .CFG_GRAPHQL_WS_IDLE_TIMEOUT_SECONDS.get() == null ? 0
                : io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_WS_IDLE_TIMEOUT_SECONDS.get() * 1000L;
        if (configuredMillis > 0) {
            session.setMaxIdleTimeout(configuredMillis);
        } else {
            org.slf4j.LoggerFactory.getLogger(JakartaWebSocketSession.class)
                    .warn("nop.warn.websocket.adapter-idle-timeout-unsupported:keepAliveScanOnly=true");
        }
    }

    @Override
    public void sendMessage(String message) throws IOException {
        session.getBasicRemote().sendText(message);
    }

    @Override
    public void close(short statusCode, String reason) {
        try {
            session.close(new jakarta.websocket.CloseReason(
                    jakarta.websocket.CloseReason.CloseCodes.getCloseCode(statusCode), reason));
        } catch (IOException expected) {
            // close 失败为容器回收竞态的预期形态：会话已判定关闭，无需处理（lint no-empty-catch 注记）
        }
    }

    @Override
    public boolean isClosed() {
        return !session.isOpen();
    }
}
