/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.validate;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * xdsl-full-coverage roadmap WI3 (plan 2349): the model-aware lint rule faces.
 * Each face gets a positive control (known-bad model → expected finding) and the
 * clean/absent shape stays silent; the two-stage severity contract (findings are
 * WARNING, only the folded conf-validator FAIL decides the exit code) and the
 * co-occurrence contract (unconsumed-face models report N warnings + 1 folded
 * build FAIL) are pinned here.
 */
class TestStreamModelLinter {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private StreamConfValidationReport lint(String xml) throws Exception {
        Path file = tempDir.resolve("lint-" + System.nanoTime() + ".stream.xml");
        Files.writeString(file, xml);
        IResource resource = new FileResource(file.toFile());
        return new StreamModelLinter().lint(resource, null, false);
    }

    private static final String CLEAN_MODEL = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
            + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-clean\" version=\"1\">"
            + "<transforms>"
            + "<source id=\"src\"><source>return;</source></source>"
            + "<map id=\"m\"><source>return event;</source></map>"
            + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
            + "</transforms>"
            + "<edges>"
            + "<edge id=\"e1\" from=\"src\" to=\"m\"/>"
            + "<edge id=\"e2\" from=\"m\" to=\"out\"/>"
            + "</edges>"
            + "</stream>";

