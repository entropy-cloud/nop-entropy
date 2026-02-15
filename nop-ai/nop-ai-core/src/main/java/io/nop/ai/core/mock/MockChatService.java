/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.mock;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import static io.nop.ai.core.mock.MockChatConfigs.CFG_AI_MOCK_ENABLE_STREAM;
import static io.nop.ai.core.mock.MockChatConfigs.CFG_AI_MOCK_STREAM_DELAY_MS;

/**
 * Mock实现的IChatService。
 * 通过IResponseProvider获取响应，支持文件系统和内存两种模式。
 */
public class MockChatService implements IChatService {

    private IResponseProvider responseProvider;

    public MockChatService() {
    }

    @Inject
    public void setResponseProvider(IResponseProvider responseProvider) {
        this.responseProvider = responseProvider;
    }

    @Override
    public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
        if (responseProvider == null) {
            throw new IllegalStateException("ResponseProvider is not set");
        }
        return responseProvider.awaitResponse(request, cancelToken);
    }

    @Override
    public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
        boolean enableStream = CFG_AI_MOCK_ENABLE_STREAM.get();
        long streamDelayMs = CFG_AI_MOCK_STREAM_DELAY_MS.get();

        SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();

        responseProvider.awaitResponse(request, cancelToken).whenComplete((response, error) -> {
            if (error != null) {
                publisher.closeExceptionally(error);
                return;
            }

            if (!enableStream || response.getMessage() == null) {
                // 非流式模式，直接发送完整响应
                ChatStreamChunk chunk = createChunk(response, true);
                publisher.submit(chunk);
                publisher.close();
                return;
            }

            // 流式模式，逐字符发送
            String content = response.getMessage().getContent();
            if (content == null || content.isEmpty()) {
                ChatStreamChunk chunk = createChunk(response, true);
                publisher.submit(chunk);
                publisher.close();
                return;
            }

            // 在后台线程中模拟流式输出
            Thread streamThread = new Thread(() -> {
                try {
                    StringBuilder built = new StringBuilder();
                    for (int i = 0; i < content.length(); i++) {
                        if (cancelToken != null && cancelToken.isCancelled()) {
                            publisher.close();
                            return;
                        }

                        built.append(content.charAt(i));

                        ChatAssistantMessage msg = new ChatAssistantMessage();
                        msg.setContent(built.toString());

                        ChatStreamChunk chunk = new ChatStreamChunk();
                        chunk.setContent(String.valueOf(content.charAt(i)));
                        chunk.setRole("assistant");

                        boolean isLast = (i == content.length() - 1);
                        if (isLast) {
                            chunk.setFinishReason(response.getFinishReason());
                        }

                        publisher.submit(chunk);

                        if (streamDelayMs > 0 && !isLast) {
                            Thread.sleep(streamDelayMs);
                        }
                    }
                    publisher.close();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    publisher.closeExceptionally(e);
                }
            });
            streamThread.setDaemon(true);
            streamThread.start();
        });

        return publisher;
    }

    protected ChatStreamChunk createChunk(ChatResponse response, boolean isLast) {
        ChatStreamChunk chunk = new ChatStreamChunk();
        chunk.setRole("assistant");

        if (response.getMessage() != null) {
            chunk.setContent(response.getMessage().getContent());
            chunk.setThinking(response.getMessage().getThink());
        }

        if (isLast) {
            chunk.setFinishReason(response.getFinishReason());
        }
        return chunk;
    }
}
