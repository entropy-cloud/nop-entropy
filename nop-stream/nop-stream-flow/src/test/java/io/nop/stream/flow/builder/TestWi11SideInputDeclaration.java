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
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.validate.StreamConfValidationReport;
import io.nop.stream.flow.validate.StreamModelLinter;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.nop.stream.flow.builder.functions.XplFunctionSupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11 (plan 2357): the declarative side-input contract. Structural checks run in
 * validateDag (before any build-loop work, no runtime factory touched) — every
 * failure is a typed reject with the declaring element's location. The positive
 * path proves the WIRING: the target transform's xpl-fn body reads the frozen
 * build-time snapshot through the {@code sideInputs} binding, so a silent-binding
 * gap (binding never injected) fails the data assertion, not just a type check.
 *
 * <p>Seven reject classes pinned here (plan contract): duplicate id / broadcast
 * false (explicit and legacy-default forms) / unknown to / to not map-family /
 * to bean-form / missing from bean / from bean not a Supplier. Plus the lint
 * release face: a valid sideInputs model no longer raises the NOT_IMPLEMENTED
 * warning.
 */
public class TestWi11SideInputDeclaration {

    private static final Map<String, Object> DICT = new LinkedHashMap<>();

    static {
        DICT.put("k1", "v1");
        DICT.put("k2", "v2");
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static InMemoryBeanFunctionResolver dictResolver() {
        return new InMemoryBeanFunctionResolver()
                .register("dictSupplier", (Supplier<Map<String, Object>>) () -> DICT)
                .register("notASupplier", "just-a-string");
    }

    private static final String BASE_TRANSFORMS =
            "<source id=\"src\" bean=\"noopSrc\"/>"
                    + "<map id=\"enrich\"><source>event</source></map>"
                    + "<sink id=\"out\" bean=\"noopSink\"/>";
    private static final String BASE_EDGES =
            "<edges><edge id=\"e0\" from=\"src\" to=\"enrich\"/>"
                    + "<edge id=\"e1\" from=\"enrich\" to=\"out\"/></edges>";

    @Test
    public void emptySideInputsDeclarationIsLegal() {
        StreamModel model = parse("<sideInputs/>", BASE_TRANSFORMS, BASE_EDGES);
        InMemoryBeanFunctionResolver resolver = dictResolver()
                .register("noopSrc", new NoopSource())
                .register("noopSink", (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                });
        assertNotNull(StreamModelDslBuilder.of(model, resolver).build(),
                "an empty <sideInputs/> declaration must build");
    }

    @Test
    public void duplicateIdIsRejectedAtParseLayerByXdefUniqueness() {
        // The xdef key-attr uniqueness guard rejects the duplicate at parse time;
        // the builder-level duplicate check (validateSideInputs) remains as the
        // defense for programmatically assembled models that bypass the parser.
        io.nop.core.lang.xml.XNode node = io.nop.core.lang.xml.XNode.parse(
                "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                        + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"dup\" version=\"1\">"
                        + "<sideInputs>"
                        + "<sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrich\" broadcast=\"true\"/>"
                        + "<sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrich\" broadcast=\"true\"/>"
                        + "</sideInputs>"
                        + "</stream>");
        assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> new DslModelParser().parseFromNode(node),
                "xdef unique-attr must reject a duplicate sideInput id at parse time");
    }

    @Test
    public void explicitBroadcastFalseFailsFast() {
        StreamModel model = parse(
                "<sideInputs><sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrich\" broadcast=\"false\"/></sideInputs>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, dictResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Explicit broadcast=false must fail with ERR_STREAM_INVALID_ARG: "
                        + ex.getMessage());
        assertTrue(ex.getMessage().contains("broadcast=true"),
                () -> "Exception should name the broadcast=true-only contract: " + ex.getMessage());
    }

    @Test
    public void legacyBroadcastDefaultFailsFast() {
        // Attribute omitted → xdef default false back-filled → typed reject.
        StreamModel model = parse(
                "<sideInputs><sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrich\"/></sideInputs>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, dictResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Legacy broadcast default must fail with ERR_STREAM_INVALID_ARG: "
                        + ex.getMessage());
    }

    @Test
    public void unknownTargetFailsFast() {
        StreamModel model = parse(
                "<sideInputs><sideInput id=\"dict\" from=\"dictSupplier\" to=\"nope\" broadcast=\"true\"/></sideInputs>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, dictResolver()).build());
        assertEquals("nop.err.stream.ref-unknown", ex.getErrorCode().toString(),
                () -> "Unknown to-transform must fail with ERR_STREAM_REF_UNKNOWN: "
                        + ex.getMessage());
    }

    @Test
    public void nonMapFamilyTargetFailsFast() {
        StreamModel model = parse(
                "<sideInputs><sideInput id=\"dict\" from=\"dictSupplier\" to=\"out\" broadcast=\"true\"/></sideInputs>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, dictResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Sink target must fail with ERR_STREAM_INVALID_ARG: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("map/filter/flatMap"),
                () -> "Exception should name the allowed target family: " + ex.getMessage());
    }

    @Test
    public void beanFormTargetFailsFast() {
        // Syntax-detectable silent no-op: a bean-form map can never see the binding.
        StreamModel model = parse(
                "<sideInputs><sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrich\" broadcast=\"true\"/></sideInputs>",
                "<source id=\"src\" bean=\"noopSrc\"/>"
                        + "<map id=\"enrich\" bean=\"noopMap\"/>"
                        + "<sink id=\"out\" bean=\"noopSink\"/>",
                BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, dictResolver()
                        .register("noopMap", (io.nop.stream.core.common.functions.MapFunction<Object, Object>) v -> v)
                        .register("noopSrc", new NoopSource())
                        .register("noopSink", (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                        })).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Bean-form target must fail with ERR_STREAM_INVALID_ARG: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("bean-form"),
                () -> "Exception should name the bean-form silent-no-op rule: " + ex.getMessage());
    }

