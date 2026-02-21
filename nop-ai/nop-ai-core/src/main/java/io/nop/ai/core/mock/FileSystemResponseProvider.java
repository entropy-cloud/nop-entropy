/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.mock;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.core.mock.MockChatConfigs.CFG_AI_MOCK_DIR;
import static io.nop.ai.core.mock.MockChatConfigs.CFG_AI_MOCK_EOF_MARKER;
import static io.nop.ai.core.mock.MockChatConfigs.CFG_AI_MOCK_POLL_INTERVAL_MS;
import static io.nop.ai.core.mock.MockChatConfigs.CFG_AI_MOCK_TIMEOUT_HOURS;

/**
 * 基于文件系统的响应提供者实现。
 * 通过轮询文件来等待响应，适用于手动构造响应的调试场景。
 */
public class FileSystemResponseProvider implements IResponseProvider, IRequestStore {

    private IThreadPoolExecutor executor;

    public FileSystemResponseProvider() {
        this.executor = GlobalExecutors.cachedThreadPool();
    }

    @Inject
    public void setExecutor(IThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Override
    public CompletionStage<ChatResponse> awaitResponse(ChatRequest request, ICancelToken cancelToken) {
        return CompletableFuture.supplyAsync(() -> doAwaitResponse(request, cancelToken), executor);
    }

    protected ChatResponse doAwaitResponse(ChatRequest request, ICancelToken cancelToken) {
        String eofMarker = CFG_AI_MOCK_EOF_MARKER.get();
        long pollIntervalMs = CFG_AI_MOCK_POLL_INTERVAL_MS.get();
        long timeoutHours = CFG_AI_MOCK_TIMEOUT_HOURS.get();
        long timeoutMs = timeoutHours * 60 * 60 * 1000;

        String requestId = saveRequest(request);
        IResource responseResource = getResponseResource(requestId);

        long beginTime = CoreMetrics.currentTimeMillis();
        StringBuilder contentBuilder = new StringBuilder();

        do {
            String text = ResourceHelper.readText(responseResource);

            if (text != null) {
                int pos = text.indexOf(eofMarker);
                if (pos >= 0) {
                    contentBuilder.append(text, 0, pos);
                    return buildResponse(contentBuilder.toString(), request);
                }
                contentBuilder.setLength(0);
                contentBuilder.append(text);
            }

            long current = CoreMetrics.currentTimeMillis();
            if (current - beginTime > timeoutMs) {
                throw new IllegalStateException("Mock response timeout after " + timeoutHours + " hours");
            }

            if (cancelToken != null && cancelToken.isCancelled()) {
                throw new CancellationException("Request cancelled");
            }

            ThreadHelper.sleep(pollIntervalMs);
        } while (true);
    }

    @Override
    public String saveRequest(ChatRequest request) {
        String requestId = generateRequestId(request);

        IResource requestResource = getRequestResource(requestId);
        ResourceHelper.writeText(requestResource, serializeRequest(request));

        IResource promptResource = getPromptResource(requestId);
        String promptText = request.getLastUserPrompt();
        if (promptText != null) {
            ResourceHelper.writeText(promptResource, promptText);
        }

        IResource responseResource = getResponseResource(requestId);
        if (!responseResource.exists()) {
            ResourceHelper.writeText(responseResource, "");
        }

        return requestId;
    }

    @Override
    public String getRequestPrompt(String requestId) {
        IResource promptResource = getPromptResource(requestId);
        return ResourceHelper.readText(promptResource);
    }

    protected ChatResponse buildResponse(String content, ChatRequest request) {
        ChatResponse response = new ChatResponse();

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(content);
        response.setMessage(message);

        ChatOptions options = request.getOptions();
        if (options != null) {
            response.setModel(options.getModel());
        }

        response.setFinishReason("stop");
        response.setResponseTime(CoreMetrics.currentTimeMillis());

        return response;
    }

    protected String serializeRequest(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Chat Request\n");
        sb.append("requestId: ").append(request.getRequestId()).append("\n");
        sb.append("retryTimes: ").append(request.getRetryTimes()).append("\n");
        sb.append("requestTime: ").append(request.getRequestTime()).append("\n");

        if (request.getOptions() != null) {
            sb.append("\n## Options\n");
            ChatOptions options = request.getOptions();
            if (options.getModel() != null) {
                sb.append("model: ").append(options.getModel()).append("\n");
            }
            if (options.getTemperature() != null) {
                sb.append("temperature: ").append(options.getTemperature()).append("\n");
            }
        }

        sb.append("\n## Messages\n");
        if (request.getMessages() != null) {
            request.getMessages().forEach(msg -> {
                sb.append("[").append(msg.getRole()).append("] ")
                        .append(msg.getContent()).append("\n\n");
            });
        }

        return sb.toString();
    }

    protected String generateRequestId(ChatRequest request) {
        if (StringHelper.isEmpty(request.getRequestId())) {
            request.setRequestId(StringHelper.generateUUID());
        }
        return request.getRequestId();
    }

    protected IResource getRequestResource(String requestId) {
        Path path = buildPath(requestId, "-request.md");
        return new FileResource(path.toFile());
    }

    protected IResource getPromptResource(String requestId) {
        Path path = buildPath(requestId, "-prompt.md");
        return new FileResource(path.toFile());
    }

    protected IResource getResponseResource(String requestId) {
        Path path = buildPath(requestId, "-response.md");
        return new FileResource(path.toFile());
    }

    protected Path buildPath(String requestId, String postfix) {
        String baseDir = CFG_AI_MOCK_DIR.get();
        LocalDate date = CoreMetrics.currentDate();

        String datePath = String.format("%d/%02d-%02d",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth());

        String fileName = requestId + postfix;

        Path fullPath = Paths.get(baseDir, datePath, fileName);

        try {
            Files.createDirectories(fullPath.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create mock directory", e);
        }

        return fullPath;
    }
}