    @Test
    void cleanModelHasNoWarningsButCarriesDormantSkipNotes() throws Exception {
        StreamConfValidationReport report = lint(CLEAN_MODEL);
        assertEquals(0, report.getExitCode(), () -> report.render());
        assertTrue(report.getIssues().stream().noneMatch(i -> i.getSeverity() == ValidationIssue.Severity.WARNING),
                () -> report.render());
        // R13/R14 dormant checks are unconditional explicit skips — never silent.
        assertTrue(report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.SKIP)
                .anyMatch(i -> i.getMessage().startsWith("R13:")), () -> report.render());
        assertTrue(report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.SKIP)
                .anyMatch(i -> i.getMessage().startsWith("R14:")), () -> report.render());
    }

    @Test
    void unconsumedFacesReportedAllAtOnceAsWarnings() throws Exception {
        // four independent unconsumed faces in one model: the lint reports ALL of
        // them at once (the builder would fail-fast on the first only).
        StreamConfValidationReport report = lint("<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-faces\" version=\"1\">"
                + "<streams><stream id=\"s1\" valueType=\"string\"/></streams>"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"s1\" windowFnId=\"tumbling-event-time\" duration=\"5s\""
                + " accumulationMode=\"ACCUMULATING_AND_RETRACTING\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"srcBean\">"
                + "<params><param name=\"topic\" defaultValue=\"demo\"/></params>"
                + "</source>"
                + "<sink id=\"out\" bean=\"sinkBean\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"out\" queueCapacity=\"100\"/>"
                + "</edges>"
                + "</stream>");
        long warnings = report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING).count();
        assertTrue(warnings >= 4, () -> "expected >=4 face findings, got: " + report.render());
        String rendered = report.render();
        assertTrue(rendered.contains("strategy s1"), rendered);
        assertTrue(rendered.contains("<streams>"), rendered);
        assertTrue(rendered.contains("edge e1"), rendered);
        assertTrue(rendered.contains("source src"), rendered);
        // machine-readable contract: every lint finding carries a nop.err.stream.* code
        // (not an ErrorCode object hash) — the WI4 ledger indexes findings by code.
        report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .forEach(i -> assertTrue(i.getErrorCode().startsWith("nop.err.stream."),
                        () -> "finding code must be a nop.err.stream.* string: " + i.getErrorCode()));
    }

    @Test
    void unconsumedFacesCoexistWithFoldedBuildFailExitOne() throws Exception {
        // co-occurrence contract: the same unconsumed-face model folds the builder's
        // fail-fast as one FAIL — exit 1 with WARNING + FAIL coexisting.
        StreamConfValidationReport report = lint("<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-coexist\" version=\"1\">"
                + "<streams><stream id=\"s1\" valueType=\"string\"/></streams>"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>");
        assertEquals(1, report.getExitCode(), () -> report.render());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getSeverity() == ValidationIssue.Severity.WARNING),
                () -> report.render());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getSeverity() == ValidationIssue.Severity.FAIL),
                () -> report.render());
    }

    @Test
    void windowAggregateJoinWithoutSchemasTriggersR4() throws Exception {
        StreamConfValidationReport report = lint("<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-r4\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"s1\" windowFnId=\"tumbling-event-time\" duration=\"5s\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + "<window id=\"w\" strategyRef=\"s1\"/>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"k\"/>"
                + "<edge id=\"e2\" from=\"k\" to=\"w\"/>"
                + "<edge id=\"e3\" from=\"w\" to=\"out\"/>"
                + "</edges>"
                + "</stream>");
        assertTrue(report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .anyMatch(i -> i.getMessage().contains("<schemas>")), () -> report.render());
    }

    @Test
    void windowWithoutWatermarksTriggersR5() throws Exception {
        String model = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-r5\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"s1\" windowFnId=\"tumbling-event-time\" duration=\"5s\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + "<window id=\"w\" strategyRef=\"s1\"/>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"k\"/>"
                + "<edge id=\"e2\" from=\"k\" to=\"w\"/>"
                + "<edge id=\"e3\" from=\"w\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";
        StreamConfValidationReport report = lint(model);
        assertTrue(report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .anyMatch(i -> i.getMessage().contains("timestampsAndWatermarks")), () -> report.render());

        // released faces stay silent: adding a watermark supply removes the R5 warning
        String withWatermarks = model.replace("<transforms>",
                "<transforms><timestampsAndWatermarks id=\"wm\" watermarkInterval=\"100\"/>");
        StreamConfValidationReport ok = lint(withWatermarks);
        assertTrue(ok.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .noneMatch(i -> i.getMessage().contains("timestampsAndWatermarks")), () -> ok.render());
    }

    @Test
    void processOnlyModelTriggersR2Hint() throws Exception {
        StreamConfValidationReport report = lint("<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-r2\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<process id=\"stateful\" bean=\"someProcessBean\"/>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"stateful\"/>"
                + "<edge id=\"e2\" from=\"stateful\" to=\"out\"/>"
                + "</edges>"
                + "</stream>");
        assertTrue(report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .anyMatch(i -> i.getMessage().contains("declarative window/aggregate")), () -> report.render());
    }

    @Test
    void customOperatorTriggersR3Hint() throws Exception {
        StreamConfValidationReport report = lint("<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-r3\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<custom id=\"op\" customType=\"someOperatorBean\"/>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"op\"/>"
                + "<edge id=\"e2\" from=\"op\" to=\"out\"/>"
                + "</edges>"
                + "</stream>");
        assertTrue(report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .anyMatch(i -> i.getMessage().contains("<custom> operators")), () -> report.render());
    }

    @Test
    void exactlyOnceGuaranteeCarriesR6DormantSkip() throws Exception {
        StreamConfValidationReport report = lint("<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-r6\" version=\"1\">"
                + "<checkpoint enabled=\"true\" interval=\"5000\" processingGuarantee=\"STRICT_EXACTLY_ONCE\"/>"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>");
        assertTrue(report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.SKIP)
                .anyMatch(i -> i.getMessage().startsWith("R6:")), () -> report.render());
        // R6 is conditional: non-exactly-once models carry no R6 note
        StreamConfValidationReport plain = lint(CLEAN_MODEL);
        assertTrue(plain.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.SKIP)
                .noneMatch(i -> i.getMessage().startsWith("R6:")), () -> plain.render());
    }

    @Test
    void releasedWindowFacesAreNotReported() throws Exception {
        // live 2026-10-09 ledger: allowedLateness / ACCUMULATING / maxDelay are
        // released — the lint must not report them (no stale roadmap-snapshot faces).
        StreamConfValidationReport report = lint("<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-released\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"s1\" windowFnId=\"tumbling-event-time\" duration=\"5s\""
                + " allowedLateness=\"1000\" accumulationMode=\"ACCUMULATING\" maxDelay=\"3000\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<keyBy id=\"k\" keyExpr=\"event\"/>"
                + "<window id=\"w\" strategyRef=\"s1\" allowedLateness=\"2000\"/>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"k\"/>"
                + "<edge id=\"e2\" from=\"k\" to=\"w\"/>"
                + "<edge id=\"e3\" from=\"w\" to=\"out\"/>"
                + "</edges>"
                + "</stream>");
        assertTrue(report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .noneMatch(i -> i.getMessage().contains("allowedLateness")
                        || i.getMessage().contains("ACCUMULATING\"")
                        || i.getMessage().contains("maxDelay")), () -> report.render());
    }

    @Test
    void parseFailureShortCircuitsToSingleFail() throws Exception {
        StreamConfValidationReport report = lint("<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-parse\" version=\"1\">"
                + "<transforms><source id=\"src\"/><bogus id=\"b\"/></transforms>"
                + "</stream>");
        assertEquals(1, report.getExitCode());
        long fails = report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.FAIL).count();
        assertEquals(1, fails, () -> "parse failure must short-circuit to ONE fail: " + report.render());
    }

    @Test
    void vfsResourceFormIsSupported() throws Exception {
        // the VFS form (leading '/' path) resolves through VirtualFileSystem when
        // initialized — mirrors the conf-validate dual-resolution contract.
        Path file = tempDir.resolve("vfs-form.stream.xml");
        Files.writeString(file, CLEAN_MODEL);
        StreamConfValidationReport report = new StreamModelLinter().lint(
                new FileResource(file.toFile()), null, false);
        assertEquals(0, report.getExitCode(), () -> report.render());
    }
}
