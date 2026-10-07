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
import io.nop.stream.core.connector.registry.ConnectorMaturity;
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
        ValidationIssue issue = findByCode(report, "nop.err.stream.connector-param-unknown");
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
        ValidationIssue issue = findByCode(report, "nop.err.stream.connector-param-required");
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
        List<ValidationIssue> connectivity = report.getIssues().stream()
                .filter(i -> i.getLayer() == StreamConfValidator.LAYER_CONNECTIVITY).toList();
        assertEquals(1, connectivity.size());
        ValidationIssue issue = connectivity.get(0);
        assertEquals("nop.err.stream.connectivity-not-supported", issue.getErrorCode());
        assertEquals(ValidationIssue.Severity.SKIP, issue.getSeverity());
    }

    // ------------------------------------------------------------------
    // WI4 (plan 2329): OptionRule-style value rules + maturity warning
    // ------------------------------------------------------------------

    @Test
    public void connectorModeIntKindMismatchIsReported() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SOURCE, "fake-source",
                Map.of("target", "t1", "retries", "not-a-number"), catalogWithRules(), false);
        assertEquals(1, report.getExitCode());
        assertTrue(report.getIssues().stream().anyMatch(
                        i -> "nop.err.stream.connector-param-type-mismatch".equals(i.getErrorCode())
                                && "retries".equals(i.getParamName())),
                () -> report.render());
    }

    @Test
    public void connectorModeAllowedValuesViolationIsReported() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SOURCE, "fake-source",
                Map.of("target", "t1", "mode", "bogus"), catalogWithRules(), false);
        assertEquals(1, report.getExitCode());
        assertTrue(report.getIssues().stream().anyMatch(
                        i -> "nop.err.stream.connector-param-value-disallowed".equals(i.getErrorCode())
                                && "mode".equals(i.getParamName())),
                () -> report.render());
    }

    @Test
    public void connectorModeSensitiveValueIsMaskedEverywhere() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SOURCE, "fake-source",
                Map.of("target", "t1", "secret", "lowercase-violation"), catalogWithRules(), false);
        assertEquals(1, report.getExitCode());
        String rendered = report.render();
        assertTrue(report.getIssues().stream().anyMatch(
                        i -> "nop.err.stream.connector-param-pattern-mismatch".equals(i.getErrorCode())
                                && "secret".equals(i.getParamName())),
                () -> report.render());
        assertFalse(rendered.contains("lowercase-violation"),
                "sensitive value must never reach the report text: " + rendered);
        assertTrue(rendered.contains("withheld (sensitive)"),
                "the mask note must be present: " + rendered);
    }

    @Test
    public void connectorModeLegalRuleValuesPass() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SOURCE, "fake-source",
                Map.of("target", "t1", "retries", "3", "mode", "a", "secret", "ABCDEF"),
                catalogWithRules(), false);
        assertTrue(report.isPassed(), () -> report.render());
    }

    @Test
    public void connectorModeExperimentalMaturityWarnsWithoutFailing() {
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SOURCE, "fake-source", Map.of("target", "t1"),
                catalogWithRules(), false);
        assertTrue(report.isPassed(), () -> report.render());
        assertTrue(report.getIssues().stream().anyMatch(
                        i -> i.getSeverity() == ValidationIssue.Severity.WARNING
                                && "nop.err.stream.connector-maturity-experimental".equals(i.getErrorCode())),
                () -> "EXPERIMENTAL endpoint must produce a warning: " + report.render());
    }

    @Test
    public void connectorModeProductionMaturityProducesNoWarning() {
        // Mechanism check: a factory explicitly declaring PRODUCTION gets no warning
        // (the fake sink is a test double — its PRODUCTION declaration here exercises
        // the builder path; real-factory grading against the semantic-strength
        // criterion is pinned by the runtime discovery test).
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SINK, "fake-sink-prod", Map.of("target", "t1"),
                catalogWithProdSink(), false);
        assertTrue(report.isPassed(), () -> report.render());
        assertTrue(report.getIssues().stream().noneMatch(
                        i -> i.getSeverity() == ValidationIssue.Severity.WARNING),
                () -> "PRODUCTION endpoint must not warn: " + report.render());
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

    private static ValidationIssue findByCode(StreamConfValidationReport report, String errorCode) {
        return report.getIssues().stream()
                .filter(i -> errorCode.equals(i.getErrorCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing issue " + errorCode + " in: " + report.render()));
    }

    private static StreamConnectorCatalog catalogWithProdSink() {
        return StreamConnectorCatalog.of(StreamConnectorRegistry.of(List.of(new ProdSinkConnectorFactory())));
    }

    /** Same shape as {@link FakeSinkConnectorFactory} but explicitly PRODUCTION. */
    private static final class ProdSinkConnectorFactory implements IStreamSinkFactory {
        private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
                .sink("fake-sink-prod", RecordingSinkFunction.class.getName())
                .sinkConsistency(io.nop.stream.core.common.functions.sink.SinkConsistencyCapability.AT_LEAST_ONCE)
                .parallelism(ConnectorParallelism.PARALLEL)
                .recoverySemantic(ConnectorRecoverySemantic.NONE)
                .params(List.of(ConnectorParamDescriptor.required("target",
                        io.nop.stream.core.connector.registry.ConnectorParamKind.STRING, "target name")))
                .maturity(ConnectorMaturity.PRODUCTION)
                .build();

        @Override
        public String getTypeName() {
            return "fake-sink-prod";
        }

        @Override
        public ConnectorCapabilityDescriptor describeCapabilities() {
            return DESCRIPTOR;
        }

        @Override
        public io.nop.stream.core.common.functions.SinkFunction<?> createSink(StreamConnectorConfig config) {
            return new RecordingSinkFunction();
        }
    }

    private static StreamConnectorCatalog catalogWithRules() {
        return StreamConnectorCatalog.of(StreamConnectorRegistry.of(
                List.of(new FakeSinkConnectorFactory(), new FakeSourceConnectorFactory())));
    }

    /**
     * WI4: source factory exercising the value rules (INT kind / allowedValues /
     * sensitive+pattern) and the EXPERIMENTAL maturity default surface.
     */
    private static final class FakeSourceConnectorFactory implements io.nop.stream.core.connector.registry.IStreamSplitSourceFactory {

        static final String TYPE = "fake-source";

        private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
                .source(TYPE, RecordingSource.class.getName())
                .sourceConsistency(io.nop.stream.core.common.functions.source.SourceConsistencyCapability.AT_LEAST_ONCE)
                .parallelism(ConnectorParallelism.PARALLEL)
                .recoverySemantic(ConnectorRecoverySemantic.SPLIT_CURSOR_CHECKPOINT)
                .params(List.of(
                        ConnectorParamDescriptor.required("target",
                                io.nop.stream.core.connector.registry.ConnectorParamKind.STRING, "target name"),
                        ConnectorParamDescriptor.optional("retries",
                                io.nop.stream.core.connector.registry.ConnectorParamKind.INT, "retry count"),
                        ConnectorParamDescriptor.optional("mode",
                                io.nop.stream.core.connector.registry.ConnectorParamKind.STRING, "mode",
                                false, List.of("a", "b"), null),
                        ConnectorParamDescriptor.optional("secret",
                                io.nop.stream.core.connector.registry.ConnectorParamKind.STRING, "token",
                                true, null, "^[A-Z]+$")))
                // maturity not declared: exercises the EXPERIMENTAL default
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
        public io.nop.stream.core.source.Source<?, ?, ?> createSource(StreamConnectorConfig config) {
            return new RecordingSource();
        }
    }

    /** Marker split for the fake source. */
    private static final class FakeSplit implements io.nop.stream.core.source.SourceSplit {
        @Override
        public String splitId() {
            return "fake-0";
        }
    }

    /** Minimal bounded FLIP-27 source standing in for the fake factory. */
    private static final class RecordingSource
            implements io.nop.stream.core.source.Source<Object, FakeSplit, Object> {
        @Override
        public io.nop.stream.core.source.Boundedness getBoundedness() {
            return io.nop.stream.core.source.Boundedness.BOUNDED;
        }

        @Override
        public io.nop.stream.core.source.SplitEnumerator<FakeSplit, Object> createEnumerator() {
            throw new UnsupportedOperationException("never enumerated in validation tests");
        }

        @Override
        public io.nop.stream.core.source.SplitEnumerator<FakeSplit, Object> restoreEnumerator(
                Object checkpointState) {
            throw new UnsupportedOperationException("never restored in validation tests");
        }

        @Override
        public io.nop.stream.core.source.SourceReader<Object, FakeSplit> createReader(
                io.nop.stream.core.source.SourceReaderContext context) {
            throw new UnsupportedOperationException("never read in validation tests");
        }

        @Override
        public io.nop.stream.core.source.SimpleVersionedSerializer<FakeSplit> getSplitSerializer() {
            throw new UnsupportedOperationException("never serialized in validation tests");
        }

        @Override
        public io.nop.stream.core.source.SimpleVersionedSerializer<Object> getEnumeratorStateSerializer() {
            throw new UnsupportedOperationException("never serialized in validation tests");
        }
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
