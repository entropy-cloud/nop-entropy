/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
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
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the fail-fast guarantees in {@link StreamModelDslBuilder}: every
 * {@code stream.xdef}-declared element that is not yet wired must throw
 * {@link StreamException} (carrying an {@code ERR_STREAM_*} code) rather than
 * being silently ignored.
 *
 * <p>Phase 1 covered base transforms (source/map/flatMap/filter/keyBy/sink) and
 * top-level registries. Phase 2 implemented the remaining transform types
 * (window/aggregate/reduce/process/cep/timestampsAndWatermarks/custom) — their
 * wiring is covered by {@link TestAdvancedTransforms}. This class retains the
 * transform that still fails-fast due to missing core runtime APIs
 * ({@code <union>} orphan shape) plus all top-level registry / callback fail-fast
 * checks. The {@code <sideOutput>} NOT_IMPLEMENTED gap was released by WI8 Phase 2
 * (plan 2332): its remaining fail-fast is the tag pairing diagnosis, asserted below.
 *
 * <p>Each test uses an inline {@code .stream.xml} snippet that exercises one unbuilt
 * branch. They parse via the real XDSL parser (so the snippet is verified against
 * the xdef), then attempt to build the environment — the builder must reject it loudly.
 */
public class TestStreamModelDslBuilderFailFast {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void unionTransformWithoutUpstreamFailsFast() {
        // WI6: <union> now builds a real multi-input merge; an orphan union with no
        // upstream edge still fails fast, but with ERR_STREAM_INVALID_ARG (not the
        // former ERR_STREAM_NOT_IMPLEMENTED runtime-gap code).
        StreamModel model = parseInline("<union id=\"u\"/>", "");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Orphan union must fail with ERR_STREAM_INVALID_ARG: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("union"),
                "Exception should mention union: " + ex.getMessage());
    }

    @Test
    public void sideOutputWithoutConsumerEdgeFailsFast() {
        // WI8 Phase 2 (plan 2332): <sideOutput> is now buildable, but a declared tag
        // without a consuming <edge outputTag> still fails fast — with the pairing
        // error (ERR_STREAM_INVALID_ARG), not the former NOT_IMPLEMENTED code.
        StreamModel model = parseInline("<sideOutput id=\"so\" tag=\"late\"/>", "");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Unpaired sideOutput must fail with ERR_STREAM_INVALID_ARG: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("no consuming tag edge"),
                "Exception should name the missing consumer edge: " + ex.getMessage());
    }

    @Test
    public void streamsRegistryRemovedFromSchema() {
        // WI12 (plan 2358): the <streams> registry was REMOVED from stream.xdef — a
        // leftover declaration is rejected at the xdef parse layer (undefined child
        // node), not by a builder fail-fast.
        // The rejection happens INSIDE the parser (xdef schema validation), so the
        // parse call itself is the code under test.
        io.nop.api.core.exceptions.NopException ex = assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> parseInline("", "<streams><stream id=\"s1\" valueType=\"string\"/></streams>"));
        assertEquals("nop.err.xlang.xdsl.undefined-child-node", ex.getErrorCode().toString(),
                () -> "Parse layer must reject the removed face: " + ex);
        assertTrue(ex.toString().contains("streams"),
                () -> "Parse-layer rejection should name the removed face: " + ex);
    }

    @Test
    public void sideInputsRegistryFailsFast() {
        // WI11 (plan 2357): <sideInputs> is released — the NOT_IMPLEMENTED gap is
        // gone. The legacy pin shape (broadcast omitted → xdef default false) now
        // maps to the broadcast-only-true reject; an EMPTY sideInputs declaration is
        // legal. Full contract coverage lives in TestWi11SideInputDeclaration.
        StreamModel empty = parseInline("", "<sideInputs/>"
                + "<transforms><source id=\"s\" bean=\"src\"/><sink id=\"o\" bean=\"snk\"/></transforms>"
                + "<edges><edge id=\"e0\" from=\"s\" to=\"o\"/></edges>");
        StreamModelDslBuilder.of(empty, new InMemoryBeanFunctionResolver()
                .register("src", new NoopSource())
                .register("snk", (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                })).build();

        StreamModel legacyDefault = parseInline("",
                "<sideInputs><sideInput id=\"si1\" from=\"x\" to=\"y\"/></sideInputs>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(legacyDefault, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Legacy broadcast default (false) must fail with ERR_STREAM_INVALID_ARG: "
                        + ex.getMessage());
        assertTrue(ex.getMessage().contains("broadcast=true"),
                "Exception should name the broadcast=true-only contract: " + ex.getMessage());
    }

    @Test
    public void schemasRegistryIsConsumedByBuilder() {
        // WI8b: <schemas> 有消费者——合法受管类型名可 build，未知类型首错即抛
        StreamModel model = parseInline("",
                "<schemas><schema id=\"s1\"><fields>"
                        + "<field name=\"a\" type=\"string\"/>"
                        + "<field name=\"b\" type=\"bigint\" nullable=\"true\"/>"
                        + "</fields></schema></schemas>");
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver());
        builder.build();

        StreamModel bad = parseInline("",
                "<schemas><schema id=\"s1\"><fields><field name=\"a\" type=\"varchar2\"/></fields></schema></schemas>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(bad, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                () -> "Unknown managed type must fail with ERR_STREAM_INVALID_ARG: " + ex.getMessage());
    }

    @Test
    public void codersRegistryFailsFast() {
        assertTopLevelRegistryFailsFast(
                "<coders><coder id=\"c1\" className=\"java.lang.String\"/></coders>",
                "<coders>");
    }

    // WI1 (plan 2347): contract-ledger nails — every remaining registry/callback
    // face from failFastOnUnsupportedRegistries gets its own build-time pin so the
    // release work items (WI12/WI13) can retire them one by one.

    @Test
    public void lifecycleCallbacksFailsFast() {
        // xpl-fn bodies are function BODY statements compiled in the xdef signature's
        // parameter scope (streamCtx) — a lambda literal is rejected at parse time by
        // the XLang ERR_XPL_FN_BODY_IS_FUNCTION guard, so the pin uses a trivial
        // expression body and asserts the builder's lifecycle-callback fail-fast.
        assertTopLevelRegistryFailsFast("<onStart>streamCtx;</onStart>", "lifecycle");
    }

    private void assertFailFast(String transformXml, String expectedToken) {
        StreamModel model = parseInline(transformXml, "");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString(),
                () -> "Exception should carry ERR_STREAM_NOT_IMPLEMENTED: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(expectedToken),
                "Exception should mention " + expectedToken + ": " + ex.getMessage());
    }

    private void assertTopLevelRegistryFailsFast(String topLevelXml, String expectedToken) {
        StreamModel model = parseInline("", topLevelXml);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString(),
                () -> "Exception should carry ERR_STREAM_NOT_IMPLEMENTED: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(expectedToken),
                "Exception should mention " + expectedToken + ": " + ex.getMessage());
    }

    /** Emit-nothing source for contract tests that only need a buildable graph. */
    public static class NoopSource
            implements SourceFunction<Object> {
        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext ctx) {
        }

        @Override
        public void cancel() {
        }
    }

    private StreamModel parseInline(String transformXml, String topLevelXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-fail-fast\" version=\"1\">"
                + topLevelXml
                + (transformXml.isEmpty() ? "" : ("<transforms>" + transformXml + "</transforms>"))
                + "</stream>";

        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        // Touch to ensure the schema loads (defensive — the resource exists in the xdefs module)
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }
}
