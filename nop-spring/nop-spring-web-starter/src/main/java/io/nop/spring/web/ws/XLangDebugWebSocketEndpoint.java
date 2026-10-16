/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.ws;

import io.nop.graphql.core.ws.IWebSocketHandler;
import io.nop.xlang.debug.web.DebugGatewayRuntime;
import io.nop.xlang.debug.web.DebugWebSocketHandler;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * XLang 调试 WS 端点薄壳（D1 §3.2，plan 2471 B3 裁定落宿主模块）：从握手 URL 查询参数
 * 抽取 {@code nopDebugToken} 传入网关核心，复用 {@link JsonRpcWebSocketEndpoint} 的
 * jakarta Endpoint + ServerEndpointExporter 机制。开关门复用平台调试通道开关
 * {@code nop.xlang.debug-web.enabled}（禁用态语义 = 端点不注册，spring 条件装配）；
 * onOpen 内 runtime 单例判空是容器装配态下的防御性兜底。
 */
@Component
@ServerEndpoint(value = "/ws/xlang-debugger")
@ConditionalOnProperty(name = "nop.xlang.debug-web.enabled", havingValue = "true")
public class XLangDebugWebSocketEndpoint extends Endpoint {

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        DebugGatewayRuntime runtime = DebugGatewayRuntime.singleton();
        if (runtime == null) {
            closeQuietly(session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE,
                    "xlang debug web disabled"));
            return;
        }

        String debugToken = extractQueryParam(session, "nopDebugToken");

        SpringWebSocketSession wsSession = new SpringWebSocketSession(session);
        DebugWebSocketHandler handler = runtime.createHandler(wsSession, debugToken);

        session.getUserProperties().put("xlang-debug.handler", handler);
        session.addMessageHandler(String.class, handler::onMessage);
    }

    static String extractQueryParam(Session session, String paramName) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        if (params == null)
            return null;
        List<String> values = params.get(paramName);
        if (values == null || values.isEmpty())
            return null;
        return values.get(0);
    }

    private void closeQuietly(Session session, CloseReason reason) {
        try {
            session.close(reason);
        } catch (Exception e) {
            // 容器会话已失效：关闭失败无需处理
        }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        IWebSocketHandler handler = (IWebSocketHandler) session.getUserProperties().get("xlang-debug.handler");
        if (handler != null) {
            handler.onClose();
        }
    }

    @Override
    public void onError(Session session, Throwable thr) {
        IWebSocketHandler handler = (IWebSocketHandler) session.getUserProperties().get("xlang-debug.handler");
        if (handler != null) {
            handler.onThrowable(thr);
        }
    }
}
