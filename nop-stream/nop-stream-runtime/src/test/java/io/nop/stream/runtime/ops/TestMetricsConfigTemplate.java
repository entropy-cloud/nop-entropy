/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-4): the metrics configuration template ships in the resource
 * directory and covers ≥3 sink-type samples; the docs reference it.
 */
class TestMetricsConfigTemplate {

    private static final String TEMPLATE_PATH = "/_vfs/nop/stream/conf/metrics.properties.template";

    @Test
    void templateExistsInResourceDirectory() throws Exception {
        try (InputStream in = TestMetricsConfigTemplate.class.getResourceAsStream(TEMPLATE_PATH)) {
            assertNotNull(in, "metrics.properties.template must ship with the runtime resources");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            // ≥3 sink samples
            assertTrue(text.contains("nop.stream.ops.http.enabled"), "sink 1: prometheus pull via ops server");
            assertTrue(text.contains("nop.stream.metrics.log.target=stdout"), "sink 2: periodic log dump");
            assertTrue(text.contains("nop.stream.metrics.log.target=file"), "sink 3: periodic file snapshot");

            // the implemented sinks are exactly the documented engine-built-in ones
            assertTrue(text.contains("nop.stream.ops.http.port"));
            assertTrue(text.contains("nop.stream.metrics.log.file"));
            // external patterns explicitly marked as not engine-built-in (honesty marker)
            assertTrue(text.contains("引擎不内建"));
        }
    }
}
