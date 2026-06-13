package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.NoOpHookRegistry;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DefaultAgentEngine implements IAgentEngine {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);

    private final IChatService chatService;
    private final IToolManager toolManager;
    private final DefaultAgentEventPublisher eventPublisher;
    private final ISessionStore sessionStore;
    private final IPermissionProvider permissionProvider;
    private final IToolAccessChecker toolAccessChecker;
    private final IPathAccessChecker pathAccessChecker;

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager) {
        this(chatService, toolManager, new InMemorySessionStore());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                              ISessionStore sessionStore) {
        this(chatService, toolManager, sessionStore, new AllowAllPermissionProvider());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                              ISessionStore sessionStore, IPermissionProvider permissionProvider) {
        this(chatService, toolManager, sessionStore, permissionProvider, new AllowAllToolAccessChecker());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                              ISessionStore sessionStore, IPermissionProvider permissionProvider,
                              IToolAccessChecker toolAccessChecker) {
        this(chatService, toolManager, sessionStore, permissionProvider,
                toolAccessChecker, new AllowAllPathAccessChecker());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                              ISessionStore sessionStore, IPermissionProvider permissionProvider,
                              IToolAccessChecker toolAccessChecker, IPathAccessChecker pathAccessChecker) {
        this.chatService = chatService;
        this.toolManager = toolManager;
        this.eventPublisher = new DefaultAgentEventPublisher();
        this.sessionStore = sessionStore;
        this.permissionProvider = permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider();
        this.toolAccessChecker = toolAccessChecker != null ? toolAccessChecker : new AllowAllToolAccessChecker();
        this.pathAccessChecker = pathAccessChecker != null ? pathAccessChecker : new AllowAllPathAccessChecker();
    }

    public IAgentEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    @Override
    public AgentMessageAck sendMessage(AgentMessageRequest request) {
        String sessionId = resolveSessionId(request.getSessionId());
        CompletableFuture<AgentExecutionResult> future = doExecute(request, sessionId);
        future.exceptionally(ex -> {
            LOG.error("Agent execution failed for agentName={}, sessionId={}: {}",
                    request.getAgentName(), sessionId, ex.getMessage(), ex);
            return null;
        });
        return new AgentMessageAck(sessionId);
    }

    @Override
    public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
        String sessionId = resolveSessionId(request.getSessionId());
        return doExecute(request, sessionId);
    }

    private CompletableFuture<AgentExecutionResult> doExecute(AgentMessageRequest request, String sessionId) {
        AgentModel agentModel = loadAgentModel(request.getAgentName());

        AgentSession session = sessionStore.getOrCreate(sessionId, request.getAgentName());
        int historyCount = session.getMessageCount();

        if (historyCount == 0) {
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_CREATED,
                    sessionId, request.getAgentName(), null));
        } else {
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_LOADED,
                    sessionId, request.getAgentName(),
                    java.util.Map.of("historyCount", historyCount)));
        }

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, sessionId);

        if (request.getMetadata() != null) {
            ctx.getMetadata().putAll(request.getMetadata());
        }

        String systemPrompt = null;
        if (agentModel.getPrompt() != null) {
            systemPrompt = agentModel.getPrompt().getSource();
        }

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ctx.addMessage(new ChatSystemMessage(systemPrompt));
        }

        if (historyCount > 0) {
            ctx.getMessages().addAll(session.getMessages());
        }

        ctx.addMessage(new ChatUserMessage(request.getUserMessage()));

        IAgentExecutor executor = resolveExecutor(agentModel);

        return CompletableFuture.supplyAsync(() -> {
            AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

            List<ChatMessage> allMessages = ctx.getMessages();
            int currentCount = allMessages.size();
            if (currentCount > historyCount) {
                List<ChatMessage> newMessages = new ArrayList<>(allMessages.subList(historyCount, currentCount));
                session.appendMessages(newMessages);
            }

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();

            return result;
        });
    }

    IAgentExecutor resolveExecutor(AgentModel model) {
        String mode = model.getMode();
        if (mode == null || mode.isEmpty() || "react".equals(mode)) {
            DefaultHookRegistry hookRegistry = DefaultHookRegistry.fromAgentModel(model);
            return ReActAgentExecutor.builder()
                    .chatService(chatService)
                    .toolManager(toolManager)
                    .eventPublisher(eventPublisher)
                    .permissionProvider(permissionProvider)
                    .toolAccessChecker(toolAccessChecker)
                    .pathAccessChecker(pathAccessChecker)
                    .hookRegistry(hookRegistry)
                    .build();
        }
        if ("single-turn".equals(mode)) {
            return new SingleTurnExecutor(chatService, eventPublisher);
        }
        if ("plan".equals(mode)) {
            throw new UnsupportedOperationException("Plan execution mode is not yet implemented: mode=plan");
        }
        throw new NopAiAgentException("Unknown agent execution mode: " + mode);
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }
        return UUID.randomUUID().toString();
    }

    private AgentModel loadAgentModel(String agentName) {
        String path = "/" + agentName + ".agent.xml";
        try {
            Object obj = ResourceComponentManager.instance().loadComponentModel(path);
            if (!(obj instanceof AgentModel)) {
                throw new NopAiAgentException("Failed to load agent model from " + path
                        + ": unexpected type " + obj.getClass().getName());
            }
            return (AgentModel) obj;
        } catch (NopAiAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new NopAiAgentException("Failed to load agent model: agentName=" + agentName, e);
        }
    }
}
