package io.nop.ai.agent.middleware;

import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.model.FilterRefModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W3-2 (declarative filter chain): the resolved form of an agent's
 * {@code <filter-chain>}. Holds the declarative filter refs (serializable /
 * auditable) and the resolved {@link IAgentMiddleware} objects (execution side)
 * keyed by the lifecycle point they were mapped to.
 *
 * <p><b>Design</b> (plano ResolvedFilterChain pattern, adapted to nop's
 * typed-object model): the declarative {@code filterRefs} and the resolved
 * middleware objects stay in sync — every ref has been resolved to exactly one
 * middleware instance at construction time (fast-fail on unknown ID). The
 * resolved map is keyed by lifecycle point so the assembler can merge
 * declarative filters with code-class {@code <middlewares>} following the D3
 * coexistence rule.
 *
 * <p><b>Immutability</b>: instances are immutable after construction. The
 * declarative ref lists and the resolved map are unmodifiable views; the
 * contained {@link IAgentMiddleware} objects are the live singletons used at
 * execution time.
 */
public final class ResolvedFilterChain {

    private final List<FilterRefModel> inputFilterRefs;
    private final List<FilterRefModel> outputFilterRefs;
    private final Map<AgentLifecyclePoint, List<IAgentMiddleware>> resolvedByPoint;

    public ResolvedFilterChain(List<FilterRefModel> inputFilterRefs,
                               List<FilterRefModel> outputFilterRefs,
                               Map<AgentLifecyclePoint, List<IAgentMiddleware>> resolvedByPoint) {
        this.inputFilterRefs = inputFilterRefs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(inputFilterRefs);
        this.outputFilterRefs = outputFilterRefs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(outputFilterRefs);
        Map<AgentLifecyclePoint, List<IAgentMiddleware>> frozen = new LinkedHashMap<>();
        for (Map.Entry<AgentLifecyclePoint, List<IAgentMiddleware>> e : resolvedByPoint.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        this.resolvedByPoint = Collections.unmodifiableMap(frozen);
    }

    /** Declarative input filter refs (request side), in declared order. */
    public List<FilterRefModel> getInputFilterRefs() {
        return inputFilterRefs;
    }

    /** Declarative output filter refs (response side), in declared order. */
    public List<FilterRefModel> getOutputFilterRefs() {
        return outputFilterRefs;
    }

    /**
     * Resolved middlewares grouped by the lifecycle point they were mapped to
     * (D2 default mapping or {@code points} override). Each list preserves the
     * declared filter order. Empty for points with no declarative filters.
     */
    public Map<AgentLifecyclePoint, List<IAgentMiddleware>> getResolvedByPoint() {
        return resolvedByPoint;
    }

    /** @return the resolved middlewares for the given point, or empty list. */
    public List<IAgentMiddleware> getResolved(AgentLifecyclePoint point) {
        List<IAgentMiddleware> list = resolvedByPoint.get(point);
        return list != null ? list : Collections.emptyList();
    }

    public boolean isEmpty() {
        return inputFilterRefs.isEmpty() && outputFilterRefs.isEmpty();
    }
}
