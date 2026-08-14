package io.nop.ai.agent.tool;

import io.nop.ai.agent.compact.ISpillStore;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@code read-spill} tool (design §3.3): reads back the full text of an
 * oversized tool result that was spilled into the session's
 * {@link ISpillStore} by {@code AgentToolDispatcher}. The spill id comes from
 * the {@code [SPILL_REF id=...]} marker left in the inline preview.
 * <p>
 * The read side resolves the <b>same</b> store instance as the write side via
 * {@link AgentSession#getOrCreateSpillStore()} through
 * {@link AgentToolExecuteContext#getSession()} — no instance drift between
 * write and read (design §3.3 adjudication).
 * <p>
 * <b>Fail-loud</b> (Minimum Rules #24): an unknown/expired spill id, or a
 * context without an {@link AgentSession}, returns a descriptive error — never
 * a silent empty result. The returned content is a normal tool result and
 * flows through the regular tool-result path (it may itself be spilled or
 * truncated again; {@code SPILL_REF} nesting is allowed).
 */
public class ReadSpillExecutor implements IToolExecutor {
    /**
     * Tool name exposed to the LLM; matches the
     * {@code read-spill.tool.xml} declaration.
     */
    public static final String TOOL_NAME = "read-spill";

    /** Logger for this executor. */
    private static final Logger LOG = LoggerFactory.getLogger(ReadSpillExecutor.class);

    /**
     * {@inheritDoc} — returns the tool name {@value #TOOL_NAME}. Not intended
     * for extension; safe to override with the same name only.
     */
    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    /**
     * {@inheritDoc} — delegates to the fail-loud implementation; any
     * unexpected exception is reported as an error result, never thrown.
     */
    @Override
    public CompletionStage<AiToolCallResult> executeAsync(
            final AiToolCall call, final IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("read-spill failed unexpectedly", e);
            return CompletableFuture.completedFuture(
                    AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    private CompletionStage<AiToolCallResult> doExecuteAsync(
            final AiToolCall call, final IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "read-spill requires AgentToolExecuteContext "
                            + "(session not available). The tool must be "
                            + "invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;
        AgentSession session = agentCtx.getSession();
        if (session == null) {
            return fail(call.getId(),
                    "read-spill failed: no AgentSession available in the "
                            + "context (sessionStore may not be wired).");
        }

        String input = call.getInput();
        String spillId = null;
        if (input != null && !input.isEmpty()) {
            try {
                Object parsed = JSON.parse(input);
                if (parsed instanceof Map) {
                    Object idRaw = ((Map<String, Object>) parsed).get("id");
                    if (idRaw != null) {
                        spillId = idRaw.toString();
                    }
                }
            } catch (Exception e) {
                LOG.debug("read-spill: could not parse input as JSON: "
                        + "input={}, err={}", input, e.toString());
            }
        }
        if (spillId == null || spillId.isEmpty()) {
            return fail(call.getId(),
                    "read-spill requires a non-empty 'id' argument "
                            + "(the SPILL_REF id from the preview marker).");
        }

        ISpillStore store = session.getOrCreateSpillStore();
        String content = store.get(spillId);
        if (content == null) {
            return fail(call.getId(),
                    "read-spill: spill id not found (expired or never "
                            + "spilled): " + spillId);
        }

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(content);
        result.setOutput(output);
        LOG.debug("read-spill: session={} spillId={} bytes={}",
                session.getSessionId(), spillId, content.length());
        return CompletableFuture.completedFuture(result);
    }

    private static CompletableFuture<AiToolCallResult> fail(
            final int callId, final String message) {
        return CompletableFuture.completedFuture(
                AiToolCallResult.errorResult(callId, message));
    }
}
