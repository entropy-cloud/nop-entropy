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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.connector.ConnectivityProbeOutcome;
import io.nop.stream.core.connector.StreamConnectivityProber;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleSourceSplit;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.testing.IntegerSourceFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-13) dry-run driver tests: the dispatch order (capability interface →
 * FLIP-27 Source → 2PC base contract → explicit skip) is really invoked at runtime
 * (wiring verification — recording implementations assert the calls happened), a
 * failing probe surfaces a typed error code instead of a silent pass, and endpoints
 * without any probe contract become explicit skip items that do not fail the run.
 */
public class TestStreamConnectivityDryRun {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // dispatch order / wiring verification
    // ------------------------------------------------------------------

    @Test
    public void connectivityCheckableDispatchIsInvoked() {
        RecordingCheckable source = new RecordingCheckable();
        RecordingCheckable sink = new RecordingCheckable();
        StreamConfValidationReport report = dryRun(source, sink);
        assertTrue(report.isPassed(), () -> report.render());
        assertEquals(1, source.checkCount.get(), "checkConnection must be invoked on the source endpoint");
        assertEquals(1, sink.checkCount.get(), "checkConnection must be invoked on the sink endpoint");
        assertTrue(report.getProbeLog().stream().anyMatch(l -> l.contains("PASS")),
                () -> "expected PASS probe log lines: " + report.render());
        assertTrue(report.getIssues().isEmpty(), () -> report.render());
    }

