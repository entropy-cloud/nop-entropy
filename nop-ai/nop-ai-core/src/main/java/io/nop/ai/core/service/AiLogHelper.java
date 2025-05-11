package io.nop.ai.core.service;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;

public class AiLogHelper {
    public static IResource getSessionResource(String dir, AiChatExchange request, String postfix) {
        String sessionId = makeSessionId(request);
        String fileName = sessionId + '/' + request.getBeginTime() + '-' + request.getRetryTimes() + '-' + request.getExchangeId() + postfix;
        return new FileResource(new File(dir, fileName));
    }

    static String makeSessionId(AiChatExchange request) {
        String sessionId = request.getChatOptions().getSessionId();
        if (sessionId == null) {
            sessionId = StringHelper.generateUUID();
            request.getChatOptions().setSessionId(sessionId);
        }
        return sessionId;
    }
}
