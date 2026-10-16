/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.ws;

import io.nop.graphql.core.ws.IWebSocketHandler;
import io.nop.xlang.debug.web.DebugGatewayRuntime;
import io.nop.xlang.debug.web.DebugWebSocketHandler;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

/**
 * XLang 调试 WS 端点薄壳（D1 §3.2，plan 2471 B3 裁定落宿主模块）：从握手 URL 查询参数
 * 抽取 {@code nopDebugToken} 传入网关核心（核心不做容器 API 依赖），复用
 * {@link JsonRpcWebSocketEndpoint} 的 jakarta Endpoint 结构。网关未启用
 * （{@code nop.xlang.debug-web.enabled=false}，runtime 单例为 null——quarkus 容器扫描
 * 态端点恒注册）时 onOpen 直接按 NORMAL_CLOSURE 关闭。
 */
@ApplicationScoped
@ServerEndpoint("/ws/xlang-debugger")
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

        JakartaWebSocketSession wsSession = new JakartaWebSocketSession(session);
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