    @Test
    public void flip27SourceProbeDispatchesEnumeratorStart() {
        // XDSL source beans resolve to SourceFunction, so the FLIP-27 dispatch is
        // reached via the connector registry world / direct prober use (H-1 path).
        RecordingSplitSource source = new RecordingSplitSource(false);
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("flip27-source", source);
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));
        assertEquals(1, source.startCount.get(), "enumerator start() must be invoked (H-1 probe path)");
    }

    @Test
    public void flip27SourceProbeFailsExplicitlyOnMissingInput() {
        RecordingSplitSource source = new RecordingSplitSource(true);
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("flip27-source", source);
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, outcome.getStatus());
        assertEquals("nop.err.stream.connectivity-check-failed", outcome.getErrorCode());
        assertTrue(outcome.getDetail().contains("directory missing"), () -> outcome.getDetail());
    }

    @Test
    public void thirdPartyTwoPhaseCommitSinkFallsBackToBaseContract() {
        RecordingTwoPhaseCommitSink sink = new RecordingTwoPhaseCommitSink();
        StreamConfValidationReport report = dryRun(new RecordingCheckable(), sink);
        assertTrue(report.isPassed(), () -> report.render());
        assertEquals(1, sink.beginCount.get(), "beginTransaction must be invoked (H-5 base probe)");
        assertEquals(1, sink.rollbackCount.get(), "rollback must be invoked (H-5 base probe)");
        assertTrue(report.getProbeLog().stream().anyMatch(
                l -> l.contains("sink 'out'") && l.contains("PASS")));
    }

    // ------------------------------------------------------------------
    // explicit failure / skip semantics
    // ------------------------------------------------------------------

    @Test
    public void failingProbeProducesTypedErrorCodeNotSilentPass() {
        FailingCheckable sink = new FailingCheckable();
        StreamConfValidationReport report = dryRun(new RecordingCheckable(), sink);
        assertEquals(1, report.getExitCode());
        ValidationIssue issue = report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.FAIL)
                .findFirst().orElseThrow();
        assertEquals(StreamConfValidator.LAYER_CONNECTIVITY, issue.getLayer());
        assertEquals("nop.err.stream.connectivity-check-failed", issue.getErrorCode());
        assertTrue(issue.getTarget().contains("sink 'out'"), "target must name the endpoint: " + issue.getTarget());
        assertTrue(issue.getTarget().contains("sinkBean"), "target must name the bean: " + issue.getTarget());
        assertTrue(issue.getMessage().contains("db unreachable"), () -> issue.getMessage());
    }

    @Test
    public void endpointsWithoutProbeContractAreExplicitSkips() {
        StreamConfValidationReport report = dryRun(new IntegerSourceFunction(), new PlainSink());
        assertTrue(report.isPassed(), "explicit skips must not fail the run: " + report.render());
        assertEquals(0, report.getExitCode());
        List<ValidationIssue> skips = report.getIssues();
        assertEquals(2, skips.size(), () -> report.render());
        assertTrue(skips.stream().allMatch(i -> i.getSeverity() == ValidationIssue.Severity.SKIP));
        assertTrue(skips.stream().allMatch(
                i -> "nop.err.stream.connectivity-not-supported".equals(i.getErrorCode())));
    }

    @Test
    public void proberDirectContract() {
        // Direct prober-level checks pinning the outcome object semantics.
        assertEquals(ConnectivityProbeOutcome.Status.PASS,
                StreamConnectivityProber.probe("t", new RecordingCheckable()).getStatus());
        assertEquals(ConnectivityProbeOutcome.Status.SKIP,
                StreamConnectivityProber.probe("t", new PlainSink()).getStatus());
        ConnectivityProbeOutcome fail = StreamConnectivityProber.probe("t", new FailingCheckable());
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, fail.getStatus());
        assertEquals("nop.err.stream.connectivity-check-failed", fail.getErrorCode());
        ConnectivityProbeOutcome nullEndpoint = StreamConnectivityProber.probe("t", null);
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, nullEndpoint.getStatus());
        assertNotNull(nullEndpoint.getDetail());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static final String JOB = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
            + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"dry-run\" version=\"1\">"
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

    private StreamConfValidationReport dryRun(Object source, Object sink) {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcBean", source);
        resolver.register("mapBean", (io.nop.stream.core.common.functions.MapFunction<Object, Object>) v -> v);
        resolver.register("sinkBean", sink);
        return new StreamConfValidator().validateStream(resource(JOB), resolver, true);
    }

    private static IResource resource(String xml) {
        return new ByteArrayResource("/test/dry-run-inline.stream.xml",
                xml.getBytes(StandardCharsets.UTF_8), System.currentTimeMillis());
    }

    private static final class RecordingCheckable implements ConnectivityCheckable,
            io.nop.stream.core.common.functions.source.SourceFunction<Object>,
            io.nop.stream.core.common.functions.SinkFunction<Object> {
        final AtomicInteger checkCount = new AtomicInteger();

        @Override
        public void checkConnection() {
            checkCount.incrementAndGet();
        }

        @Override
        public void run(SourceContext<Object> ctx) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public void consume(Object value) {
        }
    }

    private static final class FailingCheckable implements ConnectivityCheckable,
            io.nop.stream.core.common.functions.SinkFunction<Object> {
        @Override
        public void checkConnection() {
            throw new IllegalStateException("db unreachable");
        }

        @Override
        public void consume(Object value) {
        }
    }

    private static final class PlainSink implements io.nop.stream.core.common.functions.SinkFunction<Object> {
        @Override
        public void consume(Object value) {
        }
    }

    private static final class RecordingTwoPhaseCommitSink extends TwoPhaseCommitSinkFunction<Object> {
        final AtomicInteger beginCount = new AtomicInteger();
        final AtomicInteger rollbackCount = new AtomicInteger();

        @Override
        public void beginTransaction() {
            beginCount.incrementAndGet();
        }

        @Override
        public void invoke(Object value) {
        }

        @Override
        public void preCommit(long checkpointId) {
        }

        @Override
        public void commit(long checkpointId) {
        }

        @Override
        public void rollback() {
            rollbackCount.incrementAndGet();
        }
    }

    /** FLIP-27 source recording whether the enumerator's start() ran (the H-1 probe). */
    private static final class RecordingSplitSource
            implements Source<String, SimpleSourceSplit, Void> {

        final AtomicInteger startCount = new AtomicInteger();
        private final boolean failOnStart;

        RecordingSplitSource(boolean failOnStart) {
            this.failOnStart = failOnStart;
        }

        @Override
        public SplitEnumerator<SimpleSourceSplit, Void> createEnumerator() {
            return new RecordingEnumerator();
        }

        @Override
        public SplitEnumerator<SimpleSourceSplit, Void> restoreEnumerator(Void checkpointState) {
            return new RecordingEnumerator();
        }

        @Override
        public SourceReader<String, SimpleSourceSplit> createReader(SourceReaderContext readerContext) {
            throw new UnsupportedOperationException("not part of the probe path");
        }

        @Override
        public SimpleVersionedSerializer<Void> getEnumeratorStateSerializer() {
            throw new UnsupportedOperationException("not part of the probe path");
        }

        @Override
        public SimpleVersionedSerializer<SimpleSourceSplit> getSplitSerializer() {
            throw new UnsupportedOperationException("not part of the probe path");
        }

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        private class RecordingEnumerator implements SplitEnumerator<SimpleSourceSplit, Void> {
            @Override
            public void start(SplitEnumeratorContext<SimpleSourceSplit> context) {
                startCount.incrementAndGet();
                if (failOnStart) {
                    throw new IllegalStateException("directory missing");
                }
            }

            @Override
            public void addReader(int subtaskIndex) {
            }

            @Override
            public void handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) {
            }

            @Override
            public Void snapshotState(long checkpointId) {
                return null;
            }

            @Override
            public void restoreState(Void state) {
            }

            @Override
            public void close() {
            }
        }
    }
}
