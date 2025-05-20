package io.nop.ai.core.service;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.time.LocalDate;

public class AiLogHelper {
    public static IResource getSessionResource(String dir, AiChatExchange request, String postfix) {
        String sessionId = makeSessionId(request);
        LocalDate date = LocalDate.now();
        String today = date.getYear() + "/" + StringHelper.leftPad(date.getMonthValue() + "", 2, '0')
                + "-" + StringHelper.leftPad(date.getDayOfMonth() + "", 2, '0');
        String fileName = today + '/' + sessionId + '/' + request.getBeginTime() + '-' + request.getRetryTimes() + '-' + request.getExchangeId() + postfix;
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
