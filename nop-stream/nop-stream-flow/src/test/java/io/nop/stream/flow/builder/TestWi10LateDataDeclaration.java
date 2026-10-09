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
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10 (plan 2356): structural pairing checks for the declared
 * {@code <window lateData="tag">} late-data side output. All checks run in
 * {@code validateDag} — BEFORE any build-loop work — so every test model here
 * fails fast without touching the runtime window-operator factory (nop-stream-flow
 * has no nop-stream-runtime dependency; the positive wiring assertion lives in the
 * runtime E2E, see {@code TestLateDataSideOutputE2E}).
 *
 * <p>Check order pinned by these tests (stable error for the minimal broken model):
 * tag collision → DUPLICATE_ID; host count ≠ 1 → INVALID_ARG; consumer count ≠ 1 →
 * INVALID_ARG; consumer from ≠ host → REF_UNKNOWN; target/partition/keyExpr → same
 * codes as the tap path. A blank {@code lateData=""} is normalized to "undeclared" by
 * the xdef parser (probed: {@code getLateData()==null} — the platform-wide behavior
 * for optional string attributes), so it has no dedicated builder-level check.
 */
public class TestWi10LateDataDeclaration {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void lateDataCollidingWithSideOutputTagFailsFast() {
        StreamModel model = parse(
                "<sideOutput id=\"so\" tag=\"t\"/>"
                        + "<window id=\"w\" strategyRef=\"s\" lateData=\"t\"/>",
                "");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.duplicate-id", ex.getErrorCode().toString(),
                () -> "Tag collision must fail with ERR_STREAM_DUPLICATE_ID: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("sideOutput"),
                () -> "Exception should name the colliding sideOutput: " + ex.getMessage());
    }

    @Test
    public void lateDataDeclaredByTwoWindowsFailsFast() {
        StreamModel model = parse(
                "<window id=\"w1\" strategyRef=\"s\" lateData=\"t\"/>"
                        + "<window id=\"w2\" strategyRef=\"s\" lateData=\"t\"/>",
                "");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.duplicate-id", ex.getErrorCode().toString(),
                () -> "Two windows on one lateData tag must fail with ERR_STREAM_DUPLICATE_ID: "
                        + ex.getMessage());
    }

    @Test
    public void consumerEdgeFromNonHostFailsWithRefUnknown() {
        // window → agg is the host edge; the tag edge starts at the WINDOW node itself
        // instead of the host aggregate — must reject with REF_UNKNOWN.
        StreamModel model = parse(
                "<window id=\"w\" strategyRef=\"s\" lateData=\"t\"/>"
                        + "<aggregate id=\"agg\" aggregatorRef=\"a\"/>"
                        + "<map id=\"m1\"><source>event</source></map>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"w\" to=\"agg\"/>"
                        + "<edge id=\"e1\" from=\"w\" to=\"m1\" outputTag=\"t\"/>"
                        + "</edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString(),
                () -> "Tag edge from a non-host must fail with ERR_STREAM_REF_UNKNOWN: "
                        + ex.getMessage());
        assertTrue(ex.getMessage().contains("host"),
                () -> "Exception should name the host vertex rule: " + ex.getMessage());
    }

    @Test
    public void lateDataWithoutConsumerEdgeFailsFast() {
        StreamModel model = parse(
                "<window id=\"w\" strategyRef=\"s\" lateData=\"t\"/>"
                        + "<aggregate id=\"agg\" aggregatorRef=\"a\"/>",
                "<edges><edge id=\"e0\" from=\"w\" to=\"agg\"/></edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Unpaired lateData must fail with ERR_STREAM_INVALID_ARG: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("no consuming tag edge"),
                () -> "Exception should name the missing consumer edge: " + ex.getMessage());
    }

    @Test
    public void lateDataWithTwoConsumerEdgesFailsFast() {
        StreamModel model = parse(
                "<window id=\"w\" strategyRef=\"s\" lateData=\"t\"/>"
                        + "<aggregate id=\"agg\" aggregatorRef=\"a\"/>"
                        + "<map id=\"m1\"><source>event</source></map>"
                        + "<map id=\"m2\"><source>event</source></map>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"w\" to=\"agg\"/>"
                        + "<edge id=\"e1\" from=\"agg\" to=\"m1\" outputTag=\"t\"/>"
                        + "<edge id=\"e2\" from=\"agg\" to=\"m2\" outputTag=\"t\"/>"
                        + "</edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Two consumers on one lateData tag must fail with ERR_STREAM_INVALID_ARG: "
                        + ex.getMessage());
    }

    @Test
    public void lateDataWithoutHostFailsFast() {
        // window feeds a sink directly — no aggregate/reduce host vertex exists, so the
        // late-data channel has no producer operator; the first-host shortcut is forbidden.
        StreamModel model = parse(
                "<window id=\"w\" strategyRef=\"s\" lateData=\"t\"/>"
                        + "<sink id=\"out\" name=\"S\"/>",
                "<edges><edge id=\"e0\" from=\"w\" to=\"out\"/></edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Hostless lateData must fail with ERR_STREAM_INVALID_ARG: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("no aggregate/reduce downstream"),
                () -> "Exception should name the missing host: " + ex.getMessage());
    }

    @Test
    public void lateDataWithTwoHostsFailsFast() {
        StreamModel model = parse(
                "<window id=\"w\" strategyRef=\"s\" lateData=\"t\"/>"
                        + "<aggregate id=\"agg1\" aggregatorRef=\"a\"/>"
                        + "<aggregate id=\"agg2\" aggregatorRef=\"a\"/>"
                        + "<map id=\"m1\"><source>event</source></map>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"w\" to=\"agg1\"/>"
                        + "<edge id=\"e1\" from=\"w\" to=\"agg2\"/>"
                        + "<edge id=\"e2\" from=\"agg1\" to=\"m1\" outputTag=\"t\"/>"
                        + "</edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Two hosts on one lateData window must fail with ERR_STREAM_INVALID_ARG: "
                        + ex.getMessage());
        assertTrue(ex.getMessage().contains("multiple aggregate/reduce hosts"),
                () -> "Exception should name the multi-host ambiguity: " + ex.getMessage());
    }

    @Test
    public void lateDataTagEdgeToKeyedTargetFailsFast() {
        // The tag edge starts at the host but targets a keyBy — a tag channel can only
        // feed a single-input transform.
        StreamModel model = parse(
                "<window id=\"w\" strategyRef=\"s\" lateData=\"t\"/>"
                        + "<aggregate id=\"agg\" aggregatorRef=\"a\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>",
                "<edges>"
                        + "<edge id=\"e0\" from=\"w\" to=\"agg\"/>"
                        + "<edge id=\"e1\" from=\"agg\" to=\"k\" outputTag=\"t\"/>"
                        + "</edges>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Tag edge to a keyed target must fail with ERR_STREAM_INVALID_ARG: "
                        + ex.getMessage());
    }

    private static StreamModel parse(String transformsXml, String edgesXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-wi10-late-data\" version=\"1\">"
                + "<transforms>" + transformsXml + "</transforms>"
                + edgesXml
                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }
}
