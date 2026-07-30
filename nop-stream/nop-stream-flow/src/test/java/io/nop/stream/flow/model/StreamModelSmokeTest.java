/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.model;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StreamModelSmokeTest {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void testLoadStreamModel() {
        IResource resource = VirtualFileSystem.instance()
                .getResource("/nop/stream/test/test-smoke.stream.xml");
        assertTrue(resource.exists(), "Test stream resource must exist");

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromResource(resource);

        assertNotNull(model, "Parsed StreamModel must not be null");
        assertEquals("test-smoke-pipeline", model.getName());
        assertEquals(1L, model.getVersion());
        assertEquals(2, model.getParallelism());

        assertNotNull(model.getTransforms());
        assertEquals(3, model.getTransforms().size());
        assertTrue(model.hasTransforms());

        assertNotNull(model.getEdges());
        assertEquals(2, model.getEdges().size());
    }

    @Test
    void testStreamModelHasSourceTransform() {
        IResource resource = VirtualFileSystem.instance()
                .getResource("/nop/stream/test/test-smoke.stream.xml");

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromResource(resource);

        StreamTransformModel source = model.getTransform("src");
        assertNotNull(source, "Source transform 'src' must exist");
        assertEquals("TestSource", source.getName());

        StreamTransformModel sink = model.getTransform("out");
        assertNotNull(sink, "Sink transform 'out' must exist");
        assertEquals("TestSink", sink.getName());
    }

    @Test
    void testStreamModelEdgesReferenceValidTransforms() {
        IResource resource = VirtualFileSystem.instance()
                .getResource("/nop/stream/test/test-smoke.stream.xml");

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromResource(resource);

        for (StreamEdgeModel edge : model.getEdges()) {
            assertNotNull(model.getTransform(edge.getFrom()),
                    "Edge source '" + edge.getFrom() + "' must be a valid transform");
            assertNotNull(model.getTransform(edge.getTo()),
                    "Edge target '" + edge.getTo() + "' must be a valid transform");
        }
    }
}
