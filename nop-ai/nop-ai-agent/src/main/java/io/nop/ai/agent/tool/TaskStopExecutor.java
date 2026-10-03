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
 * The {@code task-stop} tool (plan 2301 WI25): cancels a running background
 * task by taskId. Stop prefers the progress-tracker process-kill channel
 * (design §三.6 — future cancellation may not kill a sandboxed child
 * process), falling back to future cancellation when no tracker hook is
 * installed.
 *
 * <p><b>Idempotent</b>: stopping an already-terminal task reports its status
 * without state change. <b>Fail-loud</b>: an unknown taskId or a missing
 * registry returns a descriptive error. Same-session default (read-spill's
 * safety default): stopping another session's task is refused.
 */
public class TaskStopExecutor implements IToolExecutor {

    public static final String TOOL_NAME = "task-stop";

    private static final Logger LOG = LoggerFactory.getLogger(TaskStopExecutor.class);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecute(call, context);
        } catch (Exception e) {
            LOG.debug("task-stop refused call {}: {}", call.getId(), e.toString());
            return CompletableFuture.completedFuture(
                    AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    private CompletionStage<AiToolCallResult> doExecute(AiToolCall call, IToolExecuteContext context) {
        IBackgroundTaskRegistry registry = TaskOutputExecutor.resolveRegistry(context);

        String taskId = taskIdArg(call);
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException(
                    "task-stop requires a non-empty 'taskId' argument "
                            + "(the id from the [BACKGROUND_TASK started ...] confirmation).");
        }

        IBackgroundTaskRegistry.TaskSnapshot snapshot = registry.snapshot(taskId);
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "task-stop: unknown taskId " + taskId
                            + " (never submitted, or the registry was restarted).");
        }

        String callerSession = context.getSessionId();
        if (snapshot.getSessionId() != null && callerSession != null
                && !snapshot.getSessionId().equals(callerSession)) {
            throw new IllegalArgumentException(
                    "task-stop: task " + taskId + " belongs to session "
                            + snapshot.getSessionId() + " — cross-session control is refused.");
        }

        String status = registry.stop(taskId);

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody("[BACKGROUND_TASK status=" + status + " taskId=" + taskId
                + "] Use task-output to read any output captured before the stop.");
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
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
