package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static io.nop.ai.core.NopAiCoreErrors.ARG_SESSION_ID;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SESSION_ID_IS_EMPTY;

public class ChatLogHelper {

    /**
     * Same allow-list as nop-ai-agent's
     * {@code SessionIds.requireValidIdentifier} (MA6.5-AR-9): the caller-
     * supplied sessionId is embedded into the log file path, so any character
     * outside [A-Za-z0-9_-] would allow path traversal outside the log dir.
     */
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

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
        requireValidSessionId(sessionId);
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
        requireValidSessionId(sessionId);
        return sessionId;
    }

    static void requireValidSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NopException(ERR_AI_SESSION_ID_IS_EMPTY);
        }
        if (!SAFE_SESSION_ID.matcher(sessionId).matches()) {
            throw new NopException(ERR_AI_SESSION_ID_INVALID)
                    .param(ARG_SESSION_ID, sessionId);
        }
    }
}
