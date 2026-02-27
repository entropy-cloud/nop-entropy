/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.ws;

import io.nop.graphql.core.ws.IWebSocketSession;
import jakarta.websocket.Session;

import java.io.IOException;

public class SpringWebSocketSession implements IWebSocketSession {

    private final Session session;

    public SpringWebSocketSession(Session session) {
        this.session = session;
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
        } catch (IOException ignore) {
        }
    }

    @Override
    public boolean isClosed() {
        return !session.isOpen();
    }
}
