/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.sql.eval;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.45 R4-05 (plan 2538) W-04 / W-08 / W-09 — the SQL static-cache and lifecycle
 * domain guards.
 *
 * <p>W-04 (1.3-MA-05, driver 红④): the two process-lifetime static caches close their
 * envelope by DECLARATION (merge-adjudication "declare reject condition or bound"
 * dual reading) — the bound/reject-condition text must be present on both carriers
 * (structure pin, honest structure-red discipline: no behavioral fake-red).
 *
 * <p>W-08 (1.3-C3-005, driver 红⑧): {@code getterFor} must not run reflective
 * introspection inside a {@code ConcurrentHashMap} bin lock ({@code computeIfAbsent}
 * callback) — class-loading lock inversion (JDK-8161372 family). Structure pin
 * (two-phase get → compute outside lock → putIfAbsent backfill) + functional
 * round-trip legs; the mutation four-phase chain (restore → pin red → re-fix →
 * green) is recorded in the red-green ledger, NOT faked behaviorally.
 *
 * <p>W-09 (1.3-C6-003, driver 红⑨): {@code close()} must identity-guard-unbind the
 * static default ({@code if (defaultResolver == this) setDefault(null)}); a late
 * {@code call()} after close hits the pre-existing null fail-fast (plan 2512 channel,
 * zero rewrite); replacement does NOT propagate close (ownership stays with the
 * registrant); re-registration after close is a legal lifecycle.
 */
public class TestSqlStaticLifecycleGuardsR405 {

    private StreamScalarFunctionResolver resolverA;

    @BeforeEach
    public void setUp() {
        resolverA = new StreamScalarFunctionResolver();
    }

    @AfterEach
    public void tearDown() {
        StreamScalarFunctionResolver.setDefault(null);
    }

    // ------------------------------------------------------------------
    // 红④ (W-04 / 1.3-MA-05): bound declaration present on BOTH carriers
    // ------------------------------------------------------------------

    /** Carrier ①: GETTER_CACHE bound declaration (structure pin). */
    @Test
    public void getterCacheBoundDeclarationIsDeclared() throws IOException {
        String src = mainSource("RecordColumnAccess.java");
        assertTrue(src.contains("1.3-MA-05"),
                "RecordColumnAccess must carry the W-04 bound declaration (CRA 8.45 R4-05 anchor)");
        assertTrue(src.contains("insert-only, no eviction"),
                "GETTER_CACHE declaration must state the entry universe is insert-only per class");
        assertTrue(src.contains("dynamic classloader redeploy"),
                "GETTER_CACHE declaration must name the rejected scenario (stale entries under redeploy)");
    }

    /** Carrier ②: SqlRowAggregateOps.CACHE reject-condition precision (structure pin). */
    @Test
    public void aggregateOpsCacheRejectConditionIsPrecise() throws IOException {
        String src = mainSource("SqlRowAggregateOps.java");
        assertTrue(src.contains("1.3-MA-05"),
                "SqlRowAggregateOps must carry the W-04 envelope precision (CRA 8.45 R4-05 anchor)");
        assertTrue(src.contains("one entry per compiled pipeline"),
                "CACHE declaration must state when the per-pipeline bound holds");
        assertTrue(src.contains("DISTINCT spec JSON"),
                "CACHE declaration must name the rejected scenario (infinitely distinct spec JSON keys)");
    }

    // ------------------------------------------------------------------
    // 红⑧ (W-08 / 1.3-C3-005): two-phase lock-free compute structure pin
    // ------------------------------------------------------------------

    /** Structure pin: getterFor computes OUTSIDE the CHM bin lock (no computeIfAbsent). */
    @Test
    public void getterForUsesTwoPhaseLockFreeCompute() throws IOException {
        String src = mainSource("RecordColumnAccess.java");
        assertTrue(!src.contains("computeIfAbsent"),
                "getterFor must not backfill via computeIfAbsent (reflection inside a CHM bin lock "
                        + "makes a class-loading lock inversion reachable) — found computeIfAbsent");
        assertTrue(src.contains("GETTER_CACHE.get(clazz)"),
                "getterFor must read the cache with a plain lock-free get first");
        assertTrue(src.contains("putIfAbsent"),
                "getterFor must backfill a lock-computed miss with putIfAbsent");
    }

    /** Functional round-trip leg: getter resolution resolves and invokes (hit path). */
    @Test
    public void columnAccessOnBeanRecordResolvesAndInvokes() {
        SampleBean bean = new SampleBean();
        bean.setName("cached");
        assertEquals("cached", new RecordColumnAccess("name").get(bean));
        // second access goes through the cached entry (same observable result)
        assertEquals("cached", new RecordColumnAccess("name").get(bean));
    }

