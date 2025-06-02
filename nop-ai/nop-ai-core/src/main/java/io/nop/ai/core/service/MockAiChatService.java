package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.persist.DefaultAiChatExchangePersister;
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_MOCK_DIR;

public class MockAiChatService {

    static final String MARKER_EOF = "\nNOP_EOF";

    public CompletionStage<AiChatExchange> sendChatAsync(Prompt prompt, AiChatOptions options, ICancelToken cancelToken) {
        return getExecutor().submit(() -> doSend(prompt, options, cancelToken));
    }

    protected AiChatExchange doSend(Prompt prompt, AiChatOptions options, ICancelToken cancelToken) {
        AiChatExchange exchange = new AiChatExchange();
        exchange.setPrompt(prompt);
        exchange.setChatOptions(options);
        exchange.setBeginTime(CoreMetrics.currentTimeMillis());
        exchange.setExchangeId(StringHelper.generateUUID());

        IResource resource = getResource(exchange, "-request.md");
        DefaultAiChatExchangePersister.instance().save(resource, exchange);

        IResource promptResource = getResource(exchange, "-prompt.md");
        ResourceHelper.writeText(promptResource, exchange.getPrompt().getLastMessage().getContent());

        IResource responseResource = getResource(exchange, "-response.md");
        ResourceHelper.writeText(responseResource, "");

        long beginTime = CoreMetrics.currentTimeMillis();

        do {
            String text = ResourceHelper.readText(responseResource);

            int pos = text.indexOf(MARKER_EOF);
            if (pos < 0) {
                long current = CoreMetrics.currentTimeMillis();

                if (expired(current - beginTime, options))
                    throw new NopTimeoutException();

                ThreadHelper.sleep(500);
            } else {
                text = text.substring(0, pos);
                exchange.setContent(text);
                break;
            }

            if (cancelToken != null && cancelToken.isCancelled()) {
                throw new CancellationException("cancelled");
            }
        } while (true);

        return exchange;
    }

    protected boolean expired(long usedTime, AiChatOptions options) {
        // 最多等待2小时
        return usedTime > 1000 * 60 * 60 * 2;
    }

    protected IResource getResource(AiChatExchange exchange, String postfix) {
        return AiLogHelper.getSessionResource(CFG_AI_SERVICE_MOCK_DIR.get(), exchange, postfix);
    }

    protected IThreadPoolExecutor getExecutor() {
        return GlobalExecutors.globalWorker();
    }
}