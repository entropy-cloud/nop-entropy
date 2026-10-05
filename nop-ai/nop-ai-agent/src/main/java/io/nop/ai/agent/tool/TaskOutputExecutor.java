package io.nop.ai.agent.tool;

import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@code task-output} tool (plan 2301 WI25): reads the status + captured
 * output tail of a background task by taskId. The idempotent read side of the
 * single result channel — the completion notification carries only the
 * taskId/status, the full tail lives here.
 *
 * <p><b>Claim scope</b>: exactly-once applies to the completion notification;
 * this read is repeatable and never mutates task state beyond the lazy
 * NOTIFICATION_FAILED redelivery attempt inside
 * {@link IBackgroundTaskRegistry#snapshot}.
 *
 * <p><b>Fail-loud</b> (Minimum Rules #24): an unknown taskId, a missing
 * registry, or an empty taskId argument returns a descriptive error — never a
 * silent empty result. Same-session default: a task submitted by another
 * session is refused (read-spill's safety default).
 */
public class TaskOutputExecutor implements IToolExecutor {

    public static final String TOOL_NAME = "task-output";

    private static final Logger LOG = LoggerFactory.getLogger(TaskOutputExecutor.class);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecute(call, context);
        } catch (Exception e) {
            LOG.debug("task-output refused call {}: {}", call.getId(), e.toString());
            return CompletableFuture.completedFuture(
                    AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    private CompletionStage<AiToolCallResult> doExecute(AiToolCall call, IToolExecuteContext context) {
        IBackgroundTaskRegistry registry = resolveRegistry(context);

        String taskId = taskIdArg(call);
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException(
                    "task-output requires a non-empty 'taskId' argument "
                            + "(the id from the [BACKGROUND_TASK started ...] confirmation).");
        }

        IBackgroundTaskRegistry.TaskSnapshot snapshot = registry.snapshot(taskId);
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "task-output: unknown taskId " + taskId
                            + " (never submitted, or the registry was restarted).");
        }

        String callerSession = context.getSessionId();
        if (snapshot.getSessionId() != null && callerSession != null
                && !snapshot.getSessionId().equals(callerSession)) {
            throw new IllegalArgumentException(
                    "task-output: task " + taskId + " belongs to session "
                            + snapshot.getSessionId() + " — cross-session reads are refused.");
        }

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(render(snapshot));
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }

    static String render(IBackgroundTaskRegistry.TaskSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("[BACKGROUND_TASK status=").append(snapshot.getStatus())
                .append(" taskId=").append(snapshot.getTaskId())
                .append(" tool=").append(snapshot.getToolName())
                .append(" bytes=").append(snapshot.getTotalBytes())
                .append(" notificationSent=").append(snapshot.isNotificationSent())
                .append("]\n");
        if (snapshot.getOutputTail() == null || snapshot.getOutputTail().isEmpty()) {
            sb.append("(no output captured yet)");
        } else {
            sb.append(snapshot.getOutputTail());
        }
        return sb.toString();
    }

    /** Shared seam resolution: UOE/missing registry becomes a loud failure. */
    static IBackgroundTaskRegistry resolveRegistry(IToolExecuteContext context) {
        try {
            IBackgroundTaskRegistry registry = context.getBackgroundTaskRegistry();
            if (registry == null) {
                throw new IllegalStateException(
                        "Background task registry is not wired in this engine.");
            }
            return registry;
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException(
                    "The task tools require the agent engine context (no background task registry on "
                            + context.getClass().getSimpleName() + ").", e);
        }
    }

    /**
     * Plan 2301 (WI25): dual-source taskId read — the engine dispatch path
     * provides JSON arguments in call.getInput() with a null node
     * (CallAgentExecutor precedent); the XML DSL path provides attributes.
     */
    static String taskIdArg(AiToolCall call) {
        String input = call.getInput();
        if (input != null && !input.isEmpty()) {
            try {
                Object parsed = io.nop.core.lang.json.JsonTool.parseNonStrict(input);
                if (parsed instanceof Map<?, ?> map) {
                    Object value = map.get("taskId");
                    if (value != null && !String.valueOf(value).isEmpty()) {
                        return String.valueOf(value);
                    }
                }
            } catch (Exception e) {
                // malformed input falls through to the node view
            }
        }
        return call.attrText("taskId");
    }
}
