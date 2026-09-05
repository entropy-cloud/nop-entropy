/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.validate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorDirection;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.StreamConnectorCatalog;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.connector.registry.StreamConnectorRegistry;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.testing.IntegerSourceFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests of the conf-validate core (item 20 / P-REQ-14, D7 layer contract):
 * legal job passes without starting it; per-class illegal jobs (model layer /
 * construction layer / missing required bean input) each produce a structured issue
 * with the option name and a non-zero exit code; connector mode reports field-level
 * errors naming the offending param.
 */
public class TestStreamConfValidator {

    private static final String VALID_JOB = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
            + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"conf-validate-ok\" version=\"1\">"
            + "<transforms>"
            + "<source id=\"src\" bean=\"srcBean\"/>"
            + "<map id=\"m\" bean=\"mapBean\"/>"
            + "<sink id=\"out\" bean=\"sinkBean\"/>"
            + "</transforms>"
            + "<edges>"
            + "<edge id=\"e1\" from=\"src\" to=\"m\"/>"
            + "<edge id=\"e2\" from=\"m\" to=\"out\"/>"
            + "</edges>"
            + "</stream>";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // stream mode (XDSL, D2 form 1: programmatic resolver)
    // ------------------------------------------------------------------

    @Test
    public void legalJobPassesWithoutStartingIt() {
        StreamConfValidationReport report = new StreamConfValidator()
                .validateStream(resource(VALID_JOB), fullResolver(), false);
        assertTrue(report.isPassed(), () -> "expected pass, got: " + report.render());
        assertEquals(0, report.getExitCode());
    }

    @Test
    public void layer1UnknownElementIsReportedWithElementName() {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"bad-model\" version=\"1\">"
                + "<transforms><source id=\"src\" bean=\"srcBean\"/>"
                + "<notATransform id=\"x\"/></transforms></stream>";
        StreamConfValidationReport report = new StreamConfValidator()
                .validateStream(resource(xml), fullResolver(), false);
        assertEquals(1, report.getExitCode());
        ValidationIssue issue = report.getIssues().get(0);
        assertEquals(StreamConfValidator.LAYER_MODEL, issue.getLayer());
        assertNotNull(issue.getMessage());
        assertFalse(issue.getMessage().isBlank());
        // Item 29 (Phase 3): the layer-1 issue keeps the parse error's location
        // anchor instead of dropping it.
        assertNotNull(issue.getSourceLocation(), () -> "layer-1 issue must keep file:line: " + issue.describe());
        assertTrue(issue.getSourceLocation().contains("conf-validate-inline.stream.xml"),
                () -> "anchor must point at the resource: " + issue.getSourceLocation());
        assertTrue(issue.describe().contains("@" + " " + issue.getSourceLocation().split(":1")[0])
                        || issue.describe().contains(issue.getSourceLocation()),
                () -> "describe() must render the anchor: " + issue.describe());
    }

    @Test
    public void layer2MissingBeanIsReportedWithOptionName() {
        InMemoryBeanFunctionResolver partial = new InMemoryBeanFunctionResolver();
        partial.register("srcBean", new IntegerSourceFunction());
        partial.register("sinkBean", new RecordingSinkFunction());
        // mapBean intentionally NOT registered
        StreamConfValidationReport report = new StreamConfValidator()
                .validateStream(resource(VALID_JOB), partial, false);
        assertEquals(1, report.getExitCode());
        ValidationIssue issue = report.getIssues().get(0);
        assertEquals(StreamConfValidator.LAYER_CONSTRUCTION, issue.getLayer());
        assertEquals("nop.err.stream.bean-not-found", issue.getErrorCode());
        assertEquals("mapBean", issue.getParamName(), "P-REQ-14: error must carry the option name");
        // Item 29 (Phase 3): the layer-2 issue carries the declaring transform
        // element's file:line anchor (bean errors anchor on the <map> element).
        assertNotNull(issue.getSourceLocation(), () -> "layer-2 issue must keep file:line: " + issue.describe());
        assertTrue(issue.getSourceLocation().contains("conf-validate-inline.stream.xml"),
                () -> "anchor must point at the resource: " + issue.getSourceLocation());
        assertTrue(issue.describe().contains(issue.getSourceLocation()),
                () -> "describe() must render the anchor: " + issue.describe());
    }

    @Test
    public void layer2MissingRequiredBodyIsReported() {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"no-body\" version=\"1\">"
                + "<transforms><source id=\"src\"/></transforms></stream>";
        StreamConfValidationReport report = new StreamConfValidator()
                .validateStream(resource(xml), new InMemoryBeanFunctionResolver(), false);
        assertEquals(1, report.getExitCode());
        ValidationIssue issue = report.getIssues().get(0);
        assertEquals(StreamConfValidator.LAYER_CONSTRUCTION, issue.getLayer());
        assertEquals("nop.err.stream.required-body", issue.getErrorCode());
    }

