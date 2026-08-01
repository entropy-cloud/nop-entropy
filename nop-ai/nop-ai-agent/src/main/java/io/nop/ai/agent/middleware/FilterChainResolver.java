package io.nop.ai.agent.middleware;

import io.nop.ai.agent.NopAiAgentErrors;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentFilterChainModel;
import io.nop.ai.agent.model.FilterDefModel;
import io.nop.ai.agent.model.FilterRefModel;
import io.nop.commons.util.ClassHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * W3-2 (declarative filter chain): resolves an agent's declarative
 * {@code <filter-chain>} into a {@link ResolvedFilterChain}.
 *
 * <p><b>D1 (option B — agent-internal id->impl mapping)</b>: filter IDs are
 * resolved against the agent's own {@code <filter-definitions>} block. No IoC
 * container lookup is performed, so {@code AgentExecutorResolver} needs no
 * container injection. The impl class is instantiated via
 * {@link ClassHelper#safeNewInstance}, the same path used by the existing
 * code-class {@code <middlewares>} assembly.
 *
 * <p><b>D2 (input/output → lifecycle point mapping)</b>:
 * <ul>
 *   <li>input-filter with no {@code points} → {@link AgentLifecyclePoint#PRE_CALL}
 *       (request boundary, single trigger per request).</li>
 *   <li>output-filter with no {@code points} → {@link AgentLifecyclePoint#POST_CALL}
 *       (response boundary, single trigger per request).</li>
 *   <li>{@code points="pre_reasoning,post_acting"} → the filter is registered
 *       at each named lifecycle point instead of the default.</li>
 * </ul>
 *
 * <p><b>Fast-fail (no silent skip)</b>: unknown filter ID, missing impl class,
 * impl that does not implement {@link IAgentMiddleware}, instantiation error,
 * and unknown {@code points} name all throw {@link NopAiAgentException} with the
 * offending filter ID / point name in the message.
 *
 * <p>The resolved middleware instances are cached per filter ID, so a filter
 * referenced from both input and output chains is instantiated once and shared
 * (identity-equal), which makes the D3 duplicate-detection against
 * {@code <middlewares>} unambiguous.
 */
public final class FilterChainResolver {

    private FilterChainResolver() {
    }

    public static ResolvedFilterChain resolve(AgentFilterChainModel chain) {
        if (chain == null) {
            return new ResolvedFilterChain(null, null, Collections.emptyMap());
        }
        Map<String, FilterDefModel> defs = indexFilterDefinitions(chain.getFilterDefinitions());

        // Cache resolved middleware by filter id: a filter referenced from both
        // input and output is instantiated once and identity-shared.
        Map<String, IAgentMiddleware> instanceCache = new HashMap<>();

        Map<AgentLifecyclePoint, List<IAgentMiddleware>> byPoint = new LinkedHashMap<>();
        List<FilterRefModel> inputRefs = chain.getInputFilters();
        List<FilterRefModel> outputRefs = chain.getOutputFilters();
        if (inputRefs != null) {
            for (FilterRefModel ref : inputRefs) {
                IAgentMiddleware mw = resolveRef(ref, defs, instanceCache);
                for (AgentLifecyclePoint point : resolvePoints(ref, AgentLifecyclePoint.PRE_CALL)) {
                    byPoint.computeIfAbsent(point, k -> new ArrayList<>()).add(mw);
                }
            }
        }
        if (outputRefs != null) {
            for (FilterRefModel ref : outputRefs) {
                IAgentMiddleware mw = resolveRef(ref, defs, instanceCache);
                for (AgentLifecyclePoint point : resolvePoints(ref, AgentLifecyclePoint.POST_CALL)) {
                    byPoint.computeIfAbsent(point, k -> new ArrayList<>()).add(mw);
                }
            }
        }
        return new ResolvedFilterChain(inputRefs, outputRefs, byPoint);
    }

    private static Map<String, FilterDefModel> indexFilterDefinitions(List<FilterDefModel> defs) {
        Map<String, FilterDefModel> map = new HashMap<>();
        if (defs == null || defs.isEmpty()) {
            return map;
        }
        for (FilterDefModel def : defs) {
            String id = def.getId();
            if (id == null || id.isEmpty()) {
                throw new NopAiAgentException(
                        "filter-def with no id is not allowed in <filter-definitions>");
            }
            if (map.put(id, def) != null) {
                throw new NopAiAgentException(
                        "duplicate filter-def id '" + id + "' in <filter-definitions>");
            }
        }
        return map;
    }

    private static IAgentMiddleware resolveRef(FilterRefModel ref,
                                               Map<String, FilterDefModel> defs,
                                               Map<String, IAgentMiddleware> instanceCache) {
        String id = ref.getRef();
        if (id == null || id.isEmpty()) {
            throw new NopAiAgentException(
                    "<filter> in <filter-chain> has no ref attribute (filter id required)");
        }
        IAgentMiddleware cached = instanceCache.get(id);
        if (cached != null) {
            return cached;
        }
        FilterDefModel def = defs.get(id);
        if (def == null) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_FILTER_UNKNOWN_REF)
                    .param(NopAiAgentErrors.ARG_FILTER_ID, id);
        }
        String impl = def.getImpl();
        if (impl == null || impl.isEmpty()) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_FILTER_DEF_MISSING_IMPL)
                    .param(NopAiAgentErrors.ARG_FILTER_ID, id);
        }
        Object instance;
        try {
            instance = ClassHelper.safeNewInstance(impl);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "filter-def '" + id + "' impl '" + impl + "' could not be instantiated", e);
        }
        if (!(instance instanceof IAgentMiddleware)) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_FILTER_DEF_NOT_MIDDLEWARE)
                    .param(NopAiAgentErrors.ARG_FILTER_ID, id)
                    .param(NopAiAgentErrors.ARG_IMPL, impl);
        }
        IAgentMiddleware mw = (IAgentMiddleware) instance;
        instanceCache.put(id, mw);
        return mw;
    }

    /**
     * Resolve the {@code points} override; fall back to the supplied default
     * when no override is declared. Each point name must resolve to a session
     * lifecycle point (fast-fail on unknown).
     */
    private static List<AgentLifecyclePoint> resolvePoints(FilterRefModel ref,
                                                           AgentLifecyclePoint defaultPoint) {
        Set<String> points = ref.getPoints();
        if (points == null || points.isEmpty()) {
            return Collections.singletonList(defaultPoint);
        }
        List<AgentLifecyclePoint> resolved = new ArrayList<>(points.size());
        for (String name : points) {
            AgentLifecyclePoint point = DefaultHookRegistry.resolveLifecyclePoint(name);
            if (point == null) {
                throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_FILTER_UNKNOWN_POINT)
                        .param(NopAiAgentErrors.ARG_FILTER_ID, ref.getRef())
                        .param(NopAiAgentErrors.ARG_POINT, name);
            }
            resolved.add(point);
        }
        return resolved;
    }
}
