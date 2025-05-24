package io.nop.ai.core.service;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AiLogHelper {
    public static IResource getSessionResource(String dir, AiChatExchange request, String postfix) {
        String sessionId = makeSessionId(request);
        LocalDate date = CoreMetrics.currentDate();
        String today = date.getYear() + "/" + StringHelper.padInt(date.getMonthValue(), 2)
                + "-" + StringHelper.padInt(date.getDayOfMonth(), 2);
        String fileName = today + '/' + sessionId + '/' + request.getBeginTime() + '-' + request.getRetryTimes() + '-' + request.getExchangeId() + postfix;
        return new FileResource(new File(dir, fileName));
    }

    static String makeSessionId(AiChatExchange request) {
        String sessionId = request.getChatOptions().getSessionId();
        if (sessionId == null) {
            LocalDateTime date = CoreMetrics.currentDateTime();
            String prefix = StringHelper.padInt(date.getHour(), 2) + StringHelper.padInt(date.getMinute(), 2) + StringHelper.padInt(date.getSecond(), 2);
            sessionId = prefix + '-' + StringHelper.generateUUID();
            request.getChatOptions().setSessionId(sessionId);
        }
        return sessionId;
    }
}
