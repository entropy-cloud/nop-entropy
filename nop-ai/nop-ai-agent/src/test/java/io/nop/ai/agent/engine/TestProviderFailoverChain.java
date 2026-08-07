package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.AccountChain;
import io.nop.ai.agent.reliability.AlwaysClosed;
import io.nop.ai.agent.reliability.IAccountChainResolver;
import io.nop.ai.agent.reliability.IProviderFailoverChainResolver;
import io.nop.ai.agent.reliability.IProviderFailoverQueue;
import io.nop.ai.agent.reliability.NoOpProviderFailoverQueue;
import io.nop.ai.agent.reliability.ProviderFailoverChain;
import io.nop.ai.agent.reliability.ProviderFailoverQueue;
import io.nop.ai.agent.reliability.StandardRetryPolicy;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.core.model.LlmAccountModel;
import io.nop.ai.core.model.LlmFailoverProviderModel;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-01-1905-3 Phase 2 端到端测试：跨 provider 有序故障转移第三通道
 * （设计 §13.4，Minimum Rules #22 端到端 / #23 接线 / #24 无静默跳过 / #25 新功能）。
 *
 * <p>直接构造 {@link LlmCallCoordinator}（不经 DefaultAgentEngine），注入假 {@link IChatService}
 * + 假 {@link IAccountChainResolver} + {@link IProviderFailoverQueue} + 假
 * {@link IProviderFailoverChainResolver}，从 {@code doLlmCallWithRetry} 入口到跨 provider 成功/fail-loud
 * 完整跑通——验证：
 * <ol>
 *   <li>同 provider 账号链耗尽 → 切下一 provider → 重试成功（第三通道真实生效）。</li>
 *   <li>跨 provider 切换确实改变 {@code ChatOptions.provider}（裁定 E 选项下沉）。</li>
 *   <li>全部 provider 耗尽 → fail-loud（不静默降级/跳过）。</li>
 *   <li>无 provider 链配置时账号链耗尽仍 fail-loud（零回归——今日行为）。</li>
 *   <li>failover_switch 去重生效（冷却期内不切回刚失败 provider，防震荡）。</li>
 *   <li>接线验证（Rule #23）：{@link IProviderFailoverQueue#recordProviderFailure} 确实被账号链耗尽分支调用。</li>
 *   <li>账号链/模型 tier 两通道行为不变。</li>
 * </ol>
 */
public class TestProviderFailoverChain {

    private static final String P1 = "provider-1";
    private static final String P2 = "provider-2";
    private static final String P3 = "provider-3";
    private static final String P1_BACKUP_KEY = "sk-p1-backup";

    // ========================================================================
    // 端到端：账号链耗尽 → 跨 provider failover → 成功（第三通道真实生效）
    // ========================================================================

    @Test
    void accountChainExhaustedFailoversToNextProviderAndSucceeds() {
        // p1 所有账号（主 + 备用）QUOTA → 账号链耗尽 → failover 到 p2 → 成功。
        ProviderAwareChatService chat = new ProviderAwareChatService();
        ChatResponse quota = ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null);
        chat.onProvider(P1, quota);
        chat.onProvider(P2, successResponse());

        // p1 有 1 个备用账号（也 QUOTA）→ 走完账号链再升级。
        IAccountChainResolver accountResolver = provider ->
                P1.equals(provider) ? new AccountChain(accounts(P1_BACKUP_KEY)) : new AccountChain(List.of());
        IProviderFailoverChainResolver failoverResolver = failoverResolver(P2);
        RecordingProviderQueue queue = new RecordingProviderQueue();
        LlmCallCoordinator coordinator = newCoordinator(chat, accountResolver, failoverResolver, queue);

        ChatOptions options = ChatOptions.builder().provider(P1).model("m").build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess(), "账号链耗尽后 failover 到 p2 必须成功");
        assertEquals(P2, result.routedOptions.getProvider(),
                "跨 provider 切换确实改变 ChatOptions.provider（裁定 E）");
        // 调用序列：p1 主(QUOTA) → p1 backup(QUOTA) → 账号链耗尽 → p2 主(success) = 3 次。
        assertEquals(3, chat.calls.get(),
                "call sequence: p1 main QUOTA -> p1 backup QUOTA -> p2 success");
        assertEquals(List.of(P1, P1, P2), chat.seenProviders,
                "provider 序列：p1 两次（主+备用）然后切 p2");
        // 接线验证（Rule #23）：账号链耗尽时确实调了 recordProviderFailure。
        assertTrue(queue.failureRecorded(P1), "recordProviderFailure(P1) 必须被账号链耗尽分支调用");
        assertTrue(queue.successRecorded(P2), "p2 成功后调 recordProviderSuccess(P2) 重置");
    }

    // ========================================================================
    // fail-loud：全部 provider 耗尽
    // ========================================================================

    @Test
    void allProvidersExhaustedFailsLoud() {
        // p1/p2/p3 全部 QUOTA → 账号链 + 跨 provider 链全耗尽 → fail-loud。
        ProviderAwareChatService chat = new ProviderAwareChatService();
        ChatResponse quota = ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null);
        chat.onProvider(P1, quota);
        chat.onProvider(P2, quota);
        chat.onProvider(P3, quota);

        IAccountChainResolver accountResolver = provider -> new AccountChain(List.of());
        IProviderFailoverChainResolver failoverResolver = failoverResolver(P2, P3);
        LlmCallCoordinator coordinator = newCoordinator(chat, accountResolver, failoverResolver,
                NoOpProviderFailoverQueue.noOp());

        ChatOptions options = ChatOptions.builder().provider(P1).model("m").build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options),
                "全部 provider 耗尽必须 fail-loud（设计 §6.9），不静默降级");
        assertTrue(ex.getMessage().contains("account chain"),
                "fail-loud 须识别 account-chain 通道. Was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("cross-provider"),
                "fail-loud 须明示跨 provider 链也耗尽. Was: " + ex.getMessage());
        // p1 主 + p2 主 + p3 主 = 3 次（无账号链，每 provider 直接耗尽 failover）。
        assertEquals(3, chat.calls.get(), "每 provider 主账号调用一次，全部耗尽后 fail-loud");
    }

    // ========================================================================
    // 零回归：无 provider 链配置 → 账号链耗尽仍 fail-loud（今日行为）
    // ========================================================================

    @Test
    void noFailoverChainConfiguredAccountExhaustedFailsLoud() {
        ProviderAwareChatService chat = new ProviderAwareChatService();
        chat.onProvider(P1, ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null));

        IAccountChainResolver accountResolver = provider -> new AccountChain(List.of());
        // 空 failover chain（primary 不在表 / 配置缺省）。
        IProviderFailoverChainResolver failoverResolver = primary -> new ProviderFailoverChain(List.of());
        LlmCallCoordinator coordinator = newCoordinator(chat, accountResolver, failoverResolver,
                NoOpProviderFailoverQueue.noOp());

        ChatOptions options = ChatOptions.builder().provider(P1).model("m").build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options),
                "无 provider 链 + 账号链耗尽必须 fail-loud（零回归——今日行为）");
        assertTrue(ex.getMessage().contains("account chain"));
        assertEquals(1, chat.calls.get(), "p1 主账号调用一次，无链可切，fail-loud");
    }

    // ========================================================================
    // failover_switch 去重：冷却期内跳过刚失败 provider（防震荡，裁定 D）
    // ========================================================================

    @Test
    void dedupSkipsRecentlyFailedProviderWithinCooldown() {
        // p1 账号链耗尽 → failover 链 [p2, p3]。p2 已在冷却期（跨调用失败过）→ 跳过 p2 → 切 p3 → 成功。
        ProviderAwareChatService chat = new ProviderAwareChatService();
        ChatResponse quota = ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null);
        chat.onProvider(P1, quota);
        chat.onProvider(P2, successResponse()); // p2 在链中但被去重跳过，不会实际调用
        chat.onProvider(P3, successResponse());

        AtomicLong clock = new AtomicLong(0L);
        ProviderFailoverQueue queue = new ProviderFailoverQueue(60_000L, clock::get);
        // 预置 p2 为冷却中（模拟前一次调用 p2 失败）。
        queue.recordProviderFailure(P2);
        assertFalse(queue.isProviderAvailable(P2), "前置：p2 冷却中");

        IAccountChainResolver accountResolver = provider -> new AccountChain(List.of());
        IProviderFailoverChainResolver failoverResolver = failoverResolver(P2, P3);
        LlmCallCoordinator coordinator = newCoordinator(chat, accountResolver, failoverResolver, queue);

        ChatOptions options = ChatOptions.builder().provider(P1).model("m").build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess(), "跳过冷却中的 p2，failover 到 p3 成功");
        assertEquals(P3, result.routedOptions.getProvider(),
                "去重生效——p2 被跳过，切到 p3（非 p2）");
        assertFalse(chat.seenProviders.contains(P2),
                "p2 冷却中未被实际调用（去重防震荡）");
        assertEquals(List.of(P1, P3), chat.seenProviders,
                "provider 序列：p1（耗尽）→ 跳过 p2（冷却）→ p3（成功）");
    }

    // ========================================================================
    // 账号链通道行为不变：QUOTA 走账号链 + 跨 provider，getFallback 不被调用
    // ========================================================================

    @Test
    void quotaRoutesToAccountThenProviderChainNotModelTier() {
        ProviderAwareChatService chat = new ProviderAwareChatService();
        chat.onProvider(P1, ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null));
        chat.onProvider(P2, successResponse());

        AtomicInteger fallbackCalls = new AtomicInteger();
        io.nop.ai.agent.router.IModelRouter recordingRouter = new io.nop.ai.agent.router.IModelRouter() {
            @Override
            public io.nop.ai.agent.router.RoutingResult route(
                    List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                return new io.nop.ai.agent.router.RoutingResult(options, "test", "test");
            }

            @Override
            public ChatOptions getFallback(ChatOptions currentOptions) {
                fallbackCalls.incrementAndGet();
                return null;
            }
        };

        IAccountChainResolver accountResolver = provider -> new AccountChain(List.of());
        IProviderFailoverChainResolver failoverResolver = failoverResolver(P2);
        AgentHookInvoker invoker = new AgentHookInvoker(
                new io.nop.ai.agent.hook.DefaultHookRegistry(), null);
        LlmCallCoordinator coordinator = new LlmCallCoordinator(
                chat, new StandardRetryPolicy(3, 1L, 10L),
                AlwaysClosed.alwaysClosed(), recordingRouter, 0, null, invoker,
                accountResolver, NoOpProviderFailoverQueue.noOp(), failoverResolver);

        ChatOptions options = ChatOptions.builder().provider(P1).model("m").build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess(), "QUOTA → 账号链（空）→ 跨 provider → p2 成功");
        assertEquals(0, fallbackCalls.get(),
                "QUOTA 走账号链+跨 provider 通道，不调 model-tier getFallback（三通道区分）");
    }

    // ========================================================================
    // 嵌套循环：provider 切换重置 accountChain（新 provider 有自己的账号链）
    // ========================================================================

    @Test
    void providerSwitchResetsAccountChainForNewProvider() {
        // p1 有备用账号链 [a1]（耗尽后 failover），p2 也有备用账号链 [a2]（p2 主账号 QUOTA，a2 成功）。
        ProviderAwareChatService chat = new ProviderAwareChatService();
        ChatResponse quota = ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null);
        // p1: 主 QUOTA + 备用 a1 QUOTA。
        chat.onProvider(P1, quota);
        // p2: 主 QUOTA + 备用 a2 成功（验证 accountChain 重置——p2 走自己的账号链）。
        chat.onProvider(P2, quota);

        IAccountChainResolver accountResolver = provider -> {
            if (P1.equals(provider)) return new AccountChain(accounts("sk-p1-b"));
            if (P2.equals(provider)) return new AccountChain(accounts("sk-p2-b"));
            return new AccountChain(List.of());
        };
        // 让 p2 备用账号成功：用 accountKey 感知的二次映射。
        ProviderAwareChatService chat2 = new ProviderAwareChatService() {
            @Override
            protected ChatResponse respond(String provider, String accountKey) {
                if (P2.equals(provider) && "sk-p2-b".equals(accountKey)) {
                    return successResponse();
                }
                return super.respond(provider, accountKey);
            }
        };
        chat2.onProvider(P1, quota);
        chat2.onProvider(P2, quota);

        IProviderFailoverChainResolver failoverResolver = failoverResolver(P2);
        LlmCallCoordinator coordinator = newCoordinator(chat2, accountResolver, failoverResolver,
                NoOpProviderFailoverQueue.noOp());

        ChatOptions options = ChatOptions.builder().provider(P1).model("m").build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess(), "p1 账号链耗尽 → failover p2 → p2 账号链 a2 成功");
        assertEquals(P2, result.routedOptions.getProvider());
        assertEquals("sk-p2-b", result.routedOptions.getAccountKey(),
                "accountChain 重置——p2 走自己的账号链切到 a2（裁定 C 嵌套循环重置）");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static LlmCallCoordinator newCoordinator(IChatService chat,
                                                      IAccountChainResolver accountResolver,
                                                      IProviderFailoverChainResolver failoverResolver,
                                                      IProviderFailoverQueue queue) {
        AgentHookInvoker invoker = new AgentHookInvoker(
                new io.nop.ai.agent.hook.DefaultHookRegistry(), null);
        return new LlmCallCoordinator(
                chat, new StandardRetryPolicy(3, 1L, 10L),
                AlwaysClosed.alwaysClosed(),
                PassThroughModelRouter.passThrough(),
                0, null, invoker, accountResolver, queue, failoverResolver);
    }

    private static AgentExecutionContext ctx() {
        AgentExecutionContext ctx = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        ctx.setStatus(AgentExecStatus.running);
        return ctx;
    }

    private static List<LlmAccountModel> accounts(String... apiKeys) {
        List<LlmAccountModel> list = new ArrayList<>();
        for (int i = 0; i < apiKeys.length; i++) {
            LlmAccountModel a = new LlmAccountModel();
            a.setId("backup-" + (i + 1));
            a.setApiKey(apiKeys[i]);
            list.add(a);
        }
        return list;
    }

    private static IProviderFailoverChainResolver failoverResolver(String... providers) {
        List<LlmFailoverProviderModel> list = new ArrayList<>();
        for (String p : providers) {
            LlmFailoverProviderModel m = new LlmFailoverProviderModel();
            m.setProvider(p);
            list.add(m);
        }
        final List<LlmFailoverProviderModel> fixed = list;
        return primary -> new ProviderFailoverChain(fixed);
    }

    private static ChatResponse successResponse() {
        ChatResponse r = new ChatResponse();
        io.nop.ai.api.chat.messages.ChatAssistantMessage msg = new io.nop.ai.api.chat.messages.ChatAssistantMessage();
        msg.setContent("ok");
        r.addMessage(msg);
        return r;
    }

    /**
     * 假 {@link IChatService}：按当前 provider（从 request.options 读）返回预设响应。
     * 默认按 provider 维度响应；子类可覆写 {@link #respond} 做 accountKey 感知。
     */
    static class ProviderAwareChatService implements IChatService {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> seenProviders = new java.util.concurrent.CopyOnWriteArrayList<>();
        final Map<String, ChatResponse> byProvider = new HashMap<>();

        void onProvider(String provider, ChatResponse response) {
            byProvider.put(provider, response);
        }

        protected ChatResponse respond(String provider, String accountKey) {
            ChatResponse resp = byProvider.get(provider);
            if (resp == null) {
                return successResponse(); // 兜底：未注册 provider 返回成功（避免误配无限循环）。
            }
            return resp;
        }

        private ChatResponse dispatch(ChatRequest request) {
            calls.incrementAndGet();
            String provider = request.getOptions() != null ? request.getOptions().getProvider() : null;
            String accountKey = request.getOptions() != null ? request.getOptions().getAccountKey() : null;
            seenProviders.add(provider);
            return respond(provider, accountKey);
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            return dispatch(request);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(dispatch(request));
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    /**
     * 记录型 {@link IProviderFailoverQueue}：委托 NoOp 行为但记录 record 调用（接线验证 Rule #23）。
     */
    static final class RecordingProviderQueue implements IProviderFailoverQueue {
        final java.util.Set<String> failures = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final java.util.Set<String> successes = java.util.concurrent.ConcurrentHashMap.newKeySet();

        boolean failureRecorded(String p) { return failures.contains(p); }

        boolean successRecorded(String p) { return successes.contains(p); }

        @Override
        public void recordProviderFailure(String provider) { failures.add(provider); }

        @Override
        public void recordProviderSuccess(String provider) { successes.add(provider); }

        @Override
        public boolean isProviderAvailable(String provider) { return true; }
    }
}
