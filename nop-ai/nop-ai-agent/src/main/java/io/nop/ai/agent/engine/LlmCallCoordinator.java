package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.AccountChain;
import io.nop.ai.agent.reliability.CircuitState;
import io.nop.ai.agent.reliability.IAccountChainResolver;
import io.nop.ai.agent.reliability.ICircuitBreaker;
import io.nop.ai.agent.reliability.IProviderFailoverChainResolver;
import io.nop.ai.agent.reliability.IProviderFailoverQueue;
import io.nop.ai.agent.reliability.IRetryPolicy;
import io.nop.ai.agent.reliability.LlmErrorClassifier;
import io.nop.ai.agent.reliability.NoOpProviderFailoverQueue;
import io.nop.ai.agent.reliability.ProviderFailoverChain;
import io.nop.ai.agent.reliability.RetryContext;
import io.nop.ai.agent.reliability.RetryOutcome;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.RoutingResult;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.core.model.LlmAccountModel;
import io.nop.ai.core.model.LlmFailoverProviderModel;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.ai.toolkit.model.AiToolCallResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LLM-call lifecycle coordinator for the ReAct loop (extracted from
 * {@link ReActAgentExecutor}, MA4.2-05). Owns the circuit-breaker check,
 * the retry loop (RETRY / STOP / FALLBACK), the wall-clock timeout wrapper
 * and the model-identity key building used for model-switched detection.
 * Failure handling delegates to the injected {@link AgentHookInvoker}
 * (on-error hooks + execution-failed event publication).
 */
