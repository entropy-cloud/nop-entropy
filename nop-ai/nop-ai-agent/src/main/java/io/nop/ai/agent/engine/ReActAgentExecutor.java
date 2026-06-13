package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.CompactionContext;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.compact.ToolResultTruncator;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.hook.NoOpHookRegistry;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.repair.NoOpToolCallRepairer;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.router.RoutingResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.NoOpAuditLogger;
import io.nop.ai.agent.security.PathAccessResult;
import io.nop.ai.agent.security.Permission;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ChatOptionsModel;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ReActAgentExecutor implements IAgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ReActAgentExecutor.class);

    public static final int DEFAULT_MAX_REENTRIES = 3;
    public static final double DEFAULT_TRIGGER_TOKEN_PERCENT = 0.8;
    public static final int DEFAULT_TRIGGER_MAX_MESSAGES = 30;
    public static final int DEFAULT_MAX_CONTEXT_TOKENS = 128000;

    private final IChatService chatService;
    private final IToolManager toolManager;
    private final IAgentEventPublisher eventPublisher;
    private final IPermissionProvider permissionProvider;
    private final IToolAccessChecker toolAccessChecker;
    private final IPathAccessChecker pathAccessChecker;
    private final IAuditLogger auditLogger;
    private final IHookRegistry hookRegistry;
    private final IToolCallRepairer toolCallRepairer;
    private final IContextCompactor contextCompactor;
    private final IContentGuardrail contentGuardrail;
    private final IModelRouter modelRouter;
    private final ITokenEstimator tokenEstimator;

    private ReActAgentExecutor(IChatService chatService, IToolManager toolManager,
                               IAgentEventPublisher eventPublisher,
                               IPermissionProvider permissionProvider,
                               IToolAccessChecker toolAccessChecker,
                               IPathAccessChecker pathAccessChecker,
                               IAuditLogger auditLogger,
                               IHookRegistry hookRegistry,
                               IToolCallRepairer toolCallRepairer,
                               IContextCompactor contextCompactor,
                               IContentGuardrail contentGuardrail,
                               IModelRouter modelRouter,
                               ITokenEstimator tokenEstimator) {
        this.chatService = chatService;
        this.toolManager = toolManager;
        this.eventPublisher = eventPublisher;
        this.permissionProvider = permissionProvider;
        this.toolAccessChecker = toolAccessChecker;
        this.pathAccessChecker = pathAccessChecker;
        this.auditLogger = auditLogger;
        this.hookRegistry = hookRegistry != null ? hookRegistry : NoOpHookRegistry.INSTANCE;
        this.toolCallRepairer = toolCallRepairer != null ? toolCallRepairer : NoOpToolCallRepairer.INSTANCE;
        this.contextCompactor = contextCompactor != null ? contextCompactor : NoOpContextCompactor.INSTANCE;
        this.contentGuardrail = contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp();
        this.modelRouter = modelRouter != null ? modelRouter : PassThroughModelRouter.passThrough();
        this.tokenEstimator = tokenEstimator != null ? tokenEstimator : CalibratedTokenEstimator.defaultInstance();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private IChatService chatService;
        private IToolManager toolManager;
        private IAgentEventPublisher eventPublisher;
        private IPermissionProvider permissionProvider;
        private IToolAccessChecker toolAccessChecker;
        private IPathAccessChecker pathAccessChecker;
        private IAuditLogger auditLogger;
        private IHookRegistry hookRegistry;
        private IToolCallRepairer toolCallRepairer;
        private IContextCompactor contextCompactor;
        private IContentGuardrail contentGuardrail;
        private IModelRouter modelRouter;
        private ITokenEstimator tokenEstimator;

        public Builder chatService(IChatService chatService) {
            this.chatService = chatService;
            return this;
        }

        public Builder toolManager(IToolManager toolManager) {
            this.toolManager = toolManager;
            return this;
        }

        public Builder eventPublisher(IAgentEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public Builder permissionProvider(IPermissionProvider permissionProvider) {
            this.permissionProvider = permissionProvider;
            return this;
        }

        public Builder toolAccessChecker(IToolAccessChecker toolAccessChecker) {
            this.toolAccessChecker = toolAccessChecker;
            return this;
        }

        public Builder pathAccessChecker(IPathAccessChecker pathAccessChecker) {
            this.pathAccessChecker = pathAccessChecker;
            return this;
        }

        public Builder auditLogger(IAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
            return this;
        }

        public Builder hookRegistry(IHookRegistry hookRegistry) {
            this.hookRegistry = hookRegistry;
            return this;
        }

        public Builder toolCallRepairer(IToolCallRepairer toolCallRepairer) {
            this.toolCallRepairer = toolCallRepairer;
            return this;
        }

        public Builder contextCompactor(IContextCompactor contextCompactor) {
            this.contextCompactor = contextCompactor;
            return this;
        }

        public Builder contentGuardrail(IContentGuardrail contentGuardrail) {
            this.contentGuardrail = contentGuardrail;
            return this;
        }

        public Builder modelRouter(IModelRouter modelRouter) {
            this.modelRouter = modelRouter;
            return this;
        }

        public Builder tokenEstimator(ITokenEstimator tokenEstimator) {
            this.tokenEstimator = tokenEstimator;
            return this;
        }

        public ReActAgentExecutor build() {
            if (chatService == null) {
                throw new NopAiAgentException("chatService must not be null");
            }
            if (toolManager == null) {
                throw new NopAiAgentException("toolManager must not be null");
            }
            return new ReActAgentExecutor(
                    chatService,
                    toolManager,
                    eventPublisher,
                    permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider(),
                    toolAccessChecker != null ? toolAccessChecker : new AllowAllToolAccessChecker(),
                    pathAccessChecker != null ? pathAccessChecker : new AllowAllPathAccessChecker(),
                    auditLogger != null ? auditLogger : new NoOpAuditLogger(),
                    hookRegistry != null ? hookRegistry : NoOpHookRegistry.INSTANCE,
                    toolCallRepairer != null ? toolCallRepairer : NoOpToolCallRepairer.INSTANCE,
                    contextCompactor != null ? contextCompactor : NoOpContextCompactor.INSTANCE,
                    contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp(),
                    modelRouter != null ? modelRouter : PassThroughModelRouter.passThrough(),
                    tokenEstimator != null ? tokenEstimator : CalibratedTokenEstimator.defaultInstance()
            );
        }
    }

    @Override
    public CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx) {
        AgentModel agentModel = ctx.getAgentModel();

        ctx.setStatus(AgentExecStatus.running);

        String agentName = agentModel != null ? agentModel.getName() : null;
        String sessionId = ctx.getSessionId();

        publishEvent(AgentEventType.EXECUTION_STARTED, sessionId, agentName,
                Map.of("agentName", agentName != null ? agentName : ""));

        List<ChatToolDefinition> toolDefs = buildToolDefinitions(agentModel);
        ChatOptions options = buildChatOptions(agentModel.getChatOptions(), toolDefs);

        Map<AgentLifecyclePoint, Integer> reentryCounters = new HashMap<>();

        try {
            HookResult preCallResult = invokeHooks(AgentLifecyclePoint.PRE_CALL, ctx, agentName, null, null);
            if (preCallResult.isVeto()) {
                ctx.setStatus(AgentExecStatus.completed);
                publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName,
                        Map.of("vetoedAt", "PRE_CALL", "reason", vetoReason(preCallResult)));
                return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
            }

            while (ctx.getCurrentIteration() < ctx.getMaxIterations()) {
                if (ctx.isCancelRequested()) {
                    handleCancellation(ctx, sessionId, agentName);
                    break;
                }

                if (shouldForceStop(ctx)) {
                    handleForcedStop(ctx, sessionId, agentName);
                    break;
                }

                if (shouldTriggerCompaction(ctx)) {
                    performCompaction(ctx, agentName);
                }

                HookResult preReasoningResult = invokeHooks(AgentLifecyclePoint.PRE_REASONING, ctx, agentName, null, null);
                if (preReasoningResult.isVeto()) {
                    ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                    continue;
                }

                GuardrailResult inputGuardrailResult = checkInputGuardrail(ctx);
                if (inputGuardrailResult.isBlock()) {
                    String blockReason = ((GuardrailResult.BlockResult) inputGuardrailResult).getReason();
                    ctx.addMessage(ChatToolResponseMessage.error(
                            "guardrail-block-input", "guardrail",
                            "Input blocked by content guardrail: " + (blockReason != null ? blockReason : "unspecified")));
                    ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                    continue;
                }

                RoutingResult routingResult = modelRouter.route(ctx.getMessages(), options, ctx);
                ChatOptions routedOptions = routingResult.getOptions();

                ChatRequest request = new ChatRequest(new ArrayList<>(ctx.getMessages()));
                request.setOptions(routedOptions);
                List<ChatMessage> messagesAtCallTime = request.getMessages();

                ChatResponse response = chatService.call(request, null);

                if (!response.isSuccess()) {
                    ctx.setStatus(AgentExecStatus.failed);
                    ctx.setLastError(response.getError());

                    invokeOnError(ctx, agentName);
                    publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName,
                            response.getError());

                    break;
                }

                ChatAssistantMessage assistantMsg = response.getMessage();
                ctx.addMessage(assistantMsg);

                if (response.getUsage() != null) {
                    int promptTokens = response.getPromptTokens() != null ? response.getPromptTokens() : 0;
                    int completionTokens = response.getCompletionTokens() != null ? response.getCompletionTokens() : 0;
                    ctx.setTokensUsed(ctx.getTokensUsed() + promptTokens + completionTokens);

                    if (promptTokens > 0) {
                        tokenEstimator.record(messagesAtCallTime, promptTokens);
                    }
                }

                Map<String, Object> llmPayload = new HashMap<>();
                llmPayload.put("iteration", ctx.getCurrentIteration());
                llmPayload.put("hasToolCalls", assistantMsg.hasToolCalls());
                publishEvent(AgentEventType.LLM_RESPONSE_RECEIVED, sessionId, agentName, llmPayload);

                invokeHooks(AgentLifecyclePoint.POST_REASONING, ctx, agentName, null, null);

                String outputContent = assistantMsg.getContent() != null ? assistantMsg.getContent() : "";
                GuardrailResult outputGuardrailResult = contentGuardrail.check(GuardrailDirection.OUTPUT, outputContent, ctx);
                if (outputGuardrailResult.isBlock()) {
                    String blockReason = ((GuardrailResult.BlockResult) outputGuardrailResult).getReason();
                    ctx.addMessage(ChatToolResponseMessage.error(
                            "guardrail-block-output", "guardrail",
                            "Output blocked by content guardrail: " + (blockReason != null ? blockReason : "unspecified")));
                    ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                    continue;
                }
                if (outputGuardrailResult.isModify()) {
                    String modifiedContent = ((GuardrailResult.ModifyResult) outputGuardrailResult).getContent();
                    assistantMsg.setContent(modifiedContent);
                }

                if (!assistantMsg.hasToolCalls()) {
                    ctx.setStatus(AgentExecStatus.completed);
                    break;
                }

                SimpleToolExecuteContext toolExecCtx = new SimpleToolExecuteContext(
                        new File("."), null, null);

                List<ChatToolCall> allowedCalls = new ArrayList<>();

                for (ChatToolCall chatToolCall : assistantMsg.getToolCalls()) {
                    chatToolCall = toolCallRepairer.repair(chatToolCall, ctx);

                    String toolName = chatToolCall.getName();

                    publishEvent(AgentEventType.TOOL_CALL_STARTED, sessionId, agentName,
                            Map.of("toolName", toolName,
                                    "iteration", ctx.getCurrentIteration()));

                    ToolAccessResult accessResult = toolAccessChecker.checkAccess(toolName, ctx);
                    auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                            accessResult.isAllowed() ? AuditDecision.ALLOW : AuditDecision.DENY,
                            accessResult.getReason(), accessResult.getMatchedRule(), null,
                            System.currentTimeMillis()));
                    if (!accessResult.isAllowed()) {
                        publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                                Map.of("toolName", toolName,
                                        "reason", accessResult.getReason() != null ? accessResult.getReason() : ""));

                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                "Access denied: " + (accessResult.getReason() != null ? accessResult.getReason() : "hardcoded deny"));
                        ctx.addMessage(toolResponse);
                        continue;
                    }

                    Permission perm = permissionProvider.resolve(toolName, agentName, sessionId);
                    auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                            perm.isAllowed() ? AuditDecision.ALLOW : AuditDecision.DENY,
                            perm.getReason(), perm.getMatchedRuleId(), null,
                            System.currentTimeMillis()));
                    if (!perm.isAllowed()) {
                        publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                                Map.of("toolName", toolName,
                                        "reason", perm.getReason() != null ? perm.getReason() : ""));

                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                "Permission denied: " + (perm.getReason() != null ? perm.getReason() : "access denied"));
                        ctx.addMessage(toolResponse);
                        continue;
                    }

                    String pathDenied = checkPathAccess(chatToolCall, ctx, sessionId, agentName);
                    if (pathDenied != null) {
                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                "Path access denied: " + pathDenied);
                        ctx.addMessage(toolResponse);
                        continue;
                    }

                    allowedCalls.add(chatToolCall);
                }

                if (!allowedCalls.isEmpty()) {
                    List<CompletableFuture<ToolCallOutput>> futures = new ArrayList<>();
                    for (ChatToolCall chatToolCall : allowedCalls) {
                        AiToolCall aiToolCall = new AiToolCall();
                        aiToolCall.setToolName(chatToolCall.getName());
                        aiToolCall.setInput(chatToolCall.getArgumentsText());

                        futures.add(toolManager.callTool(chatToolCall.getName(), aiToolCall, toolExecCtx)
                                .thenApply(result -> new ToolCallOutput(chatToolCall, result)));
                    }

                    @SuppressWarnings("unchecked")
                    CompletableFuture<ToolCallOutput>[] futuresArray = futures.toArray(new CompletableFuture[0]);
                    CompletableFuture.allOf(futuresArray).join();

                    for (CompletableFuture<ToolCallOutput> f : futuresArray) {
                        ToolCallOutput output = f.join();
                        ChatToolCall chatToolCall = output.chatToolCall;
                        AiToolCallResult toolResult = output.result;
                        String toolName = chatToolCall.getName();

                        invokeHooks(AgentLifecyclePoint.PRE_ACTING, ctx, agentName, toolName, chatToolCall.getId());

                        String toolStatus;
                        ChatToolResponseMessage toolResponse;
                        if ("success".equals(toolResult.getStatus()) && toolResult.getError() == null) {
                            String resultText = toolResult.getOutput() != null ? toolResult.getOutput().getBody() : "";
                            resultText = resultText != null ? resultText : "";
                            resultText = ToolResultTruncator.truncateIfAllowed(
                                    resultText,
                                    ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                                    toolName);
                            toolResponse = ChatToolResponseMessage.fromToolCall(chatToolCall, resultText);
                            toolStatus = "success";
                        } else {
                            String errorMsg = toolResult.getError() != null ? toolResult.getError().getBody() : "unknown error";
                            toolResponse = ChatToolResponseMessage.error(
                                    chatToolCall.getId(),
                                    chatToolCall.getName(),
                                    errorMsg != null ? errorMsg : "unknown error");
                            toolStatus = "error";
                        }

                        HookResult beforeResult = invokeHooks(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED,
                                ctx, agentName, toolName, chatToolCall.getId());
                        if (beforeResult instanceof HookResult.ReenterResult) {
                            int count = reentryCounters.getOrDefault(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, 0);
                            if (count >= DEFAULT_MAX_REENTRIES) {
                                LOG.warn("Re-entry limit ({}) reached at BEFORE_TOOL_RESULT_PROCESSED, forcing PassResult",
                                        DEFAULT_MAX_REENTRIES);
                            } else {
                                reentryCounters.put(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, count + 1);
                                String reenterMsg = ((HookResult.ReenterResult) beforeResult).getMessage();
                                ctx.addMessage(ChatToolResponseMessage.fromToolCall(chatToolCall,
                                        reenterMsg != null ? reenterMsg : "hook re-enter"));
                                break;
                            }
                        }

                        ctx.addMessage(toolResponse);

                        invokeHooks(AgentLifecyclePoint.POST_ACTING, ctx, agentName, toolName, chatToolCall.getId());

                        HookResult afterResult = invokeHooks(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED,
                                ctx, agentName, toolName, chatToolCall.getId());
                        if (afterResult instanceof HookResult.ReenterResult) {
                            int count = reentryCounters.getOrDefault(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED, 0);
                            if (count >= DEFAULT_MAX_REENTRIES) {
                                LOG.warn("Re-entry limit ({}) reached at AFTER_TOOL_RESULT_PROCESSED, forcing PassResult",
                                        DEFAULT_MAX_REENTRIES);
                            } else {
                                reentryCounters.put(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED, count + 1);
                                break;
                            }
                        }

                        publishEvent(AgentEventType.TOOL_CALL_COMPLETED, sessionId, agentName,
                                Map.of("toolName", chatToolCall.getName(),
                                        "status", toolStatus));
                    }
                }

                if (ctx.isCancelRequested()) {
                    handleCancellation(ctx, sessionId, agentName);
                    break;
                }

                ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            }

            if (ctx.getStatus() == AgentExecStatus.running) {
                ctx.setStatus(AgentExecStatus.completed);
            }

            if (ctx.getStatus() != AgentExecStatus.cancelled
                    && ctx.getStatus() != AgentExecStatus.forced_stopped) {
                invokeHooks(AgentLifecyclePoint.POST_CALL, ctx, agentName, null, null);

                Map<String, Object> completedPayload = new HashMap<>();
                completedPayload.put("totalIterations", ctx.getCurrentIteration());
                completedPayload.put("totalTokensUsed", ctx.getTokensUsed());
                completedPayload.put("durationMs", System.currentTimeMillis() - ctx.getStartTimeMs());
                publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName, completedPayload);
            }

        } catch (Exception e) {
            if (ctx.isCancelRequested()) {
                Thread.currentThread().interrupt();
                handleCancellation(ctx, sessionId, agentName);
            } else {
                ctx.setStatus(AgentExecStatus.failed);
                ctx.setLastError(e.toString());

                invokeOnError(ctx, agentName);
                publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
            }
        }

        return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
    }

    private void handleCancellation(AgentExecutionContext ctx, String sessionId, String agentName) {
        ctx.setStatus(AgentExecStatus.cancelled);
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", ctx.getCancelReason() != null ? ctx.getCancelReason() : "");
        publishEvent(AgentEventType.SESSION_CANCELLED, sessionId, agentName, payload);
    }

    /**
     * Layer 4 forced-stop hard protection (design §7.2 Layer 4 / §7.3). Uses the
     * calibrated {@link ITokenEstimator}'s <b>pre-call</b> estimate: if the
     * estimated pending request exceeds {@code maxContextTokens *
     * forcedStopPercent} (default 0.9), forced stop fires.
     */
    private boolean shouldForceStop(AgentExecutionContext ctx) {
        long maxContextTokens = resolveMaxContextTokens(ctx);
        double forcedStopPercent = CompactConfig.defaults().getForcedStopPercent();
        long estimate = tokenEstimator.estimateTokens(ctx.getMessages());
        if (estimate > maxContextTokens * forcedStopPercent) {
            LOG.warn("Forced-stop triggered: pre-call estimate {} exceeds {}% of maxContextTokens {}. session={}",
                    estimate, forcedStopPercent, maxContextTokens, ctx.getSessionId());
            return true;
        }
        return false;
    }

    private void handleForcedStop(AgentExecutionContext ctx, String sessionId, String agentName) {
        long maxContextTokens = resolveMaxContextTokens(ctx);
        long estimate = tokenEstimator.estimateTokens(ctx.getMessages());

        // Best-effort final summary: run the compaction pipeline (Layer 1 -> 2 -> 3)
        // so a final summary/tail is retained for the record. Never fails the agent.
        try {
            performCompaction(ctx, agentName);
        } catch (Exception e) {
            LOG.warn("Final-summary compaction during forced stop failed, continuing with tail retention. session={}",
                    sessionId, e);
        }

        ctx.setStatus(AgentExecStatus.forced_stopped);
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "context-window-overflow");
        payload.put("estimatedTokens", estimate);
        payload.put("maxContextTokens", maxContextTokens);
        payload.put("forcedStopPercent", CompactConfig.defaults().getForcedStopPercent());
        publishEvent(AgentEventType.FORCED_STOP, sessionId, agentName, payload);
    }

    private boolean shouldTriggerCompaction(AgentExecutionContext ctx) {
        long maxContextTokens = resolveMaxContextTokens(ctx);
        if (ctx.getTokensUsed() > maxContextTokens * DEFAULT_TRIGGER_TOKEN_PERCENT) {
            return true;
        }
        return ctx.getMessages().size() > DEFAULT_TRIGGER_MAX_MESSAGES;
    }

    private long resolveMaxContextTokens(AgentExecutionContext ctx) {
        if (ctx.getChatOptionsModel() != null && ctx.getChatOptionsModel().getMaxTokens() != null) {
            return ctx.getChatOptionsModel().getMaxTokens();
        }
        return DEFAULT_MAX_CONTEXT_TOKENS;
    }

    private void performCompaction(AgentExecutionContext ctx, String agentName) {
        CompactConfig config = CompactConfig.defaults();

        CompactionContext compactCtx = new CompactionContext(
                new ArrayList<>(ctx.getMessages()),
                config,
                ctx.getSessionId(),
                agentName,
                ctx,
                tokenEstimator
        );

        invokeHooks(AgentLifecyclePoint.PRE_COMPACT, ctx, agentName, null, null);

        CompactionResult result = contextCompactor.compact(compactCtx);

        invokeHooks(AgentLifecyclePoint.POST_COMPACT, ctx, agentName, null, null);

        if (result.getCompactedMessages() != null) {
            if (result.getCompactedMessages().isEmpty()) {
                LOG.warn("Compactor returned empty compactedMessages for session {}, skipping replacement",
                        ctx.getSessionId());
            } else if (result.getTokensAfter() < result.getTokensBefore()) {
                ctx.getMessages().clear();
                ctx.getMessages().addAll(result.getCompactedMessages());
                ctx.setTokensUsed(ctx.getTokensUsed() - (result.getTokensBefore() - result.getTokensAfter()));
                LOG.info("Context compacted: tokens {} -> {}, retained {} messages for session {}",
                        result.getTokensBefore(), result.getTokensAfter(),
                        result.getRetainedMessageCount(), ctx.getSessionId());
            }
        }
    }

    private HookResult invokeHooks(AgentLifecyclePoint point, AgentExecutionContext ctx,
                                   String agentName, String toolName, String toolCallId) {
        List<IAgentLifecycleHook> hooks = hookRegistry.getHooks(point, agentName);
        if (hooks.isEmpty()) {
            return HookResult.PassResult.instance();
        }

        for (IAgentLifecycleHook hook : hooks) {
            try {
                HookContext hookCtx = new HookContext(point, ctx);
                hookCtx.setToolName(toolName);
                hookCtx.setToolCallId(toolCallId);
                HookResult result = hook.onEvent(hookCtx);

                if (result instanceof HookResult.ReenterResult) {
                    if (point != AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED
                            && point != AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED) {
                        throw new NopAiAgentException(
                                "ReenterResult is only valid at re-entrant hook points (BEFORE_TOOL_RESULT_PROCESSED, AFTER_TOOL_RESULT_PROCESSED), got: " + point);
                    }
                    return result;
                }

                if (result.isVeto()) {
                    return result;
                }
            } catch (Exception e) {
                if (point == AgentLifecyclePoint.ON_ERROR) {
                    LOG.warn("on_error hook failed, using engine default error handling", e);
                } else if (point.name().startsWith("PRE_") || point.name().startsWith("BEFORE_")) {
                    LOG.error("before_* hook at {} failed", point, e);
                    throw e;
                } else {
                    LOG.warn("after_* hook at {} failed, continuing", point, e);
                }
            }
        }
        return HookResult.PassResult.instance();
    }

    private void invokeOnError(AgentExecutionContext ctx, String agentName) {
        try {
            invokeHooks(AgentLifecyclePoint.ON_ERROR, ctx, agentName, null, null);
        } catch (Exception e) {
            LOG.warn("ON_ERROR hook invocation failed", e);
        }
    }

    private String vetoReason(HookResult result) {
        if (result instanceof HookResult.VetoResult) {
            return ((HookResult.VetoResult) result).getReason();
        }
        return "vetoed";
    }

    private void publishEvent(AgentEventType type, String sessionId, String agentName,
                              Map<String, Object> payload) {
        if (eventPublisher != null) {
            eventPublisher.publish(AgentEvent.create(type, sessionId, agentName, payload));
        }
    }

    private void publishErrorEvent(AgentEventType type, String sessionId, String agentName,
                                   String error) {
        if (eventPublisher != null) {
            eventPublisher.publish(AgentEvent.createError(type, sessionId, agentName, error));
        }
    }

    private static final Set<String> PATH_ARG_KEYS = Set.of(
            "path", "file", "filePath", "filename", "directory", "dir",
            "destination", "output", "input", "source", "target", "cwd"
    );

    private String checkPathAccess(ChatToolCall chatToolCall, AgentExecutionContext ctx,
                                   String sessionId, String agentName) {
        Map<String, Object> arguments = chatToolCall.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!PATH_ARG_KEYS.contains(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String pathValue = (String) value;
            if (pathValue == null || pathValue.trim().isEmpty()) {
                continue;
            }

            PathAccessResult pathResult = pathAccessChecker.checkAccess(pathValue, ctx);
            if (!pathResult.isAllowed()) {
                auditLogger.log(new AuditEvent(sessionId, agentName, null, chatToolCall.getName(),
                        AuditDecision.DENY, pathResult.getReason(), pathResult.getMatchedRule(),
                        pathValue, System.currentTimeMillis()));
                publishEvent(AgentEventType.PATH_ACCESS_DENIED, sessionId, agentName,
                        Map.of("path", pathValue,
                                "reason", pathResult.getReason() != null ? pathResult.getReason() : ""));
                return pathResult.getReason() != null ? pathResult.getReason() : "path access denied";
            }
        }

        return null;
    }

    private GuardrailResult checkInputGuardrail(AgentExecutionContext ctx) {
        String inputContent = extractLastUserContent(ctx);
        GuardrailResult result = contentGuardrail.check(GuardrailDirection.INPUT, inputContent, ctx);
        if (result.isModify()) {
            String modifiedContent = ((GuardrailResult.ModifyResult) result).getContent();
            List<ChatMessage> messages = ctx.getMessages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof ChatUserMessage) {
                    messages.get(i).setContent(modifiedContent);
                    break;
                }
            }
        }
        return result;
    }

    private String extractLastUserContent(AgentExecutionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ChatUserMessage) {
                return messages.get(i).getContent();
            }
        }
        return "";
    }

    private List<ChatToolDefinition> buildToolDefinitions(AgentModel agentModel) {
        List<ChatToolDefinition> defs = new ArrayList<>();
        if (agentModel.getTools() == null)
            return defs;

        for (String toolName : agentModel.getTools()) {
            AiToolModel toolModel = toolManager.loadTool(toolName);
            if (toolModel == null)
                continue;

            Map<String, Object> parameters = ToolSchemaConverter.convert(toolModel.getSchema());
            ChatToolDefinition def;
            if (parameters != null) {
                def = ChatToolDefinition.of(toolModel.getName(), toolModel.getDescription(), parameters);
            } else {
                def = ChatToolDefinition.of(toolModel.getName(), toolModel.getDescription());
            }
            defs.add(def);
        }
        return defs;
    }

    private ChatOptions buildChatOptions(ChatOptionsModel model, List<ChatToolDefinition> toolDefs) {
        ChatOptions options = new ChatOptions();
        if (model != null) {
            if (model.getProvider() != null)
                options.setProvider(model.getProvider());
            if (model.getModel() != null)
                options.setModel(model.getModel());
            if (model.getTemperature() != null)
                options.setTemperature(model.getTemperature());
            if (model.getTopP() != null)
                options.setTopP(model.getTopP());
            if (model.getMaxTokens() != null)
                options.setMaxTokens(model.getMaxTokens());
            if (model.getTopK() != null)
                options.setTopK(model.getTopK());
            if (model.getStop() != null)
                options.setStop(model.getStop());
        }
        if (!toolDefs.isEmpty()) {
            options.setTools(toolDefs);
            options.autoToolChoice();
        }
        return options;
    }

    private static class ToolCallOutput {
        final ChatToolCall chatToolCall;
        final AiToolCallResult result;

        ToolCallOutput(ChatToolCall chatToolCall, AiToolCallResult result) {
            this.chatToolCall = chatToolCall;
            this.result = result;
        }
    }
}
