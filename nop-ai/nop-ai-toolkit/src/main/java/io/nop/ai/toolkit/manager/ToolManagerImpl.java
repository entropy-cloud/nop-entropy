package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.api.*;
import io.nop.ai.toolkit.model.*;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class ToolManagerImpl implements IToolManager {
    private static final Logger LOG = LoggerFactory.getLogger(ToolManagerImpl.class);

    private IToolExecutorProvider executorProvider;
    private List<IToolCallInterceptor> interceptors = Collections.emptyList();

    /**
     * Plan 2298 (WI17): per-path serialization tails — manager-instance-global
     * (cross-session conservative: same-process writes to the same file are
     * mutually excluded regardless of session). Values are the tail futures of
     * per-path queues; consumers normalize predecessor exceptions via handle.
     */
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pathQueues =
            new ConcurrentHashMap<>();

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

        // The execution itself (not just its callbacks) must be lazy so a
        // queued call does not start until its turn — see the queue below.
        java.util.function.Supplier<CompletableFuture<AiToolCallResult>> executeNow = () ->
                executor.executeAsync(call, context)
                        .thenApply(result -> {
                            for (IToolCallInterceptor interceptor : interceptors) {
                                interceptor.afterCall(toolName, call, context, result);
                            }
                            return result;
                        }).toCompletableFuture();

        // Plan 2298 (WI17): per-path serialization for concurrency-unsafe
        // tools. This is the process-wide chokepoint — the dispatcher's
        // parallel fan-out calls callTool per call, so the queue here is what
        // actually protects the ReAct write-same-file race (executeParallel
        // has no production callers). Non-blocking future-chain: the caller
        // thread is never blocked; same-path calls chain via thenCompose and
        // the executor is invoked lazily on the caller's turn, different
        // paths run in parallel. Unqueued: concurrency-safe tools and calls
        // with no extractable path key.
        if (!isConcurrencySafe(toolName)) {
            String pathKey = ToolPathAffinity.extract(call);
            if (pathKey != null) {
                CompletableFuture<AiToolCallResult> result = new CompletableFuture<>();
                pathQueues.compute(pathKey, (k, tail) -> {
                    CompletableFuture<Void> trigger = (tail == null
                            ? CompletableFuture.completedFuture(null)
                            : tail).handle((v, e) -> null);
                    return trigger.thenCompose(x -> executeNow.get()
                            .whenComplete((r, err) -> {
                                if (err != null) {
                                    result.completeExceptionally(err);
                                } else {
                                    result.complete(r);
                                }
                            })
                            .thenApply(r -> (Void) null));
                });
                return result;
            }
        }
        return executeNow.get();
    }

    private boolean isConcurrencySafe(String toolName) {
        try {
            AiToolModel model = loadTool(toolName);
            return model != null && model.isConcurrencySafe();
        } catch (Exception e) {
            // Safety lookup must never break tool dispatch — conservative
            // fallback is "unsafe" (participates in path serialization).
            LOG.debug("ToolManagerImpl: failed to load model for '{}' — treating as concurrency-unsafe", toolName, e);
            return false;
        }
    }

    @Override
    public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
        List<AiToolCall> toolCalls = calls.getBody();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return CompletableFuture.completedFuture(new AiToolCallsResponse());
        }

        Boolean parallel = calls.getParallel();
        Integer maxConcurrency = calls.getMaxConcurrency();

        if (parallel != null && parallel) {
            return executeParallel(toolCalls, context, maxConcurrency);
        } else {
            return executeSequential(toolCalls, context);
        }
    }

    private CompletableFuture<AiToolCallsResponse> executeParallel(
            List<AiToolCall> calls, IToolExecuteContext context, Integer maxConcurrency) {
        // null / non-positive maxConcurrency means "no bound" (all calls are
        // submitted immediately). A positive value caps the number of tool
        // calls that are in flight at any moment; excess calls wait for a
        // free slot (queued, never dropped).
        Semaphore semaphore = (maxConcurrency == null || maxConcurrency <= 0)
                ? null
                : new Semaphore(maxConcurrency, true);

        List<CompletableFuture<AiToolCallResult>> futures = new ArrayList<>();
        for (AiToolCall call : calls) {
            futures.add(submitBounded(parseToolName(call), call, context, semaphore));
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

    /**
     * Submits one parallel tool call, optionally bounded by the concurrency
     * semaphore: the permit is held from submission until the call completes,
     * so at most {@code maxConcurrency} calls are in flight at any moment.
     * A null semaphore means unbounded submission.
     */
    private CompletableFuture<AiToolCallResult> submitBounded(
            String toolName, AiToolCall call, IToolExecuteContext context, Semaphore semaphore) {
        if (semaphore == null) {
            return callTool(toolName, call, context);
        }
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(
                    buildErrorResult(call, "Interrupted while waiting for a concurrency slot"));
        }
        return callTool(toolName, call, context)
                .whenComplete((result, error) -> semaphore.release());
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