public class LlmCallCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(LlmCallCoordinator.class);

    private final IChatService chatService;
    private final IRetryPolicy retryPolicy;
    private final ICircuitBreaker circuitBreaker;
    private final IModelRouter modelRouter;
    private final long llmTimeoutMs;
    private final Executor timeoutExecutor;
    private final AgentHookInvoker hookInvoker;
    private final IAccountChainResolver accountChainResolver;
    private final IProviderFailoverQueue providerFailoverQueue;
    private final IProviderFailoverChainResolver providerFailoverChainResolver;

    /**
     * 生产默认账号链解析器：经 {@code LlmConfigHelper.resolveAccountChain(provider)} 从
     * {@code {provider}.llm.xml} 的 {@code <accounts>} 解析（裁定 A：纯配置文件）。
     */
    private static final IAccountChainResolver DEFAULT_ACCOUNT_CHAIN_RESOLVER =
            provider -> new AccountChain(LlmConfigHelper.resolveAccountChain(provider));

    /**
     * 生产默认跨 provider failover 链解析器（plan 2026-08-01-1905-3，设计 §13.4 裁定 A）：经
     * {@code LlmConfigHelper.resolveFailoverChain(primaryProvider)} 从 {@code _default.llm-failover.xml}
     * 解析（纯配置文件）。配置文件缺省 / primary 不在表 / primary 是表尾 → 空 chain（无 failover）。
     */
    private static final IProviderFailoverChainResolver DEFAULT_PROVIDER_FAILOVER_CHAIN_RESOLVER =
            primaryProvider -> new ProviderFailoverChain(LlmConfigHelper.resolveFailoverChain(primaryProvider));

    public LlmCallCoordinator(IChatService chatService, IRetryPolicy retryPolicy,
                               ICircuitBreaker circuitBreaker, IModelRouter modelRouter,
                               long llmTimeoutMs, Executor timeoutExecutor,
                               AgentHookInvoker hookInvoker) {
        this(chatService, retryPolicy, circuitBreaker, modelRouter, llmTimeoutMs,
                timeoutExecutor, hookInvoker, null);
    }

    /**
     * @param accountChainResolver 账号链解析策略；null 时退回生产默认（config-based）。
     *      测试可注入假实现以隔离 config 加载（plan 2026-08-01-1505-1 Phase 2/3）。
     */
    public LlmCallCoordinator(IChatService chatService, IRetryPolicy retryPolicy,
                               ICircuitBreaker circuitBreaker, IModelRouter modelRouter,
                               long llmTimeoutMs, Executor timeoutExecutor,
                               AgentHookInvoker hookInvoker,
                               IAccountChainResolver accountChainResolver) {
        this(chatService, retryPolicy, circuitBreaker, modelRouter, llmTimeoutMs,
                timeoutExecutor, hookInvoker, accountChainResolver, null, null);
    }

    /**
     * 完整构造器（plan 2026-08-01-1905-3，跨 provider failover 第三通道）。
     *
     * @param accountChainResolver        账号链解析策略；null → 生产默认（config-based）
     * @param providerFailoverQueue       跨调用共享 provider 健康状态（去重）；null → {@link NoOpProviderFailoverQueue}
     *                                     （shipped 默认：不去重，单次调用内由向前游标保证不回退——零回归）
     * @param providerFailoverChainResolver 跨 provider failover 链解析策略；null → 生产默认（config-based）。
     *      测试可注入假实现以隔离 config 加载（Phase 2 端到端测试）。
     */
    public LlmCallCoordinator(IChatService chatService, IRetryPolicy retryPolicy,
                               ICircuitBreaker circuitBreaker, IModelRouter modelRouter,
                               long llmTimeoutMs, Executor timeoutExecutor,
                               AgentHookInvoker hookInvoker,
                               IAccountChainResolver accountChainResolver,
                               IProviderFailoverQueue providerFailoverQueue,
                               IProviderFailoverChainResolver providerFailoverChainResolver) {
        this.chatService = chatService;
        this.retryPolicy = retryPolicy;
        this.circuitBreaker = circuitBreaker;
        this.modelRouter = modelRouter;
        this.llmTimeoutMs = llmTimeoutMs;
        this.timeoutExecutor = timeoutExecutor;
        this.hookInvoker = hookInvoker;
        this.accountChainResolver = accountChainResolver != null
                ? accountChainResolver
                : DEFAULT_ACCOUNT_CHAIN_RESOLVER;
        this.providerFailoverQueue = providerFailoverQueue != null
                ? providerFailoverQueue
                : NoOpProviderFailoverQueue.noOp();
        this.providerFailoverChainResolver = providerFailoverChainResolver != null
                ? providerFailoverChainResolver
                : DEFAULT_PROVIDER_FAILOVER_CHAIN_RESOLVER;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    /**
     * Plan 304: extract the LLM call with circuit-breaker check and retry
     * loop into its own method so the reactLoop body delegates to a named
     * step. Returns an LlmCallResult holding the response, the final
     * routedOptions (which may have been reassigned by a FALLBACK switch),
     * the llmCallStart timestamp, and a success flag.
     *
     * <p><b>FALLBACK 路由（plan 2026-08-01-1505-1，设计 §3.6/§4.4）</b>：收到 FALLBACK 决策时
     * 按 {@code errorClassification} 分流到两个独立通道——{@code QUOTA_EXCEEDED}/{@code AUTH_INVALID}
     * → 账号链（同模型换 key/账号），{@code TRANSIENT} 等 → {@code IModelRouter.getFallback}（模型 tier）。
     * 任一通道耗尽都 fail-loud（设计 §6.9，不静默降级/跳过）。
     *
     * <p><b>第三通道：跨 provider 有序故障转移（plan 2026-08-01-1905-3，设计 §13.4 裁定 C）</b>：
     * 当 QUOTA/AUTH 的账号链通道<b>耗尽</b>时（同 provider 所有账号不可用 = provider 级故障），
     * 不立即 fail-loud，而是升级到跨 provider 通道——切到下一 provider 重试（重置 accountChain +
     * 新 circuit key + 重置 attempt）。全部 provider 耗尽才 fail-loud。TRANSIENT 的模型 tier 通道耗尽
     * 仍 fail-loud（两通道区分不变：QUOTA/AUTH=provider 账号问题→切 provider；TRANSIENT=瞬态→模型 tier）。
     */
    public LlmCallResult doLlmCallWithRetry(ChatRequest request,
                                             AgentExecutionContext ctx,
                                             String sessionId,
                                             String agentName,
                                             ChatOptions routedOptions) {
        String primaryModelKey = buildModelKey(routedOptions);
        if (!circuitBreaker.allowCall(primaryModelKey)) {
            CircuitState rejectedState = circuitBreaker.getState(primaryModelKey);
            LOG.warn("Circuit breaker rejected LLM call for model {} (state={}); "
                            + "failing fast. session={}", primaryModelKey, rejectedState, sessionId);
            throw new NopAiAgentException(
                    "Circuit breaker is " + rejectedState + " for model "
                            + primaryModelKey + "; rejecting call to avoid wasting "
                            + "time/tokens on a consecutively-failing model. Configure "
                            + "an IModelRouter fallback chain or wait for the breaker "
                            + "cooldown before retrying.");
        }
        long llmCallStart = System.currentTimeMillis();
        ChatResponse response;
        // fail-loud 错误：FALLBACK 通道耗尽时填充，循环退出后抛出（不在 try 块内抛，避免被
        // catch 误当作传输异常重试——设计 §6.9 fail-loud）。
        NopAiAgentException fallbackExhausted = null;
        {
            int attempt = 0;
            Throwable lastError = null;
            ChatResponse attemptResponse = null;
            // 账号链游走器：惰性解析（首次 QUOTA/AUTH FALLBACK 时），跨迭代保留游标。
            AccountChain accountChain = null;
            // 跨 provider failover 链游走器：惰性解析（首次账号链耗尽升级时），跨迭代保留游标（裁定 D 向前）。
            ProviderFailoverChain failoverChain = null;
            while (true) {
                try {
                    llmCallStart = System.currentTimeMillis();
                    attemptResponse = callChatWithTimeout(request);
                    if (attemptResponse.isSuccess()) {
                        break; // genuine success
                    }
                    // 响应级错误（W2e-2/W2e-3）：ChatServiceImpl 已规范化为携带
                    // errorClassification 的错误 ChatResponse（非 2xx 不再抛异常）。
                    // 读分类进入重试决策——不再像旧实现那样一律终止。
                    circuitBreaker.recordFailure(buildModelKey(routedOptions));
                    ErrorClassification classification = attemptResponse.getErrorClassification();
                    if (classification == null) {
                        classification = ErrorClassification.NON_TRANSIENT;
                    }
                    RetryContext retryCtx = new RetryContext(attempt, null, classification,
                            false, attemptResponse.getRetryAfterMs());
                    RetryOutcome outcome = retryPolicy.shouldRetry(retryCtx);
                    if (outcome == null) {
                        throw new NopAiAgentException(
                                "retryPolicy.shouldRetry() returned null for classification="
                                        + classification + ", attempt=" + attempt);
                    }
                    if (outcome.isRetry()) {
                        LOG.warn("LLM call returned error response (classification={}, "
                                        + "attempt={}, httpStatus={}), retrying after {} ms",
                                classification, attempt, attemptResponse.getHttpStatus(),
                                outcome.getDelayMs());
                        attempt++;
                        sleepBackoff(outcome.getDelayMs());
                        continue;
                    }
                    if (outcome.isFallback()) {
                        // 按 errorClassification 分流（设计 §4.4 两通道区分）：
                        // QUOTA/AUTH → 账号链（同模型换 key）；TRANSIENT 等 → 模型 tier 回退。
                        if (classification == ErrorClassification.QUOTA_EXCEEDED
                                || classification == ErrorClassification.AUTH_INVALID) {
                            // 惰性解析账号链（首次 QUOTA/AUTH FALLBACK），跨迭代重用游标。
                            if (accountChain == null) {
                                accountChain = resolveAccountChain(routedOptions.getProvider());
                            }
                            ChatOptions switched = doAccountSwitch(routedOptions, request,
                                    attempt, classification, accountChain,
                                    routedOptions.getProvider());
                            if (switched == null) {
                                // 账号链耗尽 → 第三通道：升级到跨 provider failover（设计 §13.4 裁定 C）。
                                // 记录 provider 级失败（去重，裁定 D）→ 试切下一 provider。
                                String exhaustedProvider = routedOptions.getProvider();
                                providerFailoverQueue.recordProviderFailure(exhaustedProvider);
                                // 惰性解析跨 provider 链（首次账号链耗尽升级时），跨迭代重用游标（裁定 D 向前）。
                                if (failoverChain == null) {
                                    failoverChain = providerFailoverChainResolver.apply(exhaustedProvider);
                                }
                                ChatOptions nextProvider = doProviderFailover(routedOptions, request,
                                        attempt, classification, exhaustedProvider, failoverChain);
                                if (nextProvider != null) {
                                    // 切到下一 provider：重置 accountChain（新 provider 有自己的 <accounts>）
                                    // + routedOptions（改 provider/model，清 accountKey，裁定 E）+ 新 circuit key
                                    // （buildModelKey 改变）+ 重置 attempt（嵌套循环内层重置，裁定 C）。
                                    routedOptions = nextProvider;
                                    accountChain = null;
                                    attempt = 0;
                                    continue;
                                }
                                // 跨 provider 链也耗尽 → fail-loud（break 退出循环，循环外抛出）。
                                fallbackExhausted = buildFallbackExhaustedError(
                                        classification, attempt, null, true, true, accountChain);
                                break;
                            }
                            routedOptions = switched;
                            attempt = 0;
                            continue;
                        }
                        // TRANSIENT 等 → 模型 tier 回退（行为不变）。
                        ChatOptions switched = doModelTierFallback(routedOptions, request,
                                attempt, classification, null);
                        if (switched == null) {
                            fallbackExhausted = buildFallbackExhaustedError(
                                    classification, attempt, null, false, false, null);
                            break;
                        }
                        routedOptions = switched;
                        attempt = 0;
                        continue;
                    }
                    // STOP：错误响应不可重试（NON_TRANSIENT 等）。退出循环，由下方
                    // !isSuccess() 终止分支处理。
                    break;
                } catch (RuntimeException | Error ex) {
                    // 传输级错误（无 HTTP 响应）：仍走 LlmErrorClassifier 启发式。
                    // 注意分类来源不对称（设计 §6.1）：启发式从不产 QUOTA/AUTH，故传输级
                    // FALLBACK 恒走模型 tier（账号链路由只在响应级路径可达）。
                    circuitBreaker.recordFailure(buildModelKey(routedOptions));
                    lastError = ex;
                    ErrorClassification classification = LlmErrorClassifier.classify(ex);
                    RetryContext retryCtx = new RetryContext(
                            attempt, ex, classification, false, null);
                    RetryOutcome outcome = retryPolicy.shouldRetry(retryCtx);
                    if (outcome == null) {
                        throw new NopAiAgentException(
                                "retryPolicy.shouldRetry() returned null for classification="
                                        + classification + ", attempt=" + attempt, ex);
                    }
                    if (outcome.isRetry()) {
                        LOG.warn("LLM call failed (classification={}, attempt={}), "
                                        + "retrying after {} ms: {}",
                                classification, attempt, outcome.getDelayMs(),
                                ex.toString());
                        attempt++;
                        sleepBackoff(outcome.getDelayMs());
                        continue;
                    }
                    if (outcome.isFallback()) {
                        // 传输级 FALLBACK：恒模型 tier（QUOTA/AUTH 不可达，见上）。
                        ChatOptions switched = doModelTierFallback(routedOptions, request,
                                attempt, classification, ex);
                        if (switched == null) {
                            fallbackExhausted = buildFallbackExhaustedError(
                                    classification, attempt, ex, false, false, null);
                            break;
                        }
                        routedOptions = switched;
                        attempt = 0;
                        lastError = null;
                        continue;
                    }
                    if (lastError instanceof RuntimeException) {
                        throw (RuntimeException) lastError;
                    }
                    throw (Error) lastError;
                }
            }
            response = attemptResponse;
        }

        if (fallbackExhausted != null) {
            // FALLBACK 通道耗尽 → fail-loud（设计 §6.9，Minimum Rules #24：不静默降级/跳过）。
            throw fallbackExhausted;
        }

        if (!response.isSuccess()) {
            // 错误响应重试耗尽 / 不可重试分类（NON_TRANSIENT 等）：终止。
            // 失败已在循环内记录（circuitBreaker.recordFailure），此处仅终止 + 通知。
            ctx.setStatus(AgentExecStatus.failed);
            ctx.setLastError(response.getError());
            hookInvoker.invokeOnError(ctx, agentName);
            hookInvoker.publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName,
                    response.getError());
            return new LlmCallResult(response, routedOptions, llmCallStart, false);
        }

        circuitBreaker.recordSuccess(buildModelKey(routedOptions));
        // 跨 provider failover 成功（或 primary 直接成功）：记录 provider 级成功，重置其失败计数
        // （去重维度，裁定 D——成功说明该 provider 恢复健康，后续调用不应跳过它）。
        providerFailoverQueue.recordProviderSuccess(routedOptions.getProvider());
        return new LlmCallResult(response, routedOptions, llmCallStart, true);
    }

    /**
     * 惰性解析 provider 的账号链（裁定 B：经 nop-ai-api 载体下沉，链解析在 nop-ai-core）。
     */
    private AccountChain resolveAccountChain(String provider) {
        return accountChainResolver.apply(provider);
    }

    /**
     * 账号链切换（设计 §3.6/§4.4，QUOTA/AUTH FALLBACK 路径）。取下一个备用账号，经
     * {@code ChatOptions.accountKey}/{@code accountBaseUrl} 下沉到 {@code ChatServiceImpl}。
     *
     * @param accountChain 当前重试循环的账号链游走器；为 null 时惰性解析（首次切换）
     * @return 切换后的 routedOptions（已设 accountKey），或 null 当链耗尽（调用方 fail-loud）
     */
    private ChatOptions doAccountSwitch(ChatOptions routedOptions, ChatRequest request,
                                         int attempt, ErrorClassification classification,
                                         AccountChain accountChain, String provider) {
        AccountChain chain = accountChain;
        if (chain == null) {
            chain = resolveAccountChain(provider);
        }
        LlmAccountModel nextAccount = chain.next();
        if (nextAccount == null) {
            LOG.error("LLM call FALLBACK (classification={}, account-chain) at attempt={} "
                    + "but account chain exhausted (provider={}, consumed={}). Failing loud.",
                    classification, attempt, provider, chain.consumed());
            return null; // 调用方 fail-loud
        }
        ChatOptions switched = routedOptions.copy();
        switched.setAccountKey(nextAccount.getApiKey());
        switched.setAccountBaseUrl(nextAccount.getBaseUrl());
        request.setOptions(switched);
        LOG.warn("LLM call FALLBACK (classification={}, attempt={}): switching to backup "
                        + "account (provider={}, account id={}, baseUrl override={}), "
                        + "attempt reset to 0",
                classification, attempt, provider, nextAccount.getId(),
                nextAccount.getBaseUrl() != null ? "yes" : "no");
        return switched;
    }

    /**
     * 跨 provider 有序故障转移切换（plan 2026-08-01-1905-3，设计 §13.4 裁定 C/E，第三通道）。
     * 同 provider 账号链耗尽时调用：从 {@code failoverChain} 取下一个<b>可用</b> provider（跳过
     * {@code providerFailoverQueue} 冷却中的），经 {@code ChatOptions.provider}/{@code model} 下沉到
     * {@code ChatServiceImpl}（每次 call 按 provider 重载 config/dialect，裁定 E）。
     *
     * <p>清 {@code accountKey}/{@code accountBaseUrl}——新 provider 从主账号开始（非继承前 provider 的
     * 备用账号）。链耗尽（无可用 provider，含全部冷却中）返回 null（调用方 fail-loud，设计 §6.9）。
     *
     * @param exhaustedProvider 刚耗尽账号链的 provider（诊断用）
     * @param failoverChain     跨 provider 链游走器（向前 cursor，裁定 D）；非 null
     * @return 切换后的 routedOptions（已设 provider/model，清 accountKey），或 null 当链耗尽
     */
    private ChatOptions doProviderFailover(ChatOptions routedOptions, ChatRequest request,
                                            int attempt, ErrorClassification classification,
                                            String exhaustedProvider,
                                            ProviderFailoverChain failoverChain) {
        LlmFailoverProviderModel next = failoverChain.nextAvailable(providerFailoverQueue);
        if (next == null) {
            LOG.error("LLM call FALLBACK (classification={}, provider-failover) at attempt={} "
                            + "but cross-provider failover chain exhausted (from provider={}, "
                            + "consumed={}). Failing loud.",
                    classification, attempt, exhaustedProvider, failoverChain.consumed());
            return null; // 调用方 fail-loud
        }
        ChatOptions switched = routedOptions.copy();
        switched.setProvider(next.getProvider());
        // model 覆盖：链声明 model 用之，否则清空让目标 provider 的 defaultModel 解析（裁定 E）。
        switched.setModel(next.getModel());
        // 新 provider 从主账号开始：清前 provider 的账号下沉（裁定 E）。
        switched.setAccountKey(null);
        switched.setAccountBaseUrl(null);
        request.setOptions(switched);
        LOG.warn("LLM call FALLBACK (classification={}, attempt={}): cross-provider failover "
                        + "{} -> {} (model={}, attempt reset to 0)",
                classification, attempt,
                buildModelKey(routedOptions), buildModelKey(switched),
                next.getModel() != null ? next.getModel() : "(target default)");
        return switched;
    }

    /**
     * 执行模型 tier 回退切换（TRANSIENT FALLBACK 路径，行为不变）。从 {@code modelRouter.getFallback(current)}
     * 取下一个模型，更新 {@code request} 的 options，返回新的 routedOptions。无可用回退模型时
     * 返回 null（调用方 fail-loud，Minimum Rules #24）。{@code ex} 可空（响应级错误无异常）。
     */
    private ChatOptions doModelTierFallback(ChatOptions routedOptions, ChatRequest request,
                                             int attempt, ErrorClassification classification,
                                             Throwable ex) {
        ChatOptions fallbackOptions = modelRouter.getFallback(routedOptions);
        if (fallbackOptions == null) {
            LOG.error("LLM call retry policy returned FALLBACK at "
                    + "attempt={} (classification={}), but the model "
                    + "router provided no fallback model — stopping "
                    + "execution. Last error: {}",
                    attempt, classification, ex != null ? ex.toString() : "(error response)");
            return null; // 调用方 fail-loud
        }
        int failedAttempt = attempt;
        String prevModelKey = buildModelKey(routedOptions);
        ChatOptions next = fallbackOptions;
        request.setOptions(next);
        LOG.warn("LLM call FALLBACK after attempt={} "
                        + "(classification={}): switching model {} -> {} "
                        + "(attempt reset to 0) and retrying",
                failedAttempt, classification, prevModelKey,
                buildModelKey(next));
        return next;
    }

    /**
     * 构造 FALLBACK 通道耗尽的 fail-loud 异常（设计 §6.9）。
     *
     * @param accountChainExhausted  true=账号链耗尽；false=模型 tier 回退链耗尽
     * @param providerChainExhausted true=账号链耗尽后升级到跨 provider 链也耗尽（全部 provider 不可用）；
     *                               false=未涉及跨 provider（模型 tier 通道 / 无 provider 链配置）
     */
    private NopAiAgentException buildFallbackExhaustedError(ErrorClassification classification,
                                                             int attempt, Throwable ex,
                                                             boolean accountChainExhausted,
                                                             boolean providerChainExhausted,
                                                             AccountChain accountChain) {
        String channel = accountChainExhausted ? "account chain" : "model-tier fallback";
        String detail = accountChainExhausted && accountChain != null
                ? " (consumed=" + accountChain.consumed() + ")"
                : "";
        String providerNote = providerChainExhausted
                ? " and cross-provider failover chain also exhausted (all providers unavailable)"
                : "";
        return new NopAiAgentException(
                "LLM call FALLBACK (" + channel + ") exhausted for classification="
                        + classification + ", attempt=" + attempt + detail + providerNote
                        + ". No more " + channel + " available — failing loud "
                        + "(design §6.9). Configure additional backup accounts (<accounts> in "
                        + "{provider}.llm.xml), IModelRouter fallback models, or a cross-provider "
                        + "failover chain (_default.llm-failover.xml).", ex);
    }
    /**
     * Holds the result of {@link #doLlmCallWithRetry}: the ChatResponse,
     * the final routedOptions (possibly updated by FALLBACK), the
     * llmCallStart timestamp for usage recording, and a success flag.
     */
    static final class LlmCallResult {
        final ChatResponse response;
        final ChatOptions routedOptions;
        final long llmCallStart;
        final boolean success;

        LlmCallResult(ChatResponse response, ChatOptions routedOptions,
                      long llmCallStart, boolean success) {
            this.response = response;
            this.routedOptions = routedOptions;
            this.llmCallStart = llmCallStart;
            this.success = success;
        }

        boolean isSuccess() { return success; }
    }
    /**
     * Build the composite model identity key ({@code provider:model}) from a
     * {@link ChatOptions} instance, as returned by {@code RoutingResult.getOptions()}.
     * Null provider/model are normalized to empty strings so the key is always
     * non-null and comparable (plan 205 / L2-21). This is the model identity
     * used to detect switches between ReAct iterations.
     */
    public static String buildModelKey(ChatOptions options) {
        String provider = options.getProvider() != null ? options.getProvider() : "";
        String model = options.getModel() != null ? options.getModel() : "";
        return provider + ":" + model;
    }
    /**
     * Plan 271 (finding 14-03): invoke {@link IChatService#call} with a
     * wall-clock timeout. When {@code llmTimeoutMs > 0} and a
     * {@code timeoutExecutor} is configured, the synchronous call is dispatched
     * to the executor and bounded by {@code f.get(llmTimeoutMs)}; on timeout a
     * {@link CompletionException} wrapping a {@link TimeoutException} is thrown
     * and handled by the caller's retry/error-classification path. When the
     * timeout is disabled ({@code llmTimeoutMs <= 0}) or no executor is wired,
     * the call is invoked directly (backward-compatible).
     *
     * <p>Uses {@link CompletableFuture#get(long, TimeUnit)} instead of
     * {@code .orTimeout().join()} so that a forced cancel (which interrupts
     * the ReAct-loop thread via {@code cancelSession(forced=true)}) breaks the
     * wait immediately. {@code .join()} is not interruptible and would block
     * until the LLM call completes or the timeout fires, defeating the purpose
     * of a forced cancel.
     */
    public ChatResponse callChatWithTimeout(ChatRequest request) {
        if (llmTimeoutMs <= 0 || timeoutExecutor == null) {
            return chatService.call(request, null);
        }
        CompletableFuture<ChatResponse> f = CompletableFuture.supplyAsync(
                () -> chatService.call(request, null), timeoutExecutor);
        try {
            return f.get(llmTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopAiAgentException(
                    "LLM call interrupted (forced cancel or thread interrupt)", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new CompletionException(cause != null ? cause : e);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new CompletionException(e);
        }
    }
    /**
     * Plan 271 (finding 14-03): parse a {@link ChatToolCall#getId()} (String)
     * into the {@code int} expected by {@link AiToolCallResult#errorResult}.
     * Returns 0 when the id is non-numeric (the id is only used for result-to-
     * call matching, which is already satisfied by the ToolCallOutput wrapper).
     */
    public static int parseToolCallId(String id) {
        if (id == null || id.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
    /**
     * Plan 213 (circuit-aware-routing): resolve the routed options against the
     * circuit breaker BEFORE the model-switched audit detection and the retry
     * loop's outer circuit check. This upgrades the engine's handling of a
     * circuit-OPEN primary model from "reject → terminate the whole agent
     * execution" (plan 210 behaviour) to "reject → proactively scan the
     * {@link IModelRouter} fallback chain via {@code getFallback(...)} for a
     * circuit-allowed model → switch {@code routedOptions} to it and continue"
     * (design {@code nop-ai-agent-reliability.md} §3.3 / §5.2).
     *
     * <p><b>Algorithm</b>:
     * <ol>
     *   <li>Build the primary model-key from {@code routedOptions} and consult
     *       {@code circuitBreaker.allowCall(key)}.</li>
     *   <li>If {@code true} → the primary model is circuit-closed; return
     *       {@code routedOptions} unchanged (zero-overhead path; the shipped
     *       {@link AlwaysClosed} default always takes this branch, so wiring
     *       this step is a zero-regression change).</li>
     *   <li>If {@code false} → scan the fallback chain: repeatedly call
     *       {@code modelRouter.getFallback(current)}; for each non-null fallback,
     *       check {@code allowCall(fallback-key)}. The first circuit-allowed
     *       fallback is logged (circuit-induced switch) and returned as the
     *       new routed options.</li>
     *   <li>If {@code getFallback} returns {@code null} (chain exhausted) or
     *       every fallback is also circuit-rejected, fail fast with a
     *       {@link NopAiAgentException} listing every checked model-key + its
     *       circuit state + operator guidance (Minimum Rules #24 — no silent
     *       skip, no swallowing).</li>
     * </ol>
     *
     * <p><b>Positioning</b>: invoked between {@code modelRouter.route(...)} and
     * the model-switched audit detection (plan 205, role=80 message). This
     * ordering is deliberate: the resolution may change {@code routedOptions},
     * so the audit detection must observe the <i>post-resolution</i> final
     * model to correctly emit the model-switched message (the {@code fromModel}
     * is the previous iteration's model, the {@code toModel} is the
     * circuit-resolved model). The downstream outer circuit check (formerly
     * the primary rejection point) now acts as a safety-net for the rare
     * concurrent-circuit-trip race (a model that was circuit-cleared by this
     * resolution tripping OPEN between resolution and the check).
     *
     * <p><b>No RoutingResult mutation</b>: this method does not modify the
     * immutable {@link io.nop.ai.agent.router.RoutingResult} returned by
     * {@code route()} — the circuit-induced switch is recorded only via
     * {@code LOG.warn} and the natural model-switched audit message. The
     * {@code routingReason} stays as route()'s original decision.
     *
     * <p><b>Loop bound</b>: the fallback chain length is bounded by router
     * configuration (SmartModelRouter's configured chain, typically ≤5). A
     * defensive hard cap ({@link #MAX_FALLBACK_SCAN}) guards against a buggy
     * custom {@code IModelRouter} whose {@code getFallback} never returns
     * {@code null} — the cap fails loud rather than spinning forever.
     *
     * @param routedOptions the options returned by {@code route()}; non-null
     *      *      * @param sessionId     the session id for log correlation; may be null
     *                      (anonymous executions still resolve correctly)
     * @return the circuit-cleared options to use for this iteration (either
     *         the unchanged {@code routedOptions} or a circuit-allowed
     *         fallback); never null
     * @throws NopAiAgentException if the primary and every fallback model are
     *         all circuit-rejected (fail fast — no silent skip)
     */
    public ChatOptions resolveCircuitAware(ChatOptions routedOptions, String sessionId) {
        String primaryKey = buildModelKey(routedOptions);
        if (circuitBreaker.allowCall(primaryKey)) {
            // Zero-overhead path: primary model is circuit-closed. With the
            // shipped AlwaysClosed default this branch is always taken, so
            // wiring this resolution step is a zero-regression change.
            return routedOptions;
        }

        // Primary circuit is OPEN or HALF_OPEN-probe-busy → scan the fallback
        // chain for a circuit-allowed model. Collect every checked model-key
        // + state so the fail-fast diagnostic surfaces the full picture.
        CircuitState primaryState = circuitBreaker.getState(primaryKey);
        List<String> checked = new ArrayList<>();
        checked.add(primaryKey + "=" + primaryState);

        ChatOptions current = routedOptions;
        for (int step = 0; step < MAX_FALLBACK_SCAN; step++) {
            ChatOptions fallback = modelRouter.getFallback(current);
            if (fallback == null) {
                // Chain exhausted without a circuit-allowed model.
                break;
            }
            String fallbackKey = buildModelKey(fallback);
            if (circuitBreaker.allowCall(fallbackKey)) {
                LOG.warn("Circuit-aware routing switched model {} -> {} "
                                + "(primary {} circuit={}). session={}",
                        primaryKey, fallbackKey, primaryKey, primaryState, sessionId);
                return fallback;
            }
            checked.add(fallbackKey + "=" + circuitBreaker.getState(fallbackKey));
            current = fallback;
        }

        // All models circuit-rejected → fail fast (Minimum Rules #24 — no
        // silent skip, no swallowing). The message lists every checked
        // model-key + its circuit state so the operator can see the full
        // picture, plus actionable guidance.
        throw new NopAiAgentException(
                "Circuit breaker rejected all available models for session "
                        + sessionId + ". Checked models (key=circuitState): " + checked
                        + ". Wait for the breaker cooldown before retrying, or configure "
                        + "additional IModelRouter fallback models via SmartModelRouter.fallback(...).");
    }
    /**
     * Defensive hard cap on the circuit-aware fallback-chain scan. A correctly
     * implemented {@link IModelRouter} returns {@code null} from
     * {@code getFallback} when its (bounded) chain is exhausted; this cap only
     * triggers for a buggy custom router that never returns {@code null}, in
     * which case the fail-fast at {@link #resolveCircuitAware} surfaces the
     * problem rather than spinning forever. 64 is well above any realistic
     * configured chain length (SmartModelRouter's configured chains are
     * typically ≤5).
     */
    static final int MAX_FALLBACK_SCAN = 64;


    /**
     * Plan 207 (L3-2): sleep for the retry-policy-computed backoff delay.
     * A zero/negative delay is a no-op (the policy opted for immediate
     * retry). An interrupted sleep propagates as an {@link NopAiAgentException}
     * wrapping the {@link InterruptedException} (no silent swallow — Minimum
     * Rules #24) and re-sets the interrupt flag so upper layers honour it.
     */
    public static void sleepBackoff(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new NopAiAgentException(
                    "LLM retry backoff sleep interrupted: delayMs=" + delayMs, ie);
        }
    }
}

