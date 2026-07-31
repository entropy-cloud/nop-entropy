package io.nop.ai.agent.engine;

import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.CallAgentRequestPayload;
import io.nop.ai.agent.message.CallAgentResponsePayload;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.api.core.message.IMessageSubscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * call-agent message delegation for the engine (extracted from
 * {@link DefaultAgentEngine}, MA4.2-05). Registers the engine-level
 * call-agent request handler (idempotent), processes request envelopes by
 * delegating to the engine's execute/cancelSession entry points, and
 * extracts the final assistant message from the result.
 */
public class AgentCallDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);
    private final DefaultAgentEngine engine;

    private IAgentMessenger messenger = NoOpAgentMessenger.noOp();
    private volatile IMessageSubscription callAgentSubscription;

    public AgentCallDelegate(DefaultAgentEngine engine) {
        this.engine = engine;
    }

    public void setMessenger(IAgentMessenger messenger) {
        IAgentMessenger resolved = messenger != null ? messenger : NoOpAgentMessenger.noOp();
        this.messenger = resolved;
        registerCallAgentHandler(resolved);
    }

    // ---- moved verbatim from DefaultAgentEngine (MA4.2-05 split) ----
    private void registerCallAgentHandler(IAgentMessenger messenger) {
        IMessageSubscription existing = this.callAgentSubscription;
        if (existing != null) {
            try {
                existing.cancel();
            } catch (Exception e) {
                LOG.warn("DefaultAgentEngine: failed to cancel existing call-agent subscription", e);
            }
            this.callAgentSubscription = null;
        }
        if (messenger instanceof NoOpAgentMessenger) {
            return;
        }
        String topic = AgentMessageTopics.callAgentTopic();
        IMessageSubscription subscription = messenger.registerHandler(topic, this::handleCallAgentRequest);
        this.callAgentSubscription = subscription;
        LOG.debug("DefaultAgentEngine: registered call-agent request handler on topic={}", topic);
    }
    private Object handleCallAgentRequest(AgentMessageEnvelope envelope) {
        Object payload = envelope.getPayload();
        if (!(payload instanceof CallAgentRequestPayload)) {
            LOG.warn("nop.ai.agent.call-agent.handler.unexpected-payload: payloadClass={}",
                    payload == null ? "null" : payload.getClass().getName());
            return new CallAgentResponsePayload(
                    "failure", null, "",
                    "call-agent handler: unexpected payload type: "
                            + (payload == null ? "null" : payload.getClass().getName()));
        }
        CallAgentRequestPayload req = (CallAgentRequestPayload) payload;
        // that on timeout the handler can call cancelSession to release the
        // sub-agent's LLM/DB resources (not just abandon the Future). When
        // req.getResolvedSessionId() is null (create-new mode), generate a
        // UUID — engine.execute would generate one anyway via resolveSessionId,
        // so this is behavior-preserving.
        String childSessionId = req.getResolvedSessionId();
        if (childSessionId == null || childSessionId.isEmpty()) {
            childSessionId = UUID.randomUUID().toString();
        }
        try {
            AgentMessageRequest execRequest = new AgentMessageRequest(
                    req.getTargetAgentId(),
                    req.getInput(),
                    childSessionId,
                    req.getParentConstraintMetadata());
            CompletableFuture<AgentExecutionResult> future = engine.execute(execRequest);
            AgentExecutionResult result = future.orTimeout(req.getTimeoutMs(), TimeUnit.MILLISECONDS).join();
            String finalMessage = extractFinalAssistantMessage(result);
            String sessionId = result.getSessionId() != null
                    ? result.getSessionId()
                    : childSessionId;
            boolean success = result.getStatus() == AgentExecStatus.completed;
            String status = success ? "success" : "failure";
            String error = success ? null
                    : (result.getError() != null ? result.getError()
                            : "sub-agent did not complete: status=" + result.getStatus());
            return new CallAgentResponsePayload(status, sessionId, finalMessage, error);
        } catch (Exception e) {
            // execution does not continue as a zombie consuming LLM/DB
            // resources. .orTimeout above completes the Future exceptionally
            // with a TimeoutException (wrapped in CompletionException by join);
            // without this cancel the underlying engine.execute Future keeps
            // running. cancelSession failures are logged but never mask the
            // original timeout error.
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            if (cause instanceof java.util.concurrent.TimeoutException) {
                try {
                    engine.cancelSession(childSessionId,
                            "call-agent handler timeout after " + req.getTimeoutMs() + "ms", true);
                } catch (RuntimeException cancelEx) {
                    LOG.warn("nop.ai.agent.call-agent.handler.cancel-failed: childSessionId={}",
                            childSessionId, cancelEx);
                }
            }
            LOG.warn("nop.ai.agent.call-agent.handler.execution-failed: targetAgentId={}, correlationId={}",
                    req.getTargetAgentId(), envelope.getCorrelationId(), e);
            return new CallAgentResponsePayload(
                    "failure", childSessionId, "",
                    "call-agent handler execution failed: agentId=" + req.getTargetAgentId()
                            + ", error=" + e);
        }
    }
    static String extractFinalAssistantMessage(AgentExecutionResult result) {
        List<ChatMessage> messages = result.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ChatAssistantMessage) {
                String content = msg.getContent();
                return content != null ? content : "";
            }
        }
        return "";
    }
    public IAgentMessenger getMessenger() {
        return messenger;
    }
}

