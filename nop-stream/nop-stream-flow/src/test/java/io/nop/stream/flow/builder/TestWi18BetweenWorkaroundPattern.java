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
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI18 (plan 2364): the WI0(b) DEFAULT-branch BETWEEN workaround pattern — interval
 * predicates expressed WITHOUT a range/non-equi join: split the interval table into
 * discrete segments, feed each segment through a two-way EQUI join on the segment
 * key, union the join outputs, and apply the exact BETWEEN predicate in a
 * {@code <process>} filter (the join narrows candidates to the right segment; the
 * predicate enforces low &lt;= x &lt; high).
 *
 * <p>Pins:
 * <ol>
 *   <li>the workaround TOPOLOGY is a legal DSL build (segment filters ×2, two
 *       joinRef joins on the seg key, union, process, sink);</li>
 *   <li>the BETWEEN predicate semantics are correct on the boundary three sides
 *       (x=low matches, x=high does not, out-of-range does not).</li>
 * </ol>
 * The full semantic acceptance (event-time dimension scenarios) belongs to the E11/E12
 * suites (WI26). The interval-predicate declaration surface stays excluded per WI0(b).
 */
public class TestWi18BetweenWorkaroundPattern {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * The BETWEEN predicate the workaround pattern enforces: low &lt;= amount &lt; high
     * (half-open interval — the Flink-community convention for BETWEEN workarounds so
     * adjacent segments tile without overlap).
     */
    static boolean between(int amount, int low, int high) {
        return amount >= low && amount < high;
    }

    @Test
    public void workaroundTopologyBuilds() {
        // orders → seg filters ×2; rules → segment rows; two equi joins (ON seg);
        // union; process filter; sink.
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi18-between\" version=\"1\">"
                + "<joins>"
                + "<joinSpec joinId=\"segJoin0\" joinType=\"INNER\" "
                + "leftKeyExprs=\"seg\" rightKeyExprs=\"seg\"/>"
                + "<joinSpec joinId=\"segJoin1\" joinType=\"INNER\" "
                + "leftKeyExprs=\"seg\" rightKeyExprs=\"seg\"/>"
                + "</joins>"
                + "<transforms>"
                + "<source id=\"orders\" bean=\"ordersSrc\"/>"
                + "<source id=\"rules\" bean=\"rulesSrc\"/>"
                + "<filter id=\"ordSeg0\"><source>event.seg == 0</source></filter>"
                + "<filter id=\"ordSeg1\"><source>event.seg == 1</source></filter>"
                + "<filter id=\"ruleSeg0\"><source>event.seg == 0</source></filter>"
                + "<filter id=\"ruleSeg1\"><source>event.seg == 1</source></filter>"
                + "<join id=\"joinL\" joinRef=\"segJoin0\"/>"
                + "<join id=\"joinH\" joinRef=\"segJoin1\"/>"
                + "<union id=\"merged\"/>"
                + "<process id=\"betweenFilter\" bean=\"wi18BetweenFilter\"/>"
                + "<sink id=\"out\" bean=\"wi18Sink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"orders\" to=\"ordSeg0\"/>"
                + "<edge id=\"e1\" from=\"orders\" to=\"ordSeg1\"/>"
                + "<edge id=\"e2\" from=\"rules\" to=\"ruleSeg0\"/>"
                + "<edge id=\"e3\" from=\"rules\" to=\"ruleSeg1\"/>"
                + "<edge id=\"e4\" from=\"ordSeg0\" to=\"joinL\"/>"
                + "<edge id=\"e5\" from=\"ruleSeg0\" to=\"joinL\"/>"
                + "<edge id=\"e6\" from=\"ordSeg1\" to=\"joinH\"/>"
                + "<edge id=\"e7\" from=\"ruleSeg1\" to=\"joinH\"/>"
                + "<edge id=\"e8\" from=\"joinL\" to=\"merged\"/>"
                + "<edge id=\"e9\" from=\"joinH\" to=\"merged\"/>"
                + "<edge id=\"e10\" from=\"merged\" to=\"betweenFilter\"/>"
                + "<edge id=\"e11\" from=\"betweenFilter\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(node);
        // THE PATTERN PIN: the workaround topology (segment split + two equi joins +
        // union + predicate filter) is a legal DSL build.
        org.junit.jupiter.api.Assertions.assertNotNull(
                StreamModelDslBuilder.of(model, wi18Resolver()).build(),
                "the BETWEEN workaround topology must be a legal DSL build");
    }

    @Test
    public void betweenPredicateBoundarySemantics() {
        // boundary three sides: x=low matches (half-open), x=high does not,
        // out-of-range does not — the exact BETWEEN semantics the workaround
        // enforces after the equi-join candidate narrowing.
        assertTrue(between(0, 0, 100), "x=low must match (half-open interval)");
        assertFalse(between(100, 0, 100), "x=high must not match (no overlap tiling)");
        assertFalse(between(250, 0, 100), "out-of-range must not match");
        assertTrue(between(99, 0, 100), "inside the interval must match");
        assertTrue(between(100, 100, 200), "the next segment starts where the previous ended");
    }

    private static InMemoryBeanFunctionResolver wi18Resolver() {
        return new InMemoryBeanFunctionResolver()
                // the process filter bean: a pass-through holder — the topology pin
                // only needs the build to succeed; the predicate semantics are pinned
                // above
                .register("wi18BetweenFilter", new Wi18PassThroughFilter())
                .register("wi18Sink",
                        (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                        })
                .register("ordersSrc", new io.nop.stream.flow.testing.TestSourceFunction())
                .register("rulesSrc", new io.nop.stream.flow.testing.TestSourceFunction());
    }

    static class Wi18PassThroughFilter
            extends io.nop.stream.core.common.functions.ProcessFunction<Object, Object> {
        @Override
        public void processElement(Object value,
                                   io.nop.stream.core.common.functions.ProcessFunction<Object, Object>.Context ctx,
                                   io.nop.stream.core.util.Collector<Object> out) {
            out.collect(value);
        }
    }
}
