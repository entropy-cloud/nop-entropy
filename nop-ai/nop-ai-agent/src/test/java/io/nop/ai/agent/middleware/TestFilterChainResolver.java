package io.nop.ai.agent.middleware;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.model.AgentFilterChainModel;
import io.nop.ai.agent.model.FilterDefModel;
import io.nop.ai.agent.model.FilterRefModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3-2 Phase 1 unit tests for {@link FilterChainResolver} and
 * {@link ResolvedFilterChain}.
 *
 * <p>Verifies (per plan item 1.5 / exit criteria):
 * <ul>
 *   <li>Declarative filter refs are read from the chain model.</li>
 *   <li>Filter IDs resolve to {@link IAgentMiddleware} instances (D1 option B:
 *       agent-internal &lt;filter-definitions&gt;).</li>
 *   <li>Unknown filter ID fast-fails (NopAiAgentException with ID name) — no
 *       silent skip.</li>
 *   <li>Missing impl / non-IAgentMiddleware impl fast-fail.</li>
 *   <li>D2 default mapping: input→PRE_CALL, output→POST_CALL (single trigger).</li>
 *   <li>{@code points} override replaces the default mapping.</li>
 *   <li>ResolvedFilterChain keeps declarative refs and resolved objects in sync;
 *       a filter referenced from both chains is instantiated once
 *       (identity-shared).</li>
 *   <li>ResolvedFilterChain is immutable (unmodifiable views).</li>
 * </ul>
 */
public class TestFilterChainResolver {

    // ---- Reflection-instantiable filter fixtures (public static nested so
    //      ClassHelper.safeNewInstance can load+newInstance them). Each records
    //      a fixed marker to a shared static log so execution order is visible. ----

    public static final List<String> LOG = new ArrayList<>();

