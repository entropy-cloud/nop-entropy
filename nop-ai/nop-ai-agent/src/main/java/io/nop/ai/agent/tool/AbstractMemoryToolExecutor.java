package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.json.JSON;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Shared argument-resolution and store-resolution scaffolding for the three
 * working-memory tool executors ({@link ReadMemoryExecutor},
 * {@link WriteMemoryExecutor}, {@link SearchMemoryExecutor}).
 *
 * <p>All three executors follow the same pattern established by
 * {@link CallAgentExecutor} / {@link SendMessageExecutor}: JSON input parsing
 * first, attr fallback second, descriptive error result on any failure
 * (never throw uncaught), and fail-fast when the store is absent (honest
 * no-config reporting).
 */
abstract class AbstractMemoryToolExecutor implements IToolExecutor {

    protected abstract Logger log();

    /**
     * Resolve the per-session memory store from the agent execution context.
     * Returns a descriptive error result when the context is not agent-scoped
     * or the store is absent (e.g. the executor was constructed outside the
     * engine for testing, or the engine's provider was explicitly null).
     */
    protected StoreResolution resolveStore(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return StoreResolution.failure(call.getId(), getToolName()
                    + " requires AgentToolExecuteContext (memory store not available). "
                    + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;
        IAiMemoryStore store = agentCtx.getMemoryStore();
        if (store == null) {
            return StoreResolution.failure(call.getId(),
                    getToolName() + " failed: no memory store available in the tool execution context. "
                            + "Ensure the ReAct executor is wired with a memory store provider "
                            + "(DefaultAgentEngine wires an InMemoryMemoryStoreProvider by default).");
        }
        return StoreResolution.ok(agentCtx, store);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> resolveArguments(AiToolCall call) {
        Map<String, Object> args = new HashMap<>();
        if (call.getInput() != null && !call.getInput().isEmpty()) {
            try {
                Object parsed = JSON.parse(call.getInput());
                if (parsed instanceof Map) {
                    args.putAll((Map<String, Object>) parsed);
                }
            } catch (Exception e) {
                log().debug("{}: could not parse input as JSON, treating as plain text: input={}",
                        getToolName(), call.getInput(), e);
                args.put("input", call.getInput());
            }
        }
        return args;
    }

    protected String getStringArg(Map<String, Object> args, AiToolCall call, String key) {
        Object val = args.get(key);
        if (val != null) {
            return val.toString();
        }
        return call.attrText(key);
    }

    protected String getStringArg(Map<String, Object> args, AiToolCall call, String key, String defaultValue) {
        String val = getStringArg(args, call, key);
        return val != null ? val : defaultValue;
    }

    protected int getIntArg(Map<String, Object> args, AiToolCall call, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        Integer nodeVal = call.attrInt(key);
        return nodeVal != null ? nodeVal : defaultValue;
    }

    protected boolean getBooleanArg(Map<String, Object> args, AiToolCall call, String key, boolean defaultValue) {
        Object val = args.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof String) {
            return Boolean.parseBoolean((String) val);
        }
        Boolean nodeVal = call.attrBoolean(key);
        return nodeVal != null ? nodeVal : defaultValue;
    }

    protected static CompletableFuture<AiToolCallResult> fail(int callId, String message) {
        return CompletableFuture.completedFuture(AiToolCallResult.errorResult(callId, message));
    }

    /**
     * Result of store resolution: either a success with the resolved store
     * (and the originating context), or a failure carrying a pre-built error
     * result to return immediately.
     */
    protected static final class StoreResolution {
        final boolean ok;
        final AgentToolExecuteContext agentCtx;
        final IAiMemoryStore store;
        final CompletionStage<AiToolCallResult> failureResult;

        private StoreResolution(boolean ok, AgentToolExecuteContext agentCtx, IAiMemoryStore store,
                                CompletionStage<AiToolCallResult> failureResult) {
            this.ok = ok;
            this.agentCtx = agentCtx;
            this.store = store;
            this.failureResult = failureResult;
        }

        static StoreResolution ok(AgentToolExecuteContext ctx, IAiMemoryStore store) {
            return new StoreResolution(true, ctx, store, null);
        }

        static StoreResolution failure(int callId, String message) {
            return new StoreResolution(false, null, null, fail(callId, message));
        }
    }
}
