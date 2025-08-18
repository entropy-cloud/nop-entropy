package io.nop.ai.tools.utils;

import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;

public class AiToolsHelper {
    static final String HEADER_CHAT_SESSION_ID = "nop-chat-session-Id";

    public static String makeChatSessionId(IServiceContext ctx) {
        String sessionId = (String) ctx.getRequestHeader(HEADER_CHAT_SESSION_ID);
        if (StringHelper.isEmpty(sessionId)) {
            sessionId = StringHelper.generateUUID();
        }
        return sessionId;
    }
}
