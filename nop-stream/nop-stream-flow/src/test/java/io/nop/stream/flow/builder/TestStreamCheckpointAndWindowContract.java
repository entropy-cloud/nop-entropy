/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.Map;
import java.util.stream.Stream;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.TestSourceFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-XDSL-6 focused tests: the six previously-ignored checkpoint fields
 * (barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/storageType/jobId/pipelineId/
 * storageConfig) must be mapped to the core {@link CheckpointConfig} (with the non-default
 * test pinned to the xdef defaults — a declared 0 must not be dropped by a {@code > 0}
 * guard), and window strategy/node-level triggerId/allowedLateness/accumulationMode
 * non-default values must fail fast with the transform id and attribute name.
 */
public class TestStreamCheckpointAndWindowContract {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ----------------------------------------------------------------
    // checkpoint six-field contract
    // ----------------------------------------------------------------

    @Test
    public void checkpointSixFieldsAreMappedToEnvConfig() {
        StreamModel model = parseInline(
                "<checkpoint enabled=\"true\" interval=\"1000\" "
                        + "barrierAlignmentTimeout=\"15000\" maxConsecutiveCheckpointFailures=\"2\" "
                        + "storageType=\"rocksdb\" jobId=\"job-1\" pipelineId=\"pipe-1\">"
                        + "<storageConfig>"
                        + "<entry key=\"path\" value=\"/tmp/cp\"/>"
                        + "<entry key=\"region\" value=\"cn-east\"/>"
                        + "</storageConfig>"
                        + "</checkpoint>",
                "<source id=\"src\" bean=\"srcFn\"/>");

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver()).build();
        CheckpointConfig cfg = env.getCheckpointConfig();

        assertEquals(15000L, cfg.getBarrierAlignmentTimeout());
        assertEquals(2, cfg.getMaxConsecutiveCheckpointFailures());
        assertEquals("rocksdb", cfg.getStorageType());
        assertEquals("job-1", cfg.getJobId());
        assertEquals("pipe-1", cfg.getPipelineId());
        Map<String, String> storageConfig = cfg.getStorageConfig();
        assertEquals("/tmp/cp", storageConfig.get("path"));
        assertEquals("cn-east", storageConfig.get("region"));
    }

    @Test
    public void declaredZeroValuesAreNotDropped() {
        // The non-default test must be `!= xdef default`, never `> 0` — a declared 0
        // would otherwise be silently ignored.
        StreamModel model = parseInline(
                "<checkpoint enabled=\"true\" interval=\"1000\" "
                        + "barrierAlignmentTimeout=\"0\" maxConsecutiveCheckpointFailures=\"0\"/>",
                "<source id=\"src\" bean=\"srcFn\"/>");

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver()).build();
        CheckpointConfig cfg = env.getCheckpointConfig();

        assertEquals(0L, cfg.getBarrierAlignmentTimeout());
        assertEquals(0, cfg.getMaxConsecutiveCheckpointFailures());
    }

    @Test
    public void undeclaredCheckpointLeavesCoreDefaults() {
        StreamModel model = parseInline("");

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver()).build();
        CheckpointConfig cfg = env.getCheckpointConfig();

        assertEquals(CheckpointConfig.DEFAULT_BARRIER_ALIGNMENT_TIMEOUT,
                cfg.getBarrierAlignmentTimeout());
        assertEquals(CheckpointConfig.DEFAULT_MAX_CONSECUTIVE_CHECKPOINT_FAILURES,
                cfg.getMaxConsecutiveCheckpointFailures());
        assertEquals("local", cfg.getStorageType());
        // core seeds jobId with a random UUID and pipelineId with "1"; only
        // DSL-declared values are mapped over these defaults
        assertTrue(cfg.getStorageConfig().isEmpty());
        assertEquals("1", cfg.getPipelineId());
    }

    // ----------------------------------------------------------------
    // window strategy / node-level attribute contract
    // ----------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("windowFailFastCases")
    public void nonDefaultWindowAttrsFailFast(String strategyXml, String windowChildXml,
                                              String expectedAttrName) {
        StreamModel model = parseInline(
                "<windowingStrategies><strategy strategyId=\"g\" windowFnId=\"global\" "
                        + strategyXml + "/></windowingStrategies>",
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                        + "<window id=\"w\" strategyRef=\"g\">" + windowChildXml + "</window>");

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, resolver()).build());
        assertEquals("nop.err.stream.window-attr-unsupported", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("w"),
                () -> "Error must locate transform 'w': " + ex.getMessage());
        assertTrue(ex.getMessage().contains(expectedAttrName),
                () -> "Error must name attribute '" + expectedAttrName + "': " + ex.getMessage());
    }

    static Stream<Arguments> windowFailFastCases() {
        return Stream.of(
                Arguments.of("triggerId=\"t\"", "", "triggerId"),
                Arguments.of("allowedLateness=\"1\"", "", "allowedLateness"),
                Arguments.of("accumulationMode=\"ACCUMULATING\"", "", "accumulationMode"),
                Arguments.of("", "<allowedLateness>1</allowedLateness>", "allowedLateness"),
                Arguments.of("", "<triggerId>t</triggerId>", "triggerId"));
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private static InMemoryBeanFunctionResolver resolver() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new TestSourceFunction());
        return resolver;
    }

    private StreamModel parseInline(String topLevelXml, String transformsXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-checkpoint-window-contract\" version=\"1\">"
                + topLevelXml
                + "<transforms>" + transformsXml + "</transforms>"
                + edgeChain(transformsXml)
                + "</stream>";

        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    private StreamModel parseInline(String transformsXml) {
        return parseInline("", transformsXml);
    }

    /**
     * Auto-generate FORWARD edges so the transforms form a linear chain in declaration
     * order (e.g. src→k→w).
     */
    private static String edgeChain(String transformsXml) {
        java.util.List<String> ids = java.util.stream.Stream.of(transformsXml.split("(?=<[a-zA-Z])"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    int idStart = s.indexOf("id=\"");
                    if (idStart < 0) {
                        return null;
                    }
                    int idEnd = s.indexOf("\"", idStart + 4);
                    return s.substring(idStart + 4, idEnd);
                })
                .collect(java.util.stream.Collectors.toList());

        StringBuilder edges = new StringBuilder("<edges>");
        int edgeIdx = 0;
        for (int i = 0; i + 1 < ids.size(); i++) {
            if (ids.get(i) == null || ids.get(i + 1) == null) {
                continue;
            }
            edges.append("<edge id=\"e").append(edgeIdx++).append("\" from=\"")
                    .append(ids.get(i)).append("\" to=\"").append(ids.get(i + 1))
                    .append("\" partition=\"FORWARD\"/>");
        }
        edges.append("</edges>");
        return edges.toString();
    }
}
