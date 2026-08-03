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
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the fail-fast guarantees in {@link StreamModelDslBuilder}: every
 * {@code stream.xdef}-declared element that is not yet wired must throw
 * {@link UnsupportedOperationException} rather than being silently ignored.
 *
 * <p>Phase 1 covered base transforms (source/map/flatMap/filter/keyBy/sink) and
 * top-level registries. Phase 2 implemented the remaining transform types
 * (window/aggregate/reduce/process/cep/timestampsAndWatermarks/custom) — their
 * wiring is covered by {@link TestAdvancedTransforms}. This class retains the
 * two transforms that still fail-fast due to missing core runtime APIs
 * ({@code <union>}, {@code <sideOutput>}) plus all top-level registry / callback
 * fail-fast checks.
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
    public void unionTransformThrowsUnsupportedOperationException() {
        assertFailFast("<union id=\"u\"/>", "union");
    }

    @Test
    public void sideOutputTransformThrowsUnsupportedOperationException() {
        assertFailFast("<sideOutput id=\"so\" tag=\"late\"/>", "sideOutput");
    }

    @Test
    public void streamsRegistryFailsFast() {
        assertTopLevelRegistryFailsFast("<streams><stream id=\"s1\" valueType=\"string\"/></streams>",
                "<streams>");
    }

    @Test
    public void sideInputsRegistryFailsFast() {
        assertTopLevelRegistryFailsFast(
                "<sideInputs><sideInput id=\"si1\" from=\"x\" to=\"y\"/></sideInputs>",
                "<sideInputs>");
    }

    @Test
    public void environmentsRegistryFailsFast() {
        assertTopLevelRegistryFailsFast(
                "<environments><environment name=\"prod\"/></environments>",
                "<environments>");
    }

    @Test
    public void schemasRegistryFailsFast() {
        assertTopLevelRegistryFailsFast(
                "<schemas><schema id=\"s1\"><fields><field name=\"a\" type=\"string\"/></fields></schema></schemas>",
                "<schemas>");
    }

    @Test
    public void codersRegistryFailsFast() {
        assertTopLevelRegistryFailsFast(
                "<coders><coder id=\"c1\" className=\"java.lang.String\"/></coders>",
                "<coders>");
    }

    private void assertFailFast(String transformXml, String expectedToken) {
        StreamModel model = parseInline(transformXml, "");
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertTrue(ex.getMessage().contains(expectedToken),
                "Exception should mention " + expectedToken + ": " + ex.getMessage());
    }

    private void assertTopLevelRegistryFailsFast(String topLevelXml, String expectedToken) {
        StreamModel model = parseInline("", topLevelXml);
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertTrue(ex.getMessage().contains(expectedToken),
                "Exception should mention " + expectedToken + ": " + ex.getMessage());
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
