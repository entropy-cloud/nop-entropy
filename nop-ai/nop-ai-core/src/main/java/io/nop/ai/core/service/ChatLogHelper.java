package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ChatLogHelper {
    public static IResource getSessionResource(String dir, ChatRequest request, String postfix) {
        String sessionId = makeSessionId(request);
        LocalDate date = CoreMetrics.currentDate();
        String today = date.getYear() + "/" + StringHelper.padInt(date.getMonthValue(), 2)
                + "-" + StringHelper.padInt(date.getDayOfMonth(), 2);
        String fileName = today + '/' + sessionId + '/' + request.getRequestTime() + '-' + request.getRetryTimes() + '-' + request.getRequestId() + postfix;
        return new FileResource(new File(dir, fileName));
    }

    /**
     * 兼容旧版 AiChatExchange API
     */
    public static IResource getSessionResource(String dir, AiChatExchange exchange, String postfix) {
        String sessionId = exchange.getExchangeId();
        if (sessionId == null) {
            sessionId = StringHelper.generateUUID();
            exchange.setExchangeId(sessionId);
        }
        LocalDate date = CoreMetrics.currentDate();
        String today = date.getYear() + "/" + StringHelper.padInt(date.getMonthValue(), 2)
                + "-" + StringHelper.padInt(date.getDayOfMonth(), 2);
        String fileName = today + '/' + sessionId + '/' + exchange.getBeginTime() + postfix;
        return new FileResource(new File(dir, fileName));
    }

    static String makeSessionId(ChatRequest request) {
        String sessionId = request.makeOptions().getSessionId();
        if (sessionId == null) {
            LocalDateTime date = CoreMetrics.currentDateTime();
            String prefix = StringHelper.padInt(date.getHour(), 2) + StringHelper.padInt(date.getMinute(), 2) + StringHelper.padInt(date.getSecond(), 2);
            sessionId = prefix + '-' + StringHelper.generateUUID();
            request.getOptions().setSessionId(sessionId);
        }
        return sessionId;
    }
}