    public static class AuthFilter implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            LOG.add("auth");
            return next.proceed(ctx);
        }
    }

    public static class RateLimitFilter implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            LOG.add("rate-limit");
            return next.proceed(ctx);
        }
    }

    public static class ContentCheckFilter implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            LOG.add("content-check");
            return next.proceed(ctx);
        }
    }

    /** Does NOT implement IAgentMiddleware — used for the not-a-middleware test. */
    public static class PlainNonMiddleware {
    }

    @BeforeEach
    void clearLog() {
        LOG.clear();
    }

    private static String cls(Class<?> c) {
        return c.getName();
    }

    private FilterDefModel def(String id, Class<?> impl) {
        FilterDefModel d = new FilterDefModel();
        d.setId(id);
        d.setImpl(cls(impl));
        return d;
    }

    private FilterRefModel ref(String id) {
        FilterRefModel r = new FilterRefModel();
        r.setRef(id);
        return r;
    }

    private FilterRefModel ref(String id, String points) {
        FilterRefModel r = new FilterRefModel();
        r.setRef(id);
        r.setPoints(Set.of(points.split(",")));
        return r;
    }

    private AgentFilterChainModel chain(List<FilterDefModel> defs,
                                        List<FilterRefModel> inputs,
                                        List<FilterRefModel> outputs) {
        AgentFilterChainModel c = new AgentFilterChainModel();
        if (defs != null) c.setFilterDefinitions(defs);
        if (inputs != null) c.setInputFilters(inputs);
        if (outputs != null) c.setOutputFilters(outputs);
        return c;
    }

    @Test
    void nullChainResolvesToEmpty() {
        ResolvedFilterChain r = FilterChainResolver.resolve(null);
        assertTrue(r.isEmpty());
        assertTrue(r.getInputFilterRefs().isEmpty());
        assertTrue(r.getOutputFilterRefs().isEmpty());
        assertTrue(r.getResolvedByPoint().isEmpty());
    }

    @Test
    void inputFiltersDefaultMapToPreCall() {
        AgentFilterChainModel c = chain(
                Arrays.asList(def("auth", AuthFilter.class), def("rate", RateLimitFilter.class)),
                Arrays.asList(ref("auth"), ref("rate")),
                null);

        ResolvedFilterChain r = FilterChainResolver.resolve(c);

        // D2 default: input filters → PRE_CALL, single point, declared order.
        List<IAgentMiddleware> preCall = r.getResolved(AgentLifecyclePoint.PRE_CALL);
        assertEquals(2, preCall.size());
        assertEquals(AuthFilter.class, preCall.get(0).getClass());
        assertEquals(RateLimitFilter.class, preCall.get(1).getClass());
        // No other point touched (single-trigger semantics).
        assertTrue(r.getResolved(AgentLifecyclePoint.POST_CALL).isEmpty());
        assertTrue(r.getResolved(AgentLifecyclePoint.PRE_REASONING).isEmpty());
    }

    @Test
    void outputFiltersDefaultMapToPostCall() {
        AgentFilterChainModel c = chain(
                Collections.singletonList(def("content", ContentCheckFilter.class)),
                null,
                Collections.singletonList(ref("content")));

        ResolvedFilterChain r = FilterChainResolver.resolve(c);

        // D2 default: output filters → POST_CALL.
        List<IAgentMiddleware> postCall = r.getResolved(AgentLifecyclePoint.POST_CALL);
        assertEquals(1, postCall.size());
        assertEquals(ContentCheckFilter.class, postCall.get(0).getClass());
        assertTrue(r.getResolved(AgentLifecyclePoint.PRE_CALL).isEmpty());
    }

    @Test
    void pointsOverrideReplacesDefaultMapping() {
        // input filter with points="pre_reasoning" → NOT PRE_CALL, IS PRE_REASONING.
        AgentFilterChainModel c = chain(
                Collections.singletonList(def("prompt-check", AuthFilter.class)),
                Collections.singletonList(ref("prompt-check", "pre_reasoning")),
                null);

        ResolvedFilterChain r = FilterChainResolver.resolve(c);

        assertTrue(r.getResolved(AgentLifecyclePoint.PRE_CALL).isEmpty());
        List<IAgentMiddleware> preReasoning = r.getResolved(AgentLifecyclePoint.PRE_REASONING);
        assertEquals(1, preReasoning.size());
        assertEquals(AuthFilter.class, preReasoning.get(0).getClass());
    }

    @Test
    void pointsWithMultipleTargetsRegistersAtEach() {
        AgentFilterChainModel c = chain(
                Collections.singletonList(def("audit", AuthFilter.class)),
                Collections.singletonList(ref("audit", "pre_acting,post_acting")),
                null);

        ResolvedFilterChain r = FilterChainResolver.resolve(c);

        assertEquals(1, r.getResolved(AgentLifecyclePoint.PRE_ACTING).size());
        assertEquals(1, r.getResolved(AgentLifecyclePoint.POST_ACTING).size());
    }

    @Test
    void unknownFilterIdFailsFastWithIdInMessage() {
        AgentFilterChainModel c = chain(
                Collections.emptyList(),
                Collections.singletonList(ref("does-not-exist")),
                null);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> FilterChainResolver.resolve(c));
        // No silent skip: exception message contains the offending ID.
        assertTrue(ex.getMessage().contains("does-not-exist"));
    }

    @Test
    void missingImplFailsFastWithIdInMessage() {
        FilterDefModel d = new FilterDefModel();
        d.setId("noimpl");
        // impl deliberately left null
        AgentFilterChainModel c = chain(
                Collections.singletonList(d),
                Collections.singletonList(ref("noimpl")),
                null);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> FilterChainResolver.resolve(c));
        assertTrue(ex.getMessage().contains("noimpl"));
    }

    @Test
    void implNotMiddlewareFailsFast() {
        AgentFilterChainModel c = chain(
                Collections.singletonList(def("plain", PlainNonMiddleware.class)),
                Collections.singletonList(ref("plain")),
                null);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> FilterChainResolver.resolve(c));
        assertTrue(ex.getMessage().contains("plain"));
    }

    @Test
    void unknownPointNameFailsFastWithPointInMessage() {
        AgentFilterChainModel c = chain(
                Collections.singletonList(def("f", AuthFilter.class)),
                Collections.singletonList(ref("f", "totally-not-a-point")),
                null);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> FilterChainResolver.resolve(c));
        assertTrue(ex.getMessage().contains("totally-not-a-point"));
    }

    @Test
    void duplicateFilterDefIdFailsFast() {
        // Duplicate filter-def ids are rejected fail-fast — at the model layer
        // the KeyedList (xdef:key-attr="id") throws on duplicate keys during
        // setFilterDefinitions, before the resolver even runs. This is the
        // parse-time guard; the resolver's own indexFilterDefinitions is
        // defense-in-depth for lists that bypass the KeyedList path.
        AgentFilterChainModel c = new AgentFilterChainModel();
        // The offending duplicate-key assignment throws (fail-fast, no silent dedupe).
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                c.setFilterDefinitions(Arrays.asList(
                        def("dup", AuthFilter.class), def("dup", RateLimitFilter.class))));
        // The duplicate key name is carried as a param on the NopException.
        assertTrue(ex.getMessage().contains("dup")
                || ex.toString().toLowerCase().contains("dup")
                || ex.toString().toLowerCase().contains("duplicate"));
    }

    @Test
    void filterReferencedFromBothChainsIsIdentityShared() {
        // A filter referenced from both input and output is instantiated once
        // (D1 cache), so D3 duplicate detection against <middlewares> is
        // unambiguous by object identity.
        AgentFilterChainModel c = chain(
                Collections.singletonList(def("shared", AuthFilter.class)),
                Collections.singletonList(ref("shared")),
                Collections.singletonList(ref("shared")));

        ResolvedFilterChain r = FilterChainResolver.resolve(c);

        IAgentMiddleware inInstance = r.getResolved(AgentLifecyclePoint.PRE_CALL).get(0);
        IAgentMiddleware outInstance = r.getResolved(AgentLifecyclePoint.POST_CALL).get(0);
        assertSame(inInstance, outInstance);
    }

    @Test
    void distinctFilterIdsYieldDistinctInstances() {
        AgentFilterChainModel c = chain(
                Arrays.asList(def("a", AuthFilter.class), def("b", RateLimitFilter.class)),
                Arrays.asList(ref("a"), ref("b")),
                null);

        ResolvedFilterChain r = FilterChainResolver.resolve(c);

        List<IAgentMiddleware> preCall = r.getResolved(AgentLifecyclePoint.PRE_CALL);
        assertNotSame(preCall.get(0), preCall.get(1));
    }

    @Test
    void declarativeRefsAndResolvedObjectsAreInSync() {
        FilterRefModel inRef = ref("auth");
        FilterRefModel outRef = ref("content");
        AgentFilterChainModel c = chain(
                Arrays.asList(def("auth", AuthFilter.class), def("content", ContentCheckFilter.class)),
                Collections.singletonList(inRef),
                Collections.singletonList(outRef));

        ResolvedFilterChain r = FilterChainResolver.resolve(c);

        // Declarative side (serializable / auditable)
        assertEquals(1, r.getInputFilterRefs().size());
        assertEquals("auth", r.getInputFilterRefs().get(0).getRef());
        assertEquals(1, r.getOutputFilterRefs().size());
        assertEquals("content", r.getOutputFilterRefs().get(0).getRef());
        // Execution side (resolved objects)
        assertEquals(AuthFilter.class, r.getResolved(AgentLifecyclePoint.PRE_CALL).get(0).getClass());
        assertEquals(ContentCheckFilter.class, r.getResolved(AgentLifecyclePoint.POST_CALL).get(0).getClass());
    }

    @Test
    void resolvedFilterChainIsImmutable() {
        AgentFilterChainModel c = chain(
                Collections.singletonList(def("auth", AuthFilter.class)),
                Collections.singletonList(ref("auth")),
                null);

        ResolvedFilterChain r = FilterChainResolver.resolve(c);

        // Declarative ref list is an unmodifiable view.
        assertThrows(UnsupportedOperationException.class, () -> r.getInputFilterRefs().clear());
        // Resolved-by-point map and per-point lists are unmodifiable.
        assertThrows(UnsupportedOperationException.class, () -> r.getResolvedByPoint().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> r.getResolved(AgentLifecyclePoint.PRE_CALL).clear());
        // getResolved for a point with no filters returns an empty list (not null).
        assertNull(r.getResolvedByPoint().get(AgentLifecyclePoint.POST_CALL));
        assertTrue(r.getResolved(AgentLifecyclePoint.POST_CALL).isEmpty());
    }

    @Test
    void resolvedFiltersActuallyExecuteViaMiddlewareChain() {
        // W3-2 wiring: resolved filters can be threaded into a MiddlewareChain
        // and execute in onion order (verifies they are real IAgentMiddleware
        // instances, not stubs).
        AgentFilterChainModel c = chain(
                Arrays.asList(def("auth", AuthFilter.class), def("rate", RateLimitFilter.class)),
                Arrays.asList(ref("auth"), ref("rate")),
                null);

        ResolvedFilterChain r = FilterChainResolver.resolve(c);
        List<IAgentMiddleware> filters = r.getResolved(AgentLifecyclePoint.PRE_CALL);
        MiddlewareChain chain = new MiddlewareChain(filters, 0, ctx -> {
            LOG.add("core");
            return HookResult.PassResult.instance();
        });
        HookContext ctx = new HookContext(AgentLifecyclePoint.PRE_CALL, null);

        HookResult result = chain.proceed(ctx);

        assertTrue(result.isPass());
        // Filters execute in declared order: auth → rate-limit → core.
        assertEquals(Arrays.asList("auth", "rate-limit", "core"), LOG);
    }
}