    /** A concurrent miss double-computes harmlessly: every result stays correct. */
    @Test
    public void concurrentMissRemainsCorrect() throws Exception {
        SampleBean bean = new SampleBean();
        bean.setName("race");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    assertEquals("race", new RecordColumnAccess("name").get(bean));
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join(5000);
            assertTrue(!t.isAlive(), "concurrent first-touch must not hang");
        }
        if (failure.get() != null) {
            throw new AssertionError("concurrent first-touch failed", failure.get());
        }
        assertEquals("race", new RecordColumnAccess("name").get(bean));
    }

    // ------------------------------------------------------------------
    // 红⑨ (W-09 / 1.3-C6-003): close() identity-guarded unbind
    // ------------------------------------------------------------------

    /** close() unbinds THIS default; a late static call() hits the null fail-fast. */
    @Test
    public void closeUnbindsDefaultAndLateCallFailsTyped() {
        resolverA.register("fnA", args -> "A:" + args[0]);
        resolverA.registerAsDefault();
        assertEquals("A:x", StreamScalarFunctionResolver.call("fnA", "x"),
                "pre-close call resolves the registered default");

        resolverA.close();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StreamScalarFunctionResolver.call("fnA", "x"),
                "a late call() after close must hit the null fail-fast (plan 2512 channel)");
        assertTrue(ex.getMessage().contains("no IScalarFunctionResolver default registered"),
                () -> "typed fail-fast must identify the unbound default: " + ex.getMessage());

        // lifecycle symmetry: re-registration after close is legal (recovery lifeline)
        resolverA.registerAsDefault();
        assertEquals("A:y", StreamScalarFunctionResolver.call("fnA", "y"),
                "re-registering as default after close must restore resolution");
    }

    /** close() on a NON-default instance must not unbind someone else's registration. */
    @Test
    public void closeOfNonDefaultInstanceDoesNotUnbind() {
        StreamScalarFunctionResolver other = new StreamScalarFunctionResolver();
        try {
            resolverA.register("fnA", args -> "A");
            resolverA.registerAsDefault();
            other.close(); // a legal lifecycle event for a non-registered instance
            assertEquals("A", StreamScalarFunctionResolver.call("fnA"),
                    "closing a non-default instance must not unbind the registered default");
        } finally {
            StreamScalarFunctionResolver.setDefault(null);
        }
    }

    /** close() is idempotent (pre-existing closeIsIdempotent contract, new-form check). */
    @Test
    public void closeTwiceKeepsConsistentState() {
        StreamScalarFunctionResolver other = new StreamScalarFunctionResolver();
        try {
            resolverA.registerAsDefault();
            resolverA.close();
            resolverA.close(); // second close: no longer the default — identity guard false, no-op
            assertThrows(IllegalStateException.class,
                    () -> StreamScalarFunctionResolver.call("fnA"),
                    "state after double close stays unbound");
            // an unrelated instance registered afterwards is untouched by more closes
            other.registerAsDefault();
            resolverA.close();
            assertNotNull(StreamScalarFunctionResolver.getDefault(),
                    "closing the OLD instance must not unbind the NEW default");
        } finally {
            StreamScalarFunctionResolver.setDefault(null);
        }
    }

    /** setDefault(A → B) does NOT close A — ownership stays with A's registrant. */
    @Test
    public void defaultReplacementDoesNotCloseOldInstance() {
        StreamScalarFunctionResolver b = new StreamScalarFunctionResolver();
        try {
            resolverA.register("fn", args -> "from-A");
            b.register("fn", args -> "from-B");
            resolverA.registerAsDefault();
            StreamScalarFunctionResolver.setDefault(b);

            // the new resolver's table is authoritative for lookups...
            assertEquals("from-B", StreamScalarFunctionResolver.call("fn"),
                    "the replacement resolver's table must be authoritative");
            // ...but A was NOT closed by the replacement: its own registry is intact
            // (close is A's owner's decision — declaration leg of the propagation ruling)
            assertTrue(resolverA.contains("fn"),
                    "setDefault replacement must not close or clear the old instance");
        } finally {
            StreamScalarFunctionResolver.setDefault(null);
        }
    }

    // ------------------------------------------------------------------

    private static String mainSource(String fileName) throws IOException {
        Path path = Paths.get("src", "main", "java", "io", "nop", "stream", "sql", "eval", fileName);
        if (!Files.exists(path)) {
            // repo-root working directory fallback
            path = Paths.get("nop-stream", "nop-stream-sql", "src", "main", "java",
                    "io", "nop", "stream", "sql", "eval", fileName);
        }
        if (!Files.exists(path)) {
            fail("cannot locate main source for structure pin: " + fileName);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** minimal bean record for the getter-cache functional leg */
    public static class SampleBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
