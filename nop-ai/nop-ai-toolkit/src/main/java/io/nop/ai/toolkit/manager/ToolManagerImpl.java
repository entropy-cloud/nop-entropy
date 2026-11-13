package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.api.*;
import io.nop.ai.toolkit.model.*;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;

import static io.nop.ai.toolkit.NopAiToolkitErrors.ARG_DETAIL;
import static io.nop.ai.toolkit.NopAiToolkitErrors.ERR_AI_TOOLKIT_INVALID_STATE;
import static io.nop.ai.toolkit.NopAiToolkitErrors.ERR_AI_TOOLKIT_PATH_QUEUE_OVERFLOW;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    /**
     * 3.2-MA-07（plan 2557 裁定 A）：per-path pending 计数（入队时增，尾落定时减）。
     * 与 {@link #pathQueues} 同域增减；驱逐（两参条件 remove）复用同一落定钩子，
     * 生命周期账本不再单调增长。
     */
    private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> pathQueueDepths =
            new ConcurrentHashMap<>();

    /**
     * 3.2-MA-07（plan 2557 裁定 A）：同 path 排队积压上界（QueueOverflowPolicy ERROR
     * 语义——超界显式错误而非静默入队）。可配，缺省 64。
     */
    public static final int DEFAULT_PATH_QUEUE_LIMIT = 64;

    private int pathQueueLimit = DEFAULT_PATH_QUEUE_LIMIT;

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

    /**
     * 3.2-MA-07（plan 2557 裁定 A）：per-path 排队积压上界（IoC 配置键
     * {@code nop.ai.tool.path-queue-limit}，缺省 64）。非正值回落缺省——上界永不关闭。
     */
    @io.nop.api.core.annotations.ioc.InjectValue("@cfg:nop.ai.tool.path-queue-limit|64")
    public void setPathQueueLimit(int pathQueueLimit) {
        this.pathQueueLimit = pathQueueLimit > 0 ? pathQueueLimit : DEFAULT_PATH_QUEUE_LIMIT;
    }

    public int getPathQueueLimit() {
        return pathQueueLimit;
    }

    /**
     * 3.2-MA-07（plan 2557 裁定 A）：全 path 排队积压总量（指标面）。
     */
    public int pathQueueSize() {
        int total = 0;
        for (java.util.concurrent.atomic.AtomicInteger depth : pathQueueDepths.values()) {
            total += depth.get();
        }
        return total;
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

        // Plan 2301 (WI25): runInBackground is a reserved argument — only
        // background-capable tools may carry it. A non-capable tool fails
        // loud instead of silently running in the foreground (the model must
        // learn the flag is not a no-op on this tool). Dual-source read: the
        // engine dispatch path carries arguments as input JSON with a null
        // node (ToolPathAffinity precedent).
        if (runInBackgroundRequested(call) && !isBackgroundCapable(toolName)) {
            return CompletableFuture.completedFuture(buildErrorResult(call,
                    "Tool '" + toolName + "' does not support runInBackground "
                            + "(not declared <background-capable>)"));
        }

        // The execution itself (not just its callbacks) must be lazy so a
        // queued call does not start until its turn — see the queue below.
        // C4-008(3)：在途取消联动需要取消到「源 future」（装饰层 cancel 不向下游池任务传播），
        // 故执行派生后经 execRef 持有源 future 引用
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<AiToolCallResult>> execRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Supplier<CompletableFuture<AiToolCallResult>> executeNow = () -> {
            // 3.2-C4-008(2)：执行入口统一 isCancelled 短路（裁定⑥）——所有排队/非排队路径
            // 的执行入口先判 context cancelToken 状态，排队窗口内 token 已取消的调用不再派生
            // 执行（闭合排队窗口 token 盲区）。显式取消终态非静默成功（rule 24）。
            // context 可为 null（既有调用契约允许无上下文调用）
            ICancelToken cancelToken = context == null ? null : context.getCancelToken();
            if (cancelToken != null && cancelToken.isCancelled()) {
                CompletableFuture<AiToolCallResult> cancelled = new CompletableFuture<>();
                cancelled.completeExceptionally(new java.util.concurrent.CancellationException(
                        "tool call cancelled before execution: reason=" + cancelToken.getCancelReason()
                                + ", tool=" + toolName));
                return cancelled;
            }
            CompletableFuture<AiToolCallResult> source =
                    executor.executeAsync(call, context).toCompletableFuture();
            execRef.set(source);
            return source.thenApply(result -> {
                for (IToolCallInterceptor interceptor : interceptors) {
                    interceptor.afterCall(toolName, call, context, result);
                }
                return result;
            }).toCompletableFuture();
        };

        // 3.2-C1-001(3) (plan 2501 Phase 1): a wired context without an executor is a
        // dispatch misconfiguration — it used to surface as an NPE thrown inside the
        // tool dispatch, which the queued branch turned into a permanently hanging
        // result (ChatBiToolExecuteContext returns null; ToolExecuteContext.Builder has
        // no default). Fail loud with an explicit terminal state instead (rule 16/24).
        if (context != null && context.getExecutor() == null) {
            CompletableFuture<AiToolCallResult> misconfigured = new CompletableFuture<>();
            misconfigured.completeExceptionally(new NopException(ERR_AI_TOOLKIT_INVALID_STATE)
                    .param(ARG_DETAIL, "Tool execution context has no executor wired (fail-loud, "
                            + "previously an NPE inside dispatch): tool=" + toolName));
            return misconfigured;
        }

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
                // 3.2-C4-008(1)：排队分支取消联动（裁定⑥）——调用方对 result 的取消传播到
                // 触发链与在途执行：轮到本调用时先判 result 取消状态即不再派生执行（消除
                // result.cancel(true) 假确认）；执行已派生则联动取消执行 future
                CompletableFuture<AiToolCallResult> result = new CompletableFuture<>();
                // 3.2-C1-001(a)(b) (plan 2501 Phase 1): compute only swaps the per-path
                // tail — the tail is a plain promise and the execution body is attached
                // AFTER compute returns. With a completed predecessor the body would
                // otherwise run synchronously inside the CHM bin lock (nop-cli
                // CliCallToolsCommand injects SyncThreadPoolExecutor.INSTANCE, putting
                // the whole tool execution inside the lock), and a nested same-path
                // callTool would re-enter compute (Recursive update).
                CompletableFuture<Void> tail = new CompletableFuture<>();
                // CHM.compute returns the NEW mapping — the predecessor tail must be
                // captured inside the mapping function
                java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>> prevRef =
                        new java.util.concurrent.atomic.AtomicReference<>();
                // 3.2-MA-07（plan 2557 裁定 A）：入队先增 per-path pending 计数；
                // 超上界 fail-loud（QueueOverflowPolicy ERROR 语义），计数回退
                java.util.concurrent.atomic.AtomicInteger depth = pathQueueDepths.computeIfAbsent(pathKey, k -> new java.util.concurrent.atomic.AtomicInteger());
                if (depth.incrementAndGet() > pathQueueLimit) {
                    depth.decrementAndGet();
                    removeDepthIfIdle(pathKey, depth);
                    CompletableFuture<AiToolCallResult> overflow = new CompletableFuture<>();
                    overflow.completeExceptionally(new NopException(ERR_AI_TOOLKIT_PATH_QUEUE_OVERFLOW)
                            .param(ARG_DETAIL, "Per-path queued backlog exceeds limit=" + pathQueueLimit
                                    + " (QueueOverflowPolicy ERROR: fail loud instead of silently queuing)"
                                    + ": pathKey=" + pathKey + ", tool=" + toolName));
                    return overflow;
                }
                pathQueues.compute(pathKey, (k, oldTail) -> {
                    prevRef.set(oldTail);
                    return tail;
                });
                CompletableFuture<Void> prev = prevRef.get();
                CompletableFuture<Void> trigger = (prev == null)
                        ? CompletableFuture.completedFuture(null)
                        : prev.handle((v, e) -> null);
                trigger.whenComplete((v, e) ->
                        runQueuedExecution(result, tail, execRef, executeNow, toolName, pathKey));
                return result;
            }
        }
        return executeNow.get();
    }

    /**
     * 3.2-C1-001 (plan 2501 Phase 1): runs one queued call's execution body on the
     * thread that observed its predecessor's completion — never inside the
     * {@code pathQueues.compute} critical section.
     *
     * <p>Completion contract (C1-001(c)): the caller-visible {@code result} future is
     * guaranteed to settle exactly once — including when {@code executeNow} throws
     * synchronously (null-executor misconfiguration, saturated-pool
     * {@link java.util.concurrent.RejectedExecutionException}). {@code tail} always
     * settles too, so the next queued call on the same path proceeds (a failed call
     * never poisons the queue). Plan 2499 (3.2-C4-008) cancellation linkage is
     * preserved: a call cancelled during its queue window never derives execution,
     * and an in-flight cancellation propagates to the source execution future.
     */
    private void runQueuedExecution(CompletableFuture<AiToolCallResult> result,
                                    CompletableFuture<Void> tail,
                                    java.util.concurrent.atomic.AtomicReference<CompletableFuture<AiToolCallResult>> execRef,
                                    java.util.function.Supplier<CompletableFuture<AiToolCallResult>> executeNow,
                                    String toolName, String pathKey) {
        try {
            // 3.2-MA-07（plan 2557 裁定 A）：尾落定钩子——所有退出路径都会落定 tail，
            // 钩在 tail 上即覆盖全部出口。落定时：① per-path pending 计数减；② 该 path
            // 已无在途/等待项时以两参条件 remove 驱逐尾（防 ABA：compute 已换新尾则不移除），
            // pathQueues 账本回基线，生命周期不再单调增长。
            tail.whenComplete((v, e) -> settlePathQueue(pathKey, tail));
            if (result.isCancelled()) {
                // 排队窗口取消联动：不再派生执行，链剩余段以取消终态短路
                tail.completeExceptionally(new java.util.concurrent.CancellationException(
                        "queued tool call cancelled before its turn: tool=" + toolName
                                + ", pathKey=" + pathKey));
                return;
            }
            CompletableFuture<AiToolCallResult> exec;
            try {
                exec = executeNow.get();
            } catch (Throwable syncThrow) {
                // C1-001(c)：executeNow 同步抛（池拒绝等）时调用方 result 得到显式异常
                // 终态而非永久悬挂；tail 同步落定使后续排队调用继续（队列不中毒）
                LOG.warn("ToolManagerImpl: queued tool dispatch threw synchronously: tool="
                        + toolName + ", pathKey=" + pathKey, syncThrow);
                result.completeExceptionally(syncThrow);
                tail.completeExceptionally(syncThrow);
                return;
            }
            // 在途取消联动（C4-008(3)）：result 取消传播到「源」执行 future——
            // cancel(true) 经池载体中断 worker，任务不再跑满（取消语义不止步于包装层）
            result.whenComplete((r2, e2) -> {
                if (e2 instanceof java.util.concurrent.CancellationException) {
                    CompletableFuture<AiToolCallResult> source = execRef.get();
                    if (source != null) {
                        source.cancel(true);
                    }
                }
            });
            exec.whenComplete((r, err) -> {
                if (err != null) {
                    result.completeExceptionally(err);
                    tail.completeExceptionally(err);
                } else {
                    result.complete(r);
                    tail.complete(null);
                }
            });
        } catch (Throwable unguarded) {
            // last-resort contract closure — a queued caller must never be left hanging
            LOG.error("ToolManagerImpl: queued execution contract violated, completing exceptionally: tool="
                    + toolName + ", pathKey=" + pathKey, unguarded);
            result.completeExceptionally(unguarded);
            tail.completeExceptionally(unguarded);
        }
    }

    /**
     * 3.2-MA-07（plan 2557 裁定 A）：尾落定钩子——pending 计数减一；计数归零时
     * 计数器条目一并驱逐（账本回基线）。两参 {@code remove(pathKey, tail)} 仅在
     * 本 tail 仍是当前映射时移除（防 ABA：compute 已挂新尾则保留队列）。
     */
    private void settlePathQueue(String pathKey, CompletableFuture<Void> tail) {
        pathQueues.remove(pathKey, tail);
        java.util.concurrent.atomic.AtomicInteger depth = pathQueueDepths.get(pathKey);
        if (depth != null && depth.decrementAndGet() <= 0) {
            removeDepthIfIdle(pathKey, depth);
        }
    }

    private void removeDepthIfIdle(String pathKey, java.util.concurrent.atomic.AtomicInteger depth) {
        pathQueueDepths.remove(pathKey, depth);
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

    private boolean isBackgroundCapable(String toolName) {
        try {
            AiToolModel model = loadTool(toolName);
            return model != null && model.isBackgroundCapable();
        } catch (Exception e) {
            // Model lookup failure must not turn into a silent foreground
            // run — conservative fallback is "not capable" (the call is
            // rejected with an explicit error).
            LOG.debug("ToolManagerImpl: failed to load model for '{}' — treating as not background-capable", toolName, e);
            return false;
        }
    }

    /**
     * Plan 2301 (WI25): dual-source {@code runInBackground} read — node
     * attribute first (XML DSL path), then the input JSON (engine dispatch
     * path; ToolPathAffinity precedent). Malformed JSON yields {@code false}
     * — the reserved-flag read is never a failure surface.
     */
    static boolean runInBackgroundRequested(AiToolCall call) {
        if (call.attrBoolean("runInBackground", false)) {
            return true;
        }
        String input = call.getInput();
        if (input == null || input.isEmpty()) {
            return false;
        }
        try {
            Object parsed = io.nop.core.lang.json.JsonTool.parseNonStrict(input);
            if (parsed instanceof Map<?, ?> map) {
                Object value = map.get("runInBackground");
                if (value instanceof Boolean b) {
                    return b;
                }
                if (value instanceof String s) {
                    return Boolean.parseBoolean(s);
                }
            }
        } catch (Exception e) {
            LOG.debug("ToolManagerImpl: input is not JSON — no runInBackground flag");
        }
        return false;
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
