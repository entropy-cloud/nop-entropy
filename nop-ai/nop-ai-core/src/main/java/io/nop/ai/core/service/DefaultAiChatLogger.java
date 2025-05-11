package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.IAiChatLogger;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.persist.DefaultAiChatExchangePersister;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAiChatLogger implements IAiChatLogger {
    static final Logger LOG = LoggerFactory.getLogger(DefaultAiChatLogger.class);

    private String logDir;

    @InjectValue("@cfg:nop.ai.service.log-dir|")
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    @Override
    public void logRequest(AiChatExchange request) {
        AiMessage message = request.getPrompt().getLastMessage();
        LOG.info("request:role={},content=\n{}", message.getRole(), message.getContent());

        if (!StringHelper.isEmpty(logDir)) {
            IResource resource = getResource(request, "-request.md");
            DefaultAiChatExchangePersister.instance().save(resource, request);
        }
    }

    @Override
    public void logResponse(AiChatExchange response) {
        LOG.info("response:promptTokens={},completionTokens={},content=\n{}",
                response.getPromptTokens(), response.getCompletionTokens(), response.getContent());

        if (!StringHelper.isEmpty(logDir)) {
            IResource resource = getResource(response, "-response.md");
            ResourceHelper.writeText(resource, response.getContent(), "UTF-8");
        }
    }


    protected IResource getResource(AiChatExchange exchange, String postfix) {
        return AiLogHelper.getSessionResource(logDir, exchange, postfix);
    }
}