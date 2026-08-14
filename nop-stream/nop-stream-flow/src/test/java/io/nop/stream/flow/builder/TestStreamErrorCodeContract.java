/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.Arrays;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamDefinitionModel;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamMapModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamSinkModel;
import io.nop.stream.flow.model.StreamSourceModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-09-02 proof tests: DSL build errors surface as {@link StreamException} with a
 * non-empty, programmatically-usable {@code ERR_STREAM_*} error code, so callers can
 * distinguish configuration errors without parsing exception messages, and the message
 * retains the declaration context (ids/attributes) for diagnosis.
 *
 * <p>Models are built programmatically (the XDSL parser and the generated model's
 * keyed lists already reject duplicate ids at parse/model time — see
 * {@link #duplicateIdIsRejectedByModelLayer} — so the builder's duplicate checks are
 * defensive; the reachable builder-level error paths are exercised here).
 */
public class TestStreamErrorCodeContract {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @Test
    public void notYetImplementedRegistryCarriesErrorCode() {
        StreamModel model = new StreamModel();
        model.setName("registry-model");
        model.setStreams(Arrays.asList(new StreamDefinitionModel()));

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertNotNull(ex.getErrorCode(), "getErrorCode() must be non-null for a StreamException");
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("<streams>"), () -> ex.getMessage());
    }

    @Test
    public void cyclicGraphCarriesErrorCode() {
        StreamModel model = new StreamModel();
        model.setName("cycle-model");
        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        StreamMapModel map = new StreamMapModel();
        map.setId("m");
        StreamSinkModel sink = new StreamSinkModel();
        sink.setId("out");
        model.setTransforms(Arrays.asList(src, map, sink));
        // src -> m -> src forms a cycle; out is unreachable
        model.setEdges(Arrays.asList(
                edge("e0", "src", "m"),
                edge("e1", "m", "src"),
                edge("e2", "src", "out")));

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertNotNull(ex.getErrorCode());
        assertEquals("nop.err.stream.cyclic-job-graph", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("cycle"), () -> ex.getMessage());
    }

    @Test
    public void duplicateIdIsRejectedByModelLayer() {
        // Duplicate transform/edge ids cannot reach the builder: the XDSL parser
        // (xdef:key-attr) and the generated model's KeyedList reject them with an
        // error code before the builder runs. This proves the duplicate-id defect
        // class fails loud with a code at the earliest boundary, not silently.
        StreamModel model = new StreamModel();
        model.setName("dup-model");
        StreamSourceModel src = new StreamSourceModel();
        src.setId("dup");
        StreamMapModel map = new StreamMapModel();
        map.setId("dup");

        NopException ex = assertThrows(NopException.class,
                () -> model.setTransforms(Arrays.asList(src, map)));
        assertNotNull(ex.getErrorCode());
        assertTrue(ex.getErrorCode().toString().contains("duplicate"),
                () -> "Expected duplicate-key error code, got: " + ex.getErrorCode());
    }

    @Test
    public void errorCodeIsProgrammaticallyUsableByCallers() {
        // End-to-end: a caller catching the build error can switch on the error code
        // without parsing the message (the two-tier error-handling contract).
        StreamModel model = new StreamModel();
        model.setName("e2e-error-model");
        StreamSourceModel src = new StreamSourceModel();
        src.setId("src");
        src.setBean("srcFn");
        StreamSourceModel src2 = new StreamSourceModel();
        src2.setId("src2");
        src2.setBean("srcFn");
        StreamMapModel map = new StreamMapModel();
        map.setId("m");
        map.setBean("mapFn");
        model.setTransforms(Arrays.asList(src, src2, map));
        // two upstream edges into m -> builder-level upstream-count rejection
        model.setEdges(Arrays.asList(edge("e0", "src", "m"), edge("e1", "src2", "m")));

        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("mapFn", (io.nop.stream.core.common.functions.MapFunction<Object, Object>) x -> x);

        try {
            StreamModelDslBuilder.of(model, resolver).build();
            throw new AssertionError("build() must fail on multi-upstream transform");
        } catch (StreamException ex) {
            assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString(),
                    () -> "Unexpected error: " + ex);
            assertTrue(ex.toString().contains("2"),
                    () -> "Message must retain upstream count: " + ex);
        }
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static StreamEdgeModel edge(String id, String from, String to) {
        StreamEdgeModel e = new StreamEdgeModel();
        e.setId(id);
        e.setFrom(from);
        e.setTo(to);
        return e;
    }
}
