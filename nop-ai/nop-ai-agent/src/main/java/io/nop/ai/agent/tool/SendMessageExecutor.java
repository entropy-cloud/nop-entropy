package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.api.core.json.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Functional send-message tool executor (fire-and-forget). Sends an async
 * message to a target session's inbox topic via {@link IAgentMessenger#send},
 * then returns immediately.
 *
 * <p><b>Honest no-delivery reporting</b>: if the messenger is a
 * {@link NoOpAgentMessenger} (no messenger configured), the tool result
 * honestly reports that the message was not delivered — it does NOT pretend
 * success.
 */
public class SendMessageExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "send-message";

    private static final Logger LOG = LoggerFactory.getLogger(SendMessageExecutor.class);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("send-message failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "send-message requires AgentToolExecuteContext (messenger not available). "
                            + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;

        IAgentMessenger messenger = agentCtx.getMessenger();
        if (messenger == null) {
            return fail(call.getId(),
                    "send-message failed: no messenger reference available in the tool execution context");
        }

        Map<String, Object> args = resolveArguments(call);

        String targetSessionId = getStringArg(args, call, "targetSessionId");
        if (targetSessionId == null || targetSessionId.isEmpty()) {
            return fail(call.getId(), "send-message failed: targetSessionId is required");
        }

        String messageBody = getStringArg(args, call, "input");
        if (messageBody == null || messageBody.isEmpty()) {
            return fail(call.getId(), "send-message failed: input (message body) is required");
        }

        String correlationId = getStringArg(args, call, "correlationId");
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        String senderId = agentCtx.getSessionId();
        if (senderId == null || senderId.isEmpty()) {
            senderId = "unknown-sender";
        }

        String targetTopic = AgentMessageTopics.inboxTopic(targetSessionId);

        if (messenger instanceof NoOpAgentMessenger) {
            LOG.debug("send-message: no messenger configured (NoOp), message not delivered: targetTopic={}",
                    targetTopic);
            AiToolCallResult result = new AiToolCallResult();
            result.setId(call.getId());
            result.setStatus("success");
            AiToolOutput output = new AiToolOutput();
            output.setBody("No messenger configured — message not delivered. "
                    + "Target topic: " + targetTopic + ", correlationId: " + correlationId);
            result.setOutput(output);
            return CompletableFuture.completedFuture(result);
        }

        AgentMessageEnvelope envelope = new AgentMessageEnvelope(
                senderId,
                targetTopic,
                correlationId,
                AgentMessageKind.ASYNC,
                messageBody);

        try {
            messenger.send(envelope);
        } catch (Exception e) {
            LOG.warn("send-message failed: messenger.send() threw", e);
            return fail(call.getId(),
                    "send-message failed: messenger.send() threw an error: " + e);
        }

        LOG.debug("send-message delivered: targetTopic={}, correlationId={}, senderId={}",
                targetTopic, correlationId, senderId);

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody("Message sent to topic " + targetTopic + " (correlationId: " + correlationId + ")");
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveArguments(AiToolCall call) {
        Map<String, Object> args = new HashMap<>();
        if (call.getInput() != null && !call.getInput().isEmpty()) {
            try {
                Object parsed = JSON.parse(call.getInput());
                if (parsed instanceof Map) {
                    args.putAll((Map<String, Object>) parsed);
                }
            } catch (Exception e) {
                LOG.debug("send-message: could not parse input as JSON, treating as plain text: input={}",
                        call.getInput(), e);
                args.put("input", call.getInput());
            }
        }
        return args;
    }

    private String getStringArg(Map<String, Object> args, AiToolCall call, String key) {
        Object val = args.get(key);
        if (val != null) {
            return val.toString();
        }
        return call.attrText(key);
    }

    private static CompletableFuture<AiToolCallResult> fail(int callId, String message) {
        return CompletableFuture.completedFuture(AiToolCallResult.errorResult(callId, message));
    }
}
