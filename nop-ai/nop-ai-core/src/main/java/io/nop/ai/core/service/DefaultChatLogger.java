package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.IChatLogger;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class DefaultChatLogger implements IChatLogger {
    static final Logger LOG = LoggerFactory.getLogger(DefaultChatLogger.class);

    private static final Pattern[] CREDENTIAL_PATTERNS = {
            Pattern.compile("(api[_-]?key|apikey|secret|token|password|passwd|credential)\\s*[:=]\\s*['\"]?[A-Za-z0-9_\\-./+]{8,}['\"]?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(Authorization|Bearer|X-API-Key)\\s*[:=]\\s*['\"]?\\S+['\"]?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("sk-[A-Za-z0-9]{20,}", Pattern.CASE_INSENSITIVE),
    };

    private String logDir;
    private boolean redactCredentials = true;

    @InjectValue("@cfg:nop.ai.service.log-dir|/nop/ai/log")
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    @InjectValue("@cfg:nop.ai.service.redact-credentials|true")
    public void setRedactCredentials(boolean redactCredentials) {
        this.redactCredentials = redactCredentials;
    }

    @Override
    public void logRequest(ChatRequest request) {
        ChatMessage message = request.getLastMessage();
        String content = message.getContent();
        LOG.info("request:role={},content=\n{}", message.getRole(), redactIfNeeded(content));

        if (isValidLogDir()) {
            IResource resource = getResource(request, "-request.yaml");
            if (redactCredentials) {
                String redacted = JSON.serialize(request, true);
                for (Pattern p : CREDENTIAL_PATTERNS) {
                    redacted = p.matcher(redacted).replaceAll("$1: ***REDACTED***");
                }
                ResourceHelper.writeText(resource, redacted);
            } else {
                ResourceHelper.writeText(resource, JSON.serialize(request, true));
            }
        }
    }

    @Override
    public void logResponse(ChatRequest request, ChatResponse response) {
        LOG.info("response:promptTokens={},completionTokens={},content=\n{}",
                response.getPromptTokens(), response.getCompletionTokens(),
                redactIfNeeded(response.getFullContent()));

        if (isValidLogDir()) {
            IResource resource = getResource(request, "-response.yaml");
            if (redactCredentials) {
                String redacted = JSON.serialize(response, true);
                for (Pattern p : CREDENTIAL_PATTERNS) {
                    redacted = p.matcher(redacted).replaceAll("$1: ***REDACTED***");
                }
                ResourceHelper.writeText(resource, redacted, "UTF-8");
            } else {
                ResourceHelper.writeText(resource, JSON.serialize(response, true), "UTF-8");
            }
        }
    }

    private String redactIfNeeded(String content) {
        if (!redactCredentials || content == null) return content;
        String result = content;
        for (Pattern p : CREDENTIAL_PATTERNS) {
            result = p.matcher(result).replaceAll("***REDACTED***");
        }
        return result;
    }

    boolean isValidLogDir() {
        return !StringHelper.isEmpty(logDir) && !logDir.equals("none");
    }

    protected IResource getResource(ChatRequest request, String postfix) {
        return ChatLogHelper.getSessionResource(logDir, request, postfix);
    }
}