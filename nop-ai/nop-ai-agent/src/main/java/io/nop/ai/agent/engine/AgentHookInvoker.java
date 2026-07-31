package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.middleware.IAgentMiddleware;
import io.nop.ai.agent.middleware.MiddlewareChain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import java.util.List;
import java.util.Map;

/**
 * Lifecycle-hook invocation and agent-event publication (extracted from
 * {@link ReActAgentExecutor}, MA4.2-05). Wraps the hook observer loop
 * ({@link #invokeHooks}) in the middleware onion chain
 * ({@link #executeWithMiddleware}) for the 9 chain-enabled lifecycle points,
 * and publishes {@link AgentEvent}s (including error events) to the wired
 * {@link IAgentEventPublisher}.
 */
public class AgentHookInvoker {
    private static final Logger LOG = LoggerFactory.getLogger(AgentHookInvoker.class);

    private final IHookRegistry hookRegistry;
    private final IAgentEventPublisher eventPublisher;

    public AgentHookInvoker(IHookRegistry hookRegistry, IAgentEventPublisher eventPublisher) {
        this.hookRegistry = hookRegistry;
        this.eventPublisher = eventPublisher;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    /**
     * Plan 296 (Workstream 1): invoke lifecycle hooks with optional middleware
     * wrapping. When middlewares are registered at {@code point}, they form an
     * onion chain whose core is {@link #invokeHooks} (the existing hook
     * observer loop). When no middlewares are registered, this delegates
     * directly to {@link #invokeHooks} — zero overhead, identical to the
     * pre-middleware path.
     *
     * <p>The 9 chain-enabled points are: PRE_CALL, PRE_REASONING,
     * POST_REASONING, PRE_ACTING, POST_ACTING, POST_CALL, PRE_COMPACT,
     * BEFORE_TOOL_RESULT_PROCESSED, AFTER_TOOL_RESULT_PROCESSED. The
     * non-chain points (ON_ERROR, REASONING_CHUNK, POST_COMPACT) continue to
     * call {@link #invokeHooks} directly.
     */
    public HookResult executeWithMiddleware(AgentLifecyclePoint point, AgentExecutionContext ctx,
                                             String agentName, String toolName, String toolCallId) {
        List<IAgentMiddleware> mws = hookRegistry.getMiddlewares(point, agentName);
        if (mws.isEmpty()) {
            return invokeHooks(point, ctx, agentName, toolName, toolCallId);
        }
        HookContext mwCtx = new HookContext(point, ctx);
        mwCtx.setToolName(toolName);
        mwCtx.setToolCallId(toolCallId);
        java.util.function.Function<HookContext, HookResult> core = hookCtx ->
                invokeHooks(point, hookCtx.getExecutionContext(), agentName,
                        hookCtx.getToolName(), hookCtx.getToolCallId());
        MiddlewareChain chain = new MiddlewareChain(mws, 0, core);
        return chain.proceed(mwCtx);
    }
    public HookResult invokeHooks(AgentLifecyclePoint point, AgentExecutionContext ctx,
                                   String agentName, String toolName, String toolCallId) {
        List<IAgentLifecycleHook> hooks = hookRegistry.getHooks(point, agentName);
        if (hooks.isEmpty()) {
            return HookResult.PassResult.instance();
        }

        for (IAgentLifecycleHook hook : hooks) {
            try {
                HookContext hookCtx = new HookContext(point, ctx);
                hookCtx.setToolName(toolName);
                hookCtx.setToolCallId(toolCallId);
                HookResult result = hook.onEvent(hookCtx);

                if (result instanceof HookResult.ReenterResult) {
                    if (point != AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED
                            && point != AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED) {
                        throw new NopAiAgentException(
                                "ReenterResult is only valid at re-entrant hook points (BEFORE_TOOL_RESULT_PROCESSED, AFTER_TOOL_RESULT_PROCESSED), got: " + point);
                    }
                    return result;
                }

                if (result.isVeto()) {
                    return result;
                }
            } catch (Exception e) {
                if (point == AgentLifecyclePoint.ON_ERROR) {
                    LOG.warn("on_error hook failed, using engine default error handling", e);
                } else if (point.name().startsWith("PRE_") || point.name().startsWith("BEFORE_")) {
                    LOG.error("before_* hook at {} failed", point, e);
                    throw e;
                } else {
                    LOG.warn("after_* hook at {} failed, continuing", point, e);
                }
            }
        }
        return HookResult.PassResult.instance();
    }
    public void invokeOnError(AgentExecutionContext ctx, String agentName) {
        try {
            invokeHooks(AgentLifecyclePoint.ON_ERROR, ctx, agentName, null, null);
        } catch (Exception e) {
            LOG.warn("ON_ERROR hook invocation failed", e);
        }
    }

    public String vetoReason(HookResult result) {
        if (result instanceof HookResult.VetoResult) {
            return ((HookResult.VetoResult) result).getReason();
        }
        return "vetoed";
    }
    public void publishEvent(AgentEventType type, String sessionId, String agentName,
                              Map<String, Object> payload) {
        if (eventPublisher != null) {
            eventPublisher.publish(AgentEvent.create(type, sessionId, agentName, payload));
        }
    }

    public void publishErrorEvent(AgentEventType type, String sessionId, String agentName,
                                   String error) {
        if (eventPublisher != null) {
            eventPublisher.publish(AgentEvent.createError(type, sessionId, agentName, error));
        }
    }
}

