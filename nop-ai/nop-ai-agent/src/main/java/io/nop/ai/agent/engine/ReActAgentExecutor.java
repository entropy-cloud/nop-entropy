package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.PathAccessResult;
import io.nop.ai.agent.security.Permission;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.core.model.ChatOptionsModel;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ReActAgentExecutor implements IAgentExecutor {

    private final IChatService chatService;
    private final IToolManager toolManager;
    private final IAgentEventPublisher eventPublisher;
    private final IPermissionProvider permissionProvider;
    private final IToolAccessChecker toolAccessChecker;
    private final IPathAccessChecker pathAccessChecker;

    public ReActAgentExecutor(IChatService chatService, IToolManager toolManager) {
        this(chatService, toolManager, null, new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(), new AllowAllPathAccessChecker());
    }

    public ReActAgentExecutor(IChatService chatService, IToolManager toolManager,
                              IAgentEventPublisher eventPublisher) {
        this(chatService, toolManager, eventPublisher, new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(), new AllowAllPathAccessChecker());
    }

    public ReActAgentExecutor(IChatService chatService, IToolManager toolManager,
                              IAgentEventPublisher eventPublisher,
                              IPermissionProvider permissionProvider) {
        this(chatService, toolManager, eventPublisher, permissionProvider,
                new AllowAllToolAccessChecker(), new AllowAllPathAccessChecker());
    }

    public ReActAgentExecutor(IChatService chatService, IToolManager toolManager,
                              IAgentEventPublisher eventPublisher,
                              IPermissionProvider permissionProvider,
                              IToolAccessChecker toolAccessChecker) {
        this(chatService, toolManager, eventPublisher, permissionProvider,
                toolAccessChecker, new AllowAllPathAccessChecker());
    }

    public ReActAgentExecutor(IChatService chatService, IToolManager toolManager,
                              IAgentEventPublisher eventPublisher,
                              IPermissionProvider permissionProvider,
                              IToolAccessChecker toolAccessChecker,
                              IPathAccessChecker pathAccessChecker) {
        this.chatService = chatService;
        this.toolManager = toolManager;
        this.eventPublisher = eventPublisher;
        this.permissionProvider = permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider();
        this.toolAccessChecker = toolAccessChecker != null ? toolAccessChecker : new AllowAllToolAccessChecker();
        this.pathAccessChecker = pathAccessChecker != null ? pathAccessChecker : new AllowAllPathAccessChecker();
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

        try {
            while (ctx.getCurrentIteration() < ctx.getMaxIterations()) {
                ChatRequest request = new ChatRequest(new ArrayList<>(ctx.getMessages()));
                request.setOptions(options);

                ChatResponse response = chatService.call(request, null);

                if (!response.isSuccess()) {
                    ctx.setStatus(AgentExecStatus.failed);
                    ctx.setLastError(response.getError());

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
                }

                Map<String, Object> llmPayload = new HashMap<>();
                llmPayload.put("iteration", ctx.getCurrentIteration());
                llmPayload.put("hasToolCalls", assistantMsg.hasToolCalls());
                publishEvent(AgentEventType.LLM_RESPONSE_RECEIVED, sessionId, agentName, llmPayload);

                if (!assistantMsg.hasToolCalls()) {
                    ctx.setStatus(AgentExecStatus.completed);
                    break;
                }

                SimpleToolExecuteContext toolExecCtx = new SimpleToolExecuteContext(
                        new File("."), null, null);

                List<ChatToolCall> allowedCalls = new ArrayList<>();

                for (ChatToolCall chatToolCall : assistantMsg.getToolCalls()) {
                    String toolName = chatToolCall.getName();

                    publishEvent(AgentEventType.TOOL_CALL_STARTED, sessionId, agentName,
                            Map.of("toolName", toolName,
                                    "iteration", ctx.getCurrentIteration()));

                    ToolAccessResult accessResult = toolAccessChecker.checkAccess(toolName, ctx);
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

                        String toolStatus;
                        ChatToolResponseMessage toolResponse;
                        if ("success".equals(toolResult.getStatus()) && toolResult.getError() == null) {
                            String resultText = toolResult.getOutput() != null ? toolResult.getOutput().getBody() : "";
                            toolResponse = ChatToolResponseMessage.fromToolCall(chatToolCall,
                                    resultText != null ? resultText : "");
                            toolStatus = "success";
                        } else {
                            String errorMsg = toolResult.getError() != null ? toolResult.getError().getBody() : "unknown error";
                            toolResponse = ChatToolResponseMessage.error(
                                    chatToolCall.getId(),
                                    chatToolCall.getName(),
                                    errorMsg != null ? errorMsg : "unknown error");
                            toolStatus = "error";
                        }
                        ctx.addMessage(toolResponse);

                        publishEvent(AgentEventType.TOOL_CALL_COMPLETED, sessionId, agentName,
                                Map.of("toolName", chatToolCall.getName(),
                                        "status", toolStatus));
                    }
                }

                ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            }

            if (ctx.getStatus() == AgentExecStatus.running) {
                ctx.setStatus(AgentExecStatus.completed);
            }

            Map<String, Object> completedPayload = new HashMap<>();
            completedPayload.put("totalIterations", ctx.getCurrentIteration());
            completedPayload.put("totalTokensUsed", ctx.getTokensUsed());
            completedPayload.put("durationMs", System.currentTimeMillis() - ctx.getStartTimeMs());
            publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName, completedPayload);

        } catch (Exception e) {
            ctx.setStatus(AgentExecStatus.failed);
            ctx.setLastError(e.toString());

            publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
        }

        return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
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
                publishEvent(AgentEventType.PATH_ACCESS_DENIED, sessionId, agentName,
                        Map.of("path", pathValue,
                                "reason", pathResult.getReason() != null ? pathResult.getReason() : ""));
                return pathResult.getReason() != null ? pathResult.getReason() : "path access denied";
            }
        }

        return null;
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
