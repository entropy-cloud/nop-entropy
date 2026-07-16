package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.api.core.json.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Plan 296 (WS2): the {@code set-active-tags} meta-tool. Replaces the current
 * session's active tool-visibility tags so the next ReAct iteration exposes
 * a different tool subset to the LLM.
 *
 * <p><b>Honest no-config reporting</b> (Minimum Rules #24): when the
 * {@link AgentSession} is not available in the context (executor constructed
 * outside the engine, or no sessionStore wired), the tool returns a
 * descriptive error rather than silently succeeding.
 *
 * <p>This tool is declared with {@code meta="true"} in its
 * {@code set-active-tags.tool.xml}, so it is always visible in the LLM tool
 * list regardless of the current activeTags selection.
 */
public class SetActiveTagsExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "set-active-tags";

    private static final Logger LOG = LoggerFactory.getLogger(SetActiveTagsExecutor.class);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("set-active-tags failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "set-active-tags requires AgentToolExecuteContext (session not available). "
                            + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;
        AgentSession session = agentCtx.getSession();
        if (session == null) {
            return fail(call.getId(),
                    "set-active-tags failed: no AgentSession available in the context "
                            + "(sessionStore may not be wired).");
        }

        Set<String> newTags = new HashSet<>();
        String input = call.getInput();
        if (input != null && !input.isEmpty()) {
            try {
                Object parsed = JSON.parse(input);
                if (parsed instanceof Map) {
                    Object tagsRaw = ((Map<String, Object>) parsed).get("tags");
                    collectTags(tagsRaw, newTags);
                }
            } catch (Exception e) {
                LOG.debug("set-active-tags: could not parse input as JSON: input={}", input, e);
            }
        }

        session.setActiveTags(newTags);

        LOG.debug("set-active-tags: session={} activeTags={}", session.getSessionId(), newTags);

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        if (newTags.isEmpty()) {
            output.setBody("Active tags cleared. All tools are now visible (no tag filtering).");
        } else {
            output.setBody("Active tags set to: " + newTags
                    + ". Only tools matching these tags (plus meta tools) will be visible in the next iteration.");
        }
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }

    @SuppressWarnings("unchecked")
    private static void collectTags(Object tagsRaw, Set<String> target) {
        if (tagsRaw instanceof List) {
            for (Object tag : (List<Object>) tagsRaw) {
                if (tag != null) {
                    String s = tag.toString().trim();
                    if (!s.isEmpty()) {
                        target.add(s);
                    }
                }
            }
        } else if (tagsRaw instanceof String) {
            String s = ((String) tagsRaw).trim();
            if (!s.isEmpty()) {
                target.add(s);
            }
        }
    }

    private static CompletableFuture<AiToolCallResult> fail(int callId, String message) {
        return CompletableFuture.completedFuture(AiToolCallResult.errorResult(callId, message));
    }
}