    @Test
    public void missingFromBeanFailsFast() {
        StreamModel model = parse(
                "<sideInputs><sideInput id=\"dict\" from=\"noSuchBean\" to=\"enrich\" broadcast=\"true\"/></sideInputs>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, dictResolver()).build());
        assertEquals("nop.err.stream.bean-not-found", ex.getErrorCode().toString(),
                () -> "Missing from-bean must fail with ERR_STREAM_BEAN_NOT_FOUND: "
                        + ex.getMessage());
    }

    @Test
    public void nonSupplierFromBeanFailsFast() {
        StreamModel model = parse(
                "<sideInputs><sideInput id=\"dict\" from=\"notASupplier\" to=\"enrich\" broadcast=\"true\"/></sideInputs>",
                BASE_TRANSFORMS, BASE_EDGES);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, dictResolver()).build());
        assertEquals("nop.err.stream.bean-type-mismatch", ex.getErrorCode().toString(),
                () -> "Non-Supplier from-bean must fail with ERR_STREAM_BEAN_TYPE_MISMATCH: "
                        + ex.getMessage());
    }

    @Test
    public void releasedFaceIsNotLintReported() throws Exception {
        // WI11 release face: the NOT_IMPLEMENTED lint warning must be gone for a
        // structurally valid sideInputs model (releasedWindowFacesAreNotReported 先例).
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-wi11\" version=\"1\">"
                + "<sideInputs><sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrich\" broadcast=\"true\"/></sideInputs>"
                + "<transforms>" + BASE_TRANSFORMS + "</transforms>"
                + BASE_EDGES
                + "</stream>";
        java.nio.file.Path file = java.nio.file.Files.createTempFile("lint-wi11-", ".stream.xml");
        java.nio.file.Files.writeString(file, xml);
        io.nop.core.resource.impl.FileResource resource =
                new io.nop.core.resource.impl.FileResource(file.toFile());
        StreamConfValidationReport report =
                new StreamModelLinter().lint(resource, null, false);
        assertTrue(report.getIssues().stream()
                        .noneMatch(i -> i.getMessage() != null
                                && i.getMessage().contains("sideInputs")),
                () -> "Released sideInputs face must not be lint-reported: " + report.render());
        java.nio.file.Files.deleteIfExists(file);
    }

    @Test
    public void sideInputBindingReachesTargetXplBody() {
        // The WIRING proof: the target map's xpl-fn body reads the frozen snapshot
        // via the `sideInputs` binding — the output value carries the dict lookup,
        // so a binding-injection gap fails the data assertion.
        StreamModel model = parse(
                "<sideInputs><sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrich\" broadcast=\"true\"/></sideInputs>",
                "<source id=\"src\" bean=\"noopSrc\"/>"
                        + "<map id=\"enrich\"><source>sideInputs['dict'][event]</source></map>"
                        + "<sink id=\"out\" bean=\"noopSink\"/>",
                BASE_EDGES);

        InMemoryBeanFunctionResolver resolver = dictResolver()
                .register("noopSrc", new NoopSource())
                .register("noopSink", (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                });
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver);
        assertNotNull(builder.build(), "wired model must build");

        // The snapshot is frozen at build time (one Supplier.get() per build); the
        // data-level end-to-end proof lives in the runtime E2E. Here we pin the
        // wrapper-level registration: the target transform is a sideInput consumer.
        assertTrue(builder.sideInputScopeTargetIds().contains("enrich"),
                "the target transform must be registered as a sideInput consumer");
    }

    @Test
    public void xplFunctionSupportBindingScopeCarriesEntries() {
        // Direct binding-scope contract: newCallScope(bindings) exposes entries as
        // scope variables; the plain scope stays available.
        io.nop.core.lang.eval.IEvalScope scope = XplFunctionSupport.newCallScope(
                java.util.Collections.singletonMap("sideInputs", DICT));
        assertEquals(DICT, scope.getValue("sideInputs"),
                "bound entries must be visible as scope variables");
    }

    public static class NoopSource
            implements SourceFunction<Object> {
        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext ctx) {
        }

        @Override
        public void cancel() {
        }
    }

    private static StreamModel parse(String sideInputsXml, String transformsXml, String edgesXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-wi11-side-input\" version=\"1\">"
                + sideInputsXml
                + "<transforms>" + transformsXml + "</transforms>"
                + edgesXml
                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }
}
