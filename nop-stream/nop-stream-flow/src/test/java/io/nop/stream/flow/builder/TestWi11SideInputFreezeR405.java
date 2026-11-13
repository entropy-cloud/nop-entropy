/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.45 R4-05 (plan 2538) W-01 / 1.3-MA-02: the WI11 side-input "frozen read-only
 * build-time snapshot" contract must be mechanically enforced, not just declared.
 * Pre-fix the {@code Supplier.get()} result is stored BARE and flows straight into
 * every per-record xpl call scope, so (a) an xpl body write
 * {@code sideInputs['dict'].put(...)} mutates the snapshot for all subsequent records
 * (cross-record pollution), (b) the snapshot stays attached to the application's live
 * reference (post-build application writes tear through), and (c) nested map values
 * stay mutable.
 *
 * <p>Driver 红①: every probe below is deterministic and single-threaded. Pre-fix the
 * write probes SUCCEED (so the typed-rejection assertion fails = red); post-fix the
 * frozen snapshot rejects writes with {@link UnsupportedOperationException} and the
 * detach leg observes build-time isolation from the application reference.
 */
public class TestWi11SideInputFreezeR405 {

    private static Map<String, Object> DICT;
    private static Map<String, Object> SUB;

    private static CollectingSinkFunction<Object> sink;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void resetDict() {
        SUB = new LinkedHashMap<>();
        SUB.put("a", 1);
        DICT = new LinkedHashMap<>();
        DICT.put("k1", "v1");
        DICT.put("sub", SUB);
        sink = new CollectingSinkFunction<>();
    }

    private static io.nop.stream.core.environment.StreamExecutionEnvironment buildEnv(String mapBody) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"wi11-freeze-r405\" version=\"1\">"
                + "<sideInputs><sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrich\" broadcast=\"true\"/></sideInputs>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"r405Src\"/>"
                + "<map id=\"enrich\"><source>" + mapBody + "</source></map>"
                + "<sink id=\"out\" bean=\"r405Sink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"enrich\"/>"
                + "<edge id=\"e1\" from=\"enrich\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(node);
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("dictSupplier", (Supplier<Map<String, Object>>) () -> DICT)
                .register("r405Src", new TwoRecordSource())
                .register("r405Sink", sink);
        return StreamModelDslBuilder.of(model, resolver).build();
    }

    private static void assertUoeInCauseChain(Throwable failure, String probe) {
        Throwable t = failure;
        while (t != null) {
            if (t instanceof UnsupportedOperationException) {
                return;
            }
            t = t.getCause() == t ? null : t.getCause();
        }
        fail("side-input snapshot write must be rejected typed (UnsupportedOperationException), probe="
                + probe + " but got: " + failure);
    }

    /** 红① main probe (failure mode ①): an xpl body write to the nested snapshot map. */
    @Test
    public void xplBodyWriteToNestedSnapshotIsRejectedTyped() {
        io.nop.stream.core.environment.StreamExecutionEnvironment env =
                buildEnv("sideInputs['dict'].put('injected','x'); return event;");
        try {
            env.execute("wi11-freeze-nested-write");
            fail("xpl body write to the frozen snapshot must be rejected typed");
        } catch (Exception e) {
            assertUoeInCauseChain(e, "sideInputs['dict'].put");
        }
        assertEquals(List.of(), sink.getCollected(),
                "the polluted write must fail before any record is consumed downstream");
    }

    /**
     * Failure mode ③: the snapshot must be detached from the application's live
     * reference — post-build application writes must NOT tear through.
     */
    @Test
    public void snapshotIsDetachedFromApplicationReference() throws Exception {
        io.nop.stream.core.environment.StreamExecutionEnvironment env =
                buildEnv("return sideInputs['dict']['k1'];");
        // application-side post-build write to the SAME live map the supplier returned
        DICT.put("k1", "MUTATED");
        env.execute("wi11-freeze-detach");
        assertEquals(List.of("v1", "v1"), sink.getCollected(),
                "the frozen snapshot must not see post-build application writes");
    }

    /** Failure mode ② (bindings-level half): a top-level write into the sideInputs wrapper. */
    @Test
    public void snapshotTopLevelBindingsWriteIsRejectedTyped() {
        io.nop.stream.core.environment.StreamExecutionEnvironment env =
                buildEnv("sideInputs.put('hijack','x'); return event;");
        try {
            env.execute("wi11-freeze-toplevel");
            fail("top-level sideInputs write must be rejected typed");
        } catch (Exception e) {
            assertUoeInCauseChain(e, "top-level sideInputs.put");
        }
    }

    /** Recursion leg: a second-level nested map must be frozen too. */
    @Test
    public void snapshotNestedSecondLevelWriteIsRejectedTyped() {
        io.nop.stream.core.environment.StreamExecutionEnvironment env =
                buildEnv("sideInputs['dict']['sub'].put('s2','x'); return event;");
        try {
            env.execute("wi11-freeze-nested2");
            fail("second-level nested snapshot write must be rejected typed");
        } catch (Exception e) {
            assertUoeInCauseChain(e, "nested second-level put");
        }
    }

    /** A deterministic bounded source: emits two records then completes. */
    public static class TwoRecordSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<String> {
        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<String> ctx) {
            ctx.collect("a");
            ctx.collect("b");
        }

        @Override
        public void cancel() {
        }
    }
}
