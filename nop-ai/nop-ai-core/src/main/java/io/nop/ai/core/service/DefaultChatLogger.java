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

public class DefaultChatLogger implements IChatLogger {
    static final Logger LOG = LoggerFactory.getLogger(DefaultChatLogger.class);

    private String logDir;

    @InjectValue("@cfg:nop.ai.service.log-dir|/nop/ai/log")
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    @Override
    public void logRequest(ChatRequest request) {
        ChatMessage message = request.getLastMessage();
        LOG.info("request:role={},content=\n{}", message.getRole(), message.getContent());

        if (isValidLogDir()) {
            IResource resource = getResource(request, "-request.yaml");
            //DefaultAiChatExchangePersister.instance().save(resource, request);
            ResourceHelper.writeText(resource, JSON.serialize(request, true));
        }
    }

    @Override
    public void logResponse(ChatRequest request, ChatResponse response) {
        LOG.info("response:promptTokens={},completionTokens={},content=\n{}",
                response.getPromptTokens(), response.getCompletionTokens(), response.getFullContent());

        if (isValidLogDir()) {
            IResource resource = getResource(request, "-response.yaml");
            ResourceHelper.writeText(resource, JSON.serialize(response, true), "UTF-8");
        }
    }

    boolean isValidLogDir() {
        return !StringHelper.isEmpty(logDir) && !logDir.equals("none");
    }

    protected IResource getResource(ChatRequest request, String postfix) {
        return ChatLogHelper.getSessionResource(logDir, request, postfix);
    }
}