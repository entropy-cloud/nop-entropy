package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.AccountChain;
import io.nop.ai.agent.reliability.AlwaysClosed;
import io.nop.ai.agent.reliability.IAccountChainResolver;
import io.nop.ai.agent.reliability.StandardRetryPolicy;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.LlmAccountModel;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-01-1505-1 Phase 3 端到端测试：账号回退链 + QUOTA/AUTH→FALLBACK 分类路由
 * （设计 §3.6/§4.4/§6.8/§6.9，Minimum Rules #22 端到端 / #23 接线 / #24 无静默跳过 / #25 新功能）。
 *
 * <p>直接构造 {@link LlmCallCoordinator}（不经 DefaultAgentEngine），注入假 {@link IChatService}
 * + 假 {@link IAccountChainResolver}，从 {@code doLlmCallWithRetry} 入口到最终成功/fail-loud
 * 完整跑通——验证：
 * <ol>
 *   <li>QUOTA_EXCEEDED 响应 → FALLBACK → 账号链切到下一个账号 → 重试成功（多账号链）。</li>
 *   <li>账号链耗尽 → fail-loud 抛异常（不静默、不降级模型）。</li>
 *   <li>两通道区分：QUOTA/AUTH 走账号链（{@code getFallback} 不被调用）；</li>
 *   <li>无账号链配置时 QUOTA/AUTH → FALLBACK → 无链 → fail-loud（设计 §6.9）。</li>
 * </ol>
 */
public class TestAccountFallbackChain {

    private static final String PROVIDER = "test-provider";
    private static final String PRIMARY_KEY = "sk-primary";
    private static final String BACKUP_1 = "sk-backup-1";
    private static final String BACKUP_2 = "sk-backup-2";

    // ========================================================================
    // 端到端：QUOTA → 账号链切换 → 成功
    // ========================================================================

    @Test
    void quotaExceededSwitchesToBackupAccountAndSucceeds() {
        // 假 chat service：主账号（accountKey null 或 PRIMARY）返回 QUOTA，备用账号返回成功。
        AccountAwareChatService chat = new AccountAwareChatService();
        chat.onAccount(PRIMARY_KEY, ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null));
        // 主账号路径：accountKey 为 null 时也视为 PRIMARY（首次调用 accountKey 未设）。
        chat.onAccount(null, ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null));
        // 备用账号 1 仍 QUOTA（测试多级切换）。
        chat.onAccount(BACKUP_1, ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null));
        // 备用账号 2 成功。
        chat.onAccount(BACKUP_2, successResponse());

        IAccountChainResolver resolver = chainResolver(BACKUP_1, BACKUP_2);
        LlmCallCoordinator coordinator = newCoordinator(chat, new StandardRetryPolicy(3, 1L, 10L), resolver);

        ChatOptions options = ChatOptions.builder()
                .provider(PROVIDER).model("m").accountKey(PRIMARY_KEY).build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess(), "account-chain switch to backup-2 must succeed");
        // 调用序列：primary(QUOTA) → backup1(QUOTA) → backup2(success) = 3 次。
        assertEquals(3, chat.calls.get(),
                "call sequence: primary QUOTA -> backup1 QUOTA -> backup2 success");
        // 接线验证（Rule #23）：每次 FALLBACK 都推进了 accountKey。
        assertEquals(List.of(PRIMARY_KEY, BACKUP_1, BACKUP_2), chat.seenAccountKeys,
                "accountKey must advance through the chain on each QUOTA FALLBACK");
    }

    // ========================================================================
    // fail-loud：账号链耗尽
    // ========================================================================

    @Test
    void accountChainExhaustedFailsLoud() {
        // 所有账号（含备用）都 QUOTA → 链耗尽 → fail-loud。
        AccountAwareChatService chat = new AccountAwareChatService();
        ChatResponse quota = ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null);
        chat.onAccount(null, quota);
        chat.onAccount(PRIMARY_KEY, quota);
        chat.onAccount(BACKUP_1, quota);
        chat.onAccount(BACKUP_2, quota);

        IAccountChainResolver resolver = chainResolver(BACKUP_1, BACKUP_2);
        LlmCallCoordinator coordinator = newCoordinator(chat, new StandardRetryPolicy(3, 1L, 10L), resolver);

        ChatOptions options = ChatOptions.builder()
                .provider(PROVIDER).model("m").accountKey(PRIMARY_KEY).build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options),
                "chain exhaustion must fail-loud (design §6.9), not silently degrade/STOP");
        assertTrue(ex.getMessage().contains("account chain"),
                "fail-loud error must identify the account-chain channel. Was: " + ex.getMessage());
        // primary + 2 backups = 3 calls, then exhausted.
        assertEquals(3, chat.calls.get(),
                "all accounts exhausted: primary + backup1 + backup2, then fail-loud");
    }

    // ========================================================================
    // 无账号链配置 → QUOTA → FALLBACK → fail-loud（设计 §6.9）
    // ========================================================================

    @Test
    void noAccountChainConfiguredQuotaFailsLoud() {
        AccountAwareChatService chat = new AccountAwareChatService();
        chat.onAccount(null, ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null));

        // 空 chain（provider 未配置 <accounts>）。
        IAccountChainResolver resolver = provider -> new AccountChain(List.of());
        LlmCallCoordinator coordinator = newCoordinator(chat, new StandardRetryPolicy(3, 1L, 10L), resolver);

        ChatOptions options = ChatOptions.builder().provider(PROVIDER).model("m").build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options),
                "no account chain + QUOTA FALLBACK must fail-loud, not silently continue");
        assertTrue(ex.getMessage().contains("account chain"));
        assertEquals(1, chat.calls.get(), "primary call only, then fail-loud (no chain to walk)");
    }

    // ========================================================================
    // 两通道区分：QUOTA 走账号链，getFallback 不被调用
    // ========================================================================

    @Test
    void quotaRoutesToAccountChainNotModelTier() {
        // 用一个记录 getFallback 调用的 modelRouter；QUOTA 路径不应调它。
        AccountAwareChatService chat = new AccountAwareChatService();
        chat.onAccount(null, ChatResponse.error(
                ErrorClassification.QUOTA_EXCEEDED, 429, "insufficient_quota", "no money", null));
        chat.onAccount(BACKUP_1, successResponse());

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

        IAccountChainResolver resolver = chainResolver(BACKUP_1);
        AgentHookInvoker invoker = new AgentHookInvoker(
                new io.nop.ai.agent.hook.DefaultHookRegistry(), null);
        LlmCallCoordinator coordinator = new LlmCallCoordinator(
                chat, new StandardRetryPolicy(3, 1L, 10L),
                AlwaysClosed.alwaysClosed(), recordingRouter, 0, null, invoker, resolver);

        ChatOptions options = ChatOptions.builder().provider(PROVIDER).model("m").build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess(), "QUOTA → account chain → backup-1 success");
        assertEquals(0, fallbackCalls.get(),
                "QUOTA must route to account chain, NOT model-tier getFallback (two-channel separation, §4.4)");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static LlmCallCoordinator newCoordinator(IChatService chat,
                                                      io.nop.ai.agent.reliability.IRetryPolicy policy,
                                                      IAccountChainResolver resolver) {
        AgentHookInvoker invoker = new AgentHookInvoker(
                new io.nop.ai.agent.hook.DefaultHookRegistry(), null);
        return new LlmCallCoordinator(
                chat, policy, AlwaysClosed.alwaysClosed(),
                PassThroughModelRouter.passThrough(),
                0, null, invoker, resolver);
    }

    private static AgentExecutionContext ctx() {
        AgentExecutionContext ctx = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        ctx.setStatus(AgentExecStatus.running);
        return ctx;
    }

    private static IAccountChainResolver chainResolver(String... apiKeys) {
        List<LlmAccountModel> accounts = new ArrayList<>();
        for (int i = 0; i < apiKeys.length; i++) {
            LlmAccountModel a = new LlmAccountModel();
            a.setId("backup-" + (i + 1));
            a.setApiKey(apiKeys[i]);
            accounts.add(a);
        }
        final List<LlmAccountModel> fixed = accounts;
        return provider -> new AccountChain(fixed);
    }

    private static ChatResponse successResponse() {
        ChatResponse r = new ChatResponse();
        io.nop.ai.api.chat.messages.ChatAssistantMessage msg = new io.nop.ai.api.chat.messages.ChatAssistantMessage();
        msg.setContent("ok");
        r.setMessage(msg);
        return r;
    }

    /**
     * 假 {@link IChatService}：按当前 accountKey（从 request.options 读）返回预设响应。
     * accountKey 为 null 视为主账号（首次调用 accountKey 未设）。记录每次调用的 accountKey 序列。
     */
    private static final class AccountAwareChatService implements IChatService {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> seenAccountKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
        // HashMap 允许 null key（主账号首次调用 accountKey 为 null）；重试循环单线程顺序调用。
        final java.util.Map<String, ChatResponse> byAccount = new java.util.HashMap<>();

        void onAccount(String accountKey, ChatResponse response) {
            byAccount.put(accountKey, response);
        }

        private ChatResponse respond(ChatRequest request) {
            calls.incrementAndGet();
            String key = request.getOptions() != null ? request.getOptions().getAccountKey() : null;
            seenAccountKeys.add(key);
            ChatResponse resp = byAccount.get(key);
            if (resp == null) {
                // 兜底：未注册的 accountKey 返回成功（避免测试误配时无限循环）。
                return successResponse();
            }
            return resp;
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            return respond(request);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(respond(request));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }
}