    @Test
    public void connectFlagProbesEndpointsAndSkipsUnprobeableFamilies() {
        // Phase 3 wiring: --connect now probes. IntegerSourceFunction / plain sinks
        // implement no probe contract → explicit SKIP items, exit still 0 (D7).
        StreamConfValidationReport report = new StreamConfValidator()
                .validateStream(resource(VALID_JOB), fullResolver(), true);
        assertTrue(report.isPassed(), () -> report.render());
        assertEquals(0, report.getExitCode());
        assertFalse(report.getIssues().isEmpty(), "unprobeable endpoints must produce explicit skip items");
        assertTrue(report.getIssues().stream().allMatch(
                        i -> i.getSeverity() == ValidationIssue.Severity.SKIP),
                () -> "expected only skip items: " + report.render());
    }

    // ------------------------------------------------------------------
    // connector mode (SPI registry world, D6)
    // ------------------------------------------------------------------

    @Test
    public void connectorModeValidParamsPass() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SINK, "fake-sink", Map.of("target", "t1"), catalog(), false);
        assertTrue(report.isPassed(), () -> report.render());
        assertEquals(0, report.getExitCode());
    }

    @Test
    public void connectorModeUnknownParamIsReportedWithParamName() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SINK, "fake-sink",
                Map.of("target", "t1", "unknownOption", "x"), catalog(), false);
        assertEquals(1, report.getExitCode());
        ValidationIssue issue = report.getIssues().get(0);
        assertEquals("nop.err.stream.connector-param-unknown", issue.getErrorCode());
        assertEquals("unknownOption", issue.getParamName());
        assertTrue(issue.getMessage().contains("unknownOption"),
                "message must name the offending param: " + issue.getMessage());
        // Item 29 (Phase 3): connector-mode issues have no model origin — no fake
        // file:line anchor is produced.
        org.junit.jupiter.api.Assertions.assertNull(issue.getSourceLocation(),
                "connector-mode issue must not carry a fake location");
    }

    @Test
    public void connectorModeMissingRequiredParamIsReportedWithParamName() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SINK, "fake-sink", Map.of(), catalog(), false);
        assertEquals(1, report.getExitCode());
        ValidationIssue issue = report.getIssues().get(0);
        assertEquals("nop.err.stream.connector-param-required", issue.getErrorCode());
        assertEquals("target", issue.getParamName());
    }

    @Test
    public void connectorModeUnknownTypeFailsExplicitly() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SINK, "no-such-type", Map.of(), catalog(), false);
        assertEquals(1, report.getExitCode());
        ValidationIssue issue = report.getIssues().get(0);
        assertEquals("nop.err.stream.connector-type-not-found", issue.getErrorCode());
    }

    @Test
    public void connectorModeConnectProbesConstructedEndpoint() {
        // Phase 3 wiring: --connect constructs the endpoint and probes it. The fake
        // sink implements no probe contract → explicit SKIP, exit 0 (D7 contract).
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SINK, "fake-sink", Map.of("target", "t1"), catalog(), true);
        assertTrue(report.isPassed(), () -> report.render());
        assertEquals(0, report.getExitCode());
        assertEquals(1, report.getIssues().size());
        ValidationIssue issue = report.getIssues().get(0);
        assertEquals(StreamConfValidator.LAYER_CONNECTIVITY, issue.getLayer());
        assertEquals("nop.err.stream.connectivity-not-supported", issue.getErrorCode());
        assertEquals(ValidationIssue.Severity.SKIP, issue.getSeverity());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static IResource resource(String xml) {
        return new ByteArrayResource("/test/conf-validate-inline.stream.xml",
                xml.getBytes(StandardCharsets.UTF_8), System.currentTimeMillis());
    }

    private static InMemoryBeanFunctionResolver fullResolver() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcBean", new IntegerSourceFunction());
        resolver.register("mapBean", (io.nop.stream.core.common.functions.MapFunction<Object, Object>) v -> v);
        resolver.register("sinkBean", new RecordingSinkFunction());
        return resolver;
    }

    private static StreamConnectorCatalog catalog() {
        return StreamConnectorCatalog.of(StreamConnectorRegistry.of(List.of(new FakeSinkConnectorFactory())));
    }

    /** Minimal sink for bean resolution and registry construction probing. */
    private static final class RecordingSinkFunction implements SinkFunction<Object> {
        final java.util.List<Object> consumed = new java.util.ArrayList<>();

        @Override
        public void consume(Object value) {
            consumed.add(value);
        }
    }

    /** Minimal SPI factory so connector mode is exercised without a connector module. */
    private static final class FakeSinkConnectorFactory implements IStreamSinkFactory {

        static final String TYPE = "fake-sink";

        private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
                .sink(TYPE, RecordingSinkFunction.class.getName())
                .sinkConsistency(io.nop.stream.core.common.functions.sink.SinkConsistencyCapability.AT_LEAST_ONCE)
                .parallelism(ConnectorParallelism.PARALLEL)
                .recoverySemantic(ConnectorRecoverySemantic.NONE)
                .params(List.of(ConnectorParamDescriptor.required("target",
                        io.nop.stream.core.connector.registry.ConnectorParamKind.STRING, "target name")))
                .build();

        @Override
        public String getTypeName() {
            return TYPE;
        }

        @Override
        public ConnectorCapabilityDescriptor describeCapabilities() {
            return DESCRIPTOR;
        }

        @Override
        public SinkFunction<?> createSink(StreamConnectorConfig config) {
            config.requireString("target");
            return new RecordingSinkFunction();
        }
    }
}
