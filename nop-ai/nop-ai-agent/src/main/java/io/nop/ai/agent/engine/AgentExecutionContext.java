package io.nop.ai.agent.engine;

import io.nop.ai.agent.budget.BudgetSnapshot;
import io.nop.ai.agent.model.AgentConstraintsModel;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.core.model.ChatOptionsModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AgentExecutionContext {

    private final AgentModel agentModel;
    private final List<ChatMessage> messages;
    private AgentPlan plan;
    private String sessionId;
    private ChatOptionsModel chatOptionsModel;
    private int currentIteration;
    private long tokensUsed;
    private AgentExecStatus status;
    private int maxIterations;
    private Map<String, Object> metadata;
    private String lastError;
    private long startTimeMs;
    private volatile boolean cancelRequested;
    private volatile String cancelReason;
    // Plan 273 (carry-over 14-06, Phase 2): set by the heartbeat renewal
    // task when tryRenew returns false (takeover lease lost / preempted by
    // another JVM instance). The engine's execution cleanup path reads this
    // to force the terminal session status to {@code failed} (the ReAct
    // executor's cancel path would otherwise set {@code cancelled} —
    // lease-lost is a system-level double-execution-prevention failure, not
    // a user-initiated cancel). Volatile so the renewal-scheduler thread's
    // write is visible to the execution thread's cleanup finally.
    private volatile boolean leaseLost;
    private ChannelKind channelKind;
    private Principal principal;
    // Plan 206 (L2-22): per-iteration budget snapshot refreshed by the ReAct
    // loop before each IModelRouter.route() call. A functional router reads
    // this to decide whether to downgrade the model on budget exhaustion.
    // Nullable (null before the first refresh); the shipped NoOpBudgetProvider
    // default makes this a non-null unlimited snapshot after the first refresh.
    private BudgetSnapshot budgetSnapshot;

    // Plan 278 (AR-05): delegation depth of this execution within a
    // call-agent chain. 0 = top-level agent (no parent), 1 = first-level
    // sub-agent, etc. Extracted from AgentMessageRequest.metadata by
    // DefaultAgentEngine.doExecute and propagated to AgentToolExecuteContext
    // so CallAgentExecutor can enforce MAX_DELEGATION_DEPTH and pass the
    // child's depth (parent + 1) onward. Independent of the tool/path
    // permission constraint — it is always propagated via a dedicated
    // metadata key, even when no ParentPermissionConstraint is present.
    private int delegationDepth;

    // Plan 220 (L4-8-steering): thread-safe steering message queue. External
    // messages (injected via the Actor mailbox consumption loop) are enqueued
    // here by the Actor's consumption thread and drained by the ReAct loop at
    // the round boundary (after all tools in a round complete, before the next
    // LLM call). ConcurrentLinkedQueue provides lock-free multi-thread
    // coordination: the Actor thread writes (enqueueSteering) and the ReAct
    // thread reads (drainSteering) without explicit synchronization. A
    // per-execution instance (created with the context, garbage-collected when
    // the context goes away). With the shipped NoOpActorRuntime default no
    // Actor consumption loop runs, so this queue is always empty and the ReAct
    // loop's drain is a no-op (one isEmpty/poll check).
    private final Queue<ChatMessage> steeringQueue = new ConcurrentLinkedQueue<>();

    public AgentExecutionContext(AgentModel agentModel) {
        this.agentModel = agentModel;
        this.messages = new ArrayList<>();
        this.status = AgentExecStatus.pending;
        this.maxIterations = 10;
        this.currentIteration = 0;
        this.tokensUsed = 0;
        this.metadata = new HashMap<>();
        this.startTimeMs = System.currentTimeMillis();
    }

    public static AgentExecutionContext create(AgentModel agentModel, String sessionId) {
        AgentExecutionContext ctx = new AgentExecutionContext(agentModel);

        if (sessionId != null) {
            ctx.setSessionId(sessionId);
        }

        if (agentModel != null) {
            AgentConstraintsModel constraints = agentModel.getConstraints();
            if (constraints != null && constraints.getMaxIterations() != null) {
                ctx.setMaxIterations(constraints.getMaxIterations());
            }

            ChatOptionsModel chatOptions = agentModel.getChatOptions();
            if (chatOptions != null) {
                ctx.setChatOptionsModel(chatOptions);
            }
        }

        return ctx;
    }

    public AgentModel getAgentModel() {
        return agentModel;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
    }

    public AgentPlan getPlan() {
        return plan;
    }

    public void setPlan(AgentPlan plan) {
        this.plan = plan;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ChatOptionsModel getChatOptionsModel() {
        return chatOptionsModel;
    }

    public void setChatOptionsModel(ChatOptionsModel chatOptionsModel) {
        this.chatOptionsModel = chatOptionsModel;
    }

    public int getCurrentIteration() {
        return currentIteration;
    }

    public void setCurrentIteration(int currentIteration) {
        this.currentIteration = currentIteration;
    }

    public long getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(long tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public AgentExecStatus getStatus() {
        return status;
    }

    public void setStatus(AgentExecStatus status) {
        this.status = status;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    /**
     * Plan 273 (carry-over 14-06, Phase 2): whether the takeover lease was
     * lost mid-execution (the heartbeat renewal task's tryRenew returned
     * false). The engine's cleanup path forces terminal status
     * {@code failed} when this is true.
     */
    public boolean isLeaseLost() {
        return leaseLost;
    }

    public void setLeaseLost(boolean leaseLost) {
        this.leaseLost = leaseLost;
    }

    public ChannelKind getChannelKind() {
        return channelKind;
    }

    public void setChannelKind(ChannelKind channelKind) {
        this.channelKind = channelKind;
    }

    public Principal getPrincipal() {
        return principal;
    }

    public void setPrincipal(Principal principal) {
        this.principal = principal;
    }

    /**
     * Plan 206 (L2-22): the current session-level budget snapshot. Refreshed by
     * the ReAct loop before each {@code IModelRouter.route()} call. Nullable
     * (null before the first refresh); after the first refresh it is non-null
     * (the shipped {@code NoOpBudgetProvider} returns an unlimited snapshot).
     *
     * @return the current budget snapshot, or {@code null} before the first
     *         refresh
     */
    public BudgetSnapshot getBudgetSnapshot() {
        return budgetSnapshot;
    }

    public void setBudgetSnapshot(BudgetSnapshot budgetSnapshot) {
        this.budgetSnapshot = budgetSnapshot;
    }

    /**
     * Plan 220 (L4-8-steering): the thread-safe steering message queue shared
     * with the Actor mailbox consumption loop. The Actor holds a reference to
     * this exact queue object (bound via {@code AgentActor.setSteeringQueue})
     * and enqueues polled mailbox messages (converted to {@link ChatMessage})
     * from its dedicated consumption thread. The ReAct loop drains it at the
     * round boundary from the ReAct thread. The returned queue is the live
     * instance; callers must not retain it beyond the execution lifetime.
     *
     * @return the live steering queue (never null)
     */
    public Queue<ChatMessage> getSteeringQueue() {
        return steeringQueue;
    }

    /**
     * Plan 220 (L4-8-steering): enqueue a steering message into the steering
     * queue. Called from the Actor's consumption thread (after converting the
     * polled mailbox envelope payload to a {@link ChatMessage}). Safe to call
     * concurrently with {@link #drainSteering()} (the underlying queue is a
     * {@link ConcurrentLinkedQueue}).
     *
     * @param message the steering message to enqueue (must not be null)
     */
    public void enqueueSteering(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("enqueueSteering: message must not be null");
        }
        steeringQueue.add(message);
    }

    /**
     * Plan 220 (L4-8-steering): drain all currently-queued steering messages
     * and return them as a list (in enqueue order). Called from the ReAct
     * thread at the round boundary (after all tools in a round complete,
     * before the next LLM call). After this call the steering queue is empty.
     * Returns an empty list when no steering messages are queued (the shipped
     * NoOpActorRuntime default — no consumption loop running — makes this the
     * always-path, so the drain is a cheap no-op: a single poll loop that
     * immediately finds null).
     *
     * @return the drained steering messages (never null, empty when the queue
     *         was empty)
     */
    public List<ChatMessage> drainSteering() {
        List<ChatMessage> drained = new ArrayList<>();
        ChatMessage msg;
        while ((msg = steeringQueue.poll()) != null) {
            drained.add(msg);
        }
        return drained;
    }

    /**
     * Plan 278 (AR-05): the delegation depth of this execution within a
     * call-agent chain. 0 = top-level agent. See {@link #delegationDepth}.
     */
    public int getDelegationDepth() {
        return delegationDepth;
    }

    public void setDelegationDepth(int delegationDepth) {
        this.delegationDepth = delegationDepth;
    }
}
