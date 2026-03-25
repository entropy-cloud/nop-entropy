package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.api.*;
import io.nop.ai.toolkit.model.*;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ToolManagerImpl implements IToolManager {
    private IToolExecutorProvider executorProvider;
    private List<IToolCallInterceptor> interceptors = Collections.emptyList();

    public ToolManagerImpl() {
    }

    public ToolManagerImpl(IToolExecutorProvider executorProvider,
                           List<IToolCallInterceptor> interceptors) {
        this.executorProvider = executorProvider;
        this.interceptors = interceptors != null ? interceptors : Collections.emptyList();
    }

    public void setExecutorProvider(IToolExecutorProvider executorProvider) {
        this.executorProvider = executorProvider;
    }

    public void setInterceptors(List<IToolCallInterceptor> interceptors) {
        this.interceptors = interceptors != null ? interceptors : Collections.emptyList();
    }

    @Override
    public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
        for (IToolCallInterceptor interceptor : interceptors) {
            if (!interceptor.beforeCall(toolName, call, context)) {
                return CompletableFuture.completedFuture(
                        buildRejectedResult(call, "Interceptor blocked")
                );
            }
        }

        IToolExecutor executor = executorProvider.getExecutor(toolName);
        if (executor == null) {
            return CompletableFuture.completedFuture(
                    buildErrorResult(call, "Unknown tool: " + toolName)
            );
        }

        return executor.executeAsync(call, context)
                .thenApply(result -> {
                    for (IToolCallInterceptor interceptor : interceptors) {
                        interceptor.afterCall(toolName, call, context, result);
                    }
                    return result;
                }).toCompletableFuture();
    }

    @Override
    public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
        List<AiToolCall> toolCalls = calls.getBody();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return CompletableFuture.completedFuture(new AiToolCallsResponse());
        }

        Boolean parallel = calls.getParalllel();
        Integer maxConcurrency = calls.getMaxConcurrency();

        if (parallel != null && parallel) {
            return executeParallel(toolCalls, context, maxConcurrency);
        } else {
            return executeSequential(toolCalls, context);
        }
    }

    private CompletableFuture<AiToolCallsResponse> executeParallel(
            List<AiToolCall> calls, IToolExecuteContext context, Integer maxConcurrency) {
        List<CompletableFuture<AiToolCallResult>> futures = new ArrayList<>();
        for (AiToolCall call : calls) {
            futures.add(callTool(parseToolName(call), call, context));
        }

        CompletableFuture<AiToolCallResult>[] futuresArray = futures.toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(futuresArray)
                .thenApply(v -> {
                    AiToolCallsResponse response = new AiToolCallsResponse();
                    List<AiToolCallResult> results = new ArrayList<>();
                    for (CompletableFuture<AiToolCallResult> f : futuresArray) {
                        results.add(f.join());
                    }
                    response.setResults(results);
                    return response;
                });
    }

    private CompletableFuture<AiToolCallsResponse> executeSequential(
            List<AiToolCall> calls, IToolExecuteContext context) {
        List<AiToolCallResult> results = new ArrayList<>();

        CompletableFuture<AiToolCallsResponse> future = CompletableFuture.completedFuture(new AiToolCallsResponse());

        for (AiToolCall call : calls) {
            future = future.thenCompose(prev ->
                    callTool(parseToolName(call), call, context).thenApply(result -> {
                        results.add(result);
                        AiToolCallsResponse response = new AiToolCallsResponse();
                        response.setResults(new ArrayList<>(results));
                        return response;
                    })
            );
        }

        return future;
    }

    @Override
    public List<AiToolModel> listTools() {
        List<AiToolModel> tools = new ArrayList<>();
        List<IResource> resources = VirtualFileSystem.instance().findAll("/nop/ai/tools", "*.tool.xml");
        for (io.nop.core.resource.IResource child : resources) {
            if (child.getName().endsWith(".tool.xml")) {
                AiToolModel model = loadTool(child.getName().replace(".tool.xml", ""));
                if (model != null) {
                    tools.add(model);
                }
            }
        }
        return tools;
    }

    @Override
    public AiToolModel loadTool(String toolName) {
        String path = "/nop/ai/tools/" + toolName + ".tool.xml";
        return (AiToolModel) ResourceComponentManager.instance().loadComponentModel(path);
    }

    private String parseToolName(AiToolCall call) {
        String toolName = call.getToolName();
        return toolName != null ? toolName : "";
    }

    private AiToolCallResult buildErrorResult(AiToolCall call, String errorMessage) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("failure");
        AiToolError error = new AiToolError();
        error.setBody(errorMessage);
        result.setError(error);
        return result;
    }

    private AiToolCallResult buildRejectedResult(AiToolCall call, String reason) {
        return buildErrorResult(call, reason);
    }
}
