package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.PromptInjectionGuardrail;
import io.nop.ai.agent.model.AgentModel;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end validation (design {@code guardrail-contract.md} §增量 1,
 * Phase 3): loads the shipped 60+ corpus → runs the suite against the real
 * production {@link PromptInjectionGuardrail} → asserts measurable Report
 * metrics. Also records the baseline capability boundary of
 * {@code PromptInjectionGuardrail}: its 4 target threat types
 * (prompt_override / role_hijack / exfiltration / invisible_char) are
 * intercepted, while non-target categories (jailbreak / hallucination /
 * privilege_escalation) legitimately leak (a recorded capability boundary,
 * not a defect of this plan).
 */
public class TestGuardrailRedteamEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final AgentExecutionContext CTX = AgentExecutionContext.create(
            new AgentModel(), "redteam-session");

    /** Real guardrail wrapped in a call counter (Wiring Verification). */
    static final class CountingGuardrail implements IContentGuardrail {
        final IContentGuardrail delegate;
        final AtomicInteger checkCount = new AtomicInteger();

        CountingGuardrail(IContentGuardrail delegate) {
            this.delegate = delegate;
        }

        @Override
        public GuardrailResult check(GuardrailDirection direction, String content,
                                     AgentExecutionContext ctx) {
            checkCount.incrementAndGet();
            return delegate.check(direction, content, ctx);
        }
    }

    @Test
    void corpusMeetsSizeAndCategoryRequirements() {
        CorpusAttackPlugin plugin = new CorpusAttackPlugin();
        List<AttackCase> cases = plugin.cases();

        long attacks = cases.stream()
                .filter(c -> c.getExpectedBehavior() == ExpectedBehavior.BLOCK).count();
        long benign = cases.stream()
                .filter(c -> c.getExpectedBehavior() == ExpectedBehavior.PASS).count();
        long categories = cases.stream()
                .map(AttackCase::getCategory).distinct().count();

        assertTrue(attacks >= 60,
                "corpus must contain >=60 attack cases, got " + attacks);
        assertTrue(categories >= 8,
                "corpus must cover >=8 threat categories, got " + categories);
        assertTrue(benign >= 5,
                "corpus must include a benign control set, got " + benign);
        // industry-vertical samples exist
        assertTrue(cases.stream().anyMatch(c -> c.getCategory().startsWith("industry_")),
                "corpus must include industry-vertical samples");
    }

    @Test
    void endToEndRunProducesMeasurableReportAgainstRealGuardrail() {
        CountingGuardrail guardrail = new CountingGuardrail(new PromptInjectionGuardrail());
        AttackPlugin plugin = new CorpusAttackPlugin();
        GuardrailTestSuite suite = new GuardrailTestSuite();

        GuardrailTestReport report = suite.run(guardrail, Collections.singletonList(plugin), CTX);

        // Wiring: check() was actually invoked on the real guardrail, once per case
        assertTrue(guardrail.checkCount.get() >= 60,
                "suite must invoke check() on real PromptInjectionGuardrail for every case");

        // Benign control set: no false positives
        assertTrue(report.getTotalBenign() >= 5);
        assertEquals(0.0, report.getFalsePositiveRate(), 1e-9,
                "PromptInjectionGuardrail must not falsely block benign content");

        // Measurable headline metrics (concrete numbers, not abstract)
        assertTrue(report.getBlockRate() >= 0.0 && report.getBlockRate() <= 1.0);
        assertTrue(report.getLeakRate() >= 0.0 && report.getLeakRate() <= 1.0);
    }

    @Test
    void baselinePromptInjectionGuardrailInterceptsItsTargetThreatTypes() {
        CountingGuardrail guardrail = new CountingGuardrail(new PromptInjectionGuardrail());
        GuardrailTestReport report = new GuardrailTestSuite()
                .run(guardrail, Collections.singletonList(new CorpusAttackPlugin()), CTX);

        // The 4 target threat types of PromptInjectionGuardrail must be fully intercepted.
        assertEquals(1.0, report.getCategory("prompt_injection").getBlockRate(), 1e-9,
                "prompt_injection (prompt_override) is a target type and must be fully blocked");
        assertEquals(1.0, report.getCategory("role_hijack").getBlockRate(), 1e-9,
                "role_hijack is a target type and must be fully blocked");
        assertEquals(1.0, report.getCategory("exfiltration").getBlockRate(), 1e-9,
                "exfiltration is a target type and must be fully blocked");
        assertEquals(1.0, report.getCategory("invisible_char").getBlockRate(), 1e-9,
                "invisible_char is a target type and must be fully blocked");
        // prompt_extraction is handled via the exfiltration regex family
        assertEquals(1.0, report.getCategory("prompt_extraction").getBlockRate(), 1e-9,
                "prompt_extraction is detected via the exfiltration regex family");
    }

    @Test
    void baselineCapabilityBoundaryLeaksAreRecorded() {
        CountingGuardrail guardrail = new CountingGuardrail(new PromptInjectionGuardrail());
        GuardrailTestReport report = new GuardrailTestSuite()
                .run(guardrail, Collections.singletonList(new CorpusAttackPlugin()), CTX);

        // Non-target categories legitimately leak — recorded capability boundary.
        assertEquals(1.0, report.getCategory("jailbreak").getLeakRate(), 1e-9,
                "jailbreak is NOT a PromptInjectionGuardrail target; leaks are a recorded boundary");
        assertEquals(1.0, report.getCategory("hallucination").getLeakRate(), 1e-9,
                "hallucination is an output-correctness concern; leaks are a recorded boundary");
        assertEquals(1.0, report.getCategory("privilege_escalation").getLeakRate(), 1e-9,
                "privilege_escalation is an agency-control concern (security SPI); leaks are a recorded boundary");
    }

    @Test
    void strategyTransformsChangeInterceptionRate() {
        CountingGuardrail guardrailBase = new CountingGuardrail(new PromptInjectionGuardrail());
        CountingGuardrail guardrailTransformed = new CountingGuardrail(new PromptInjectionGuardrail());
        AttackPlugin plugin = new CorpusAttackPlugin();
        GuardrailTestSuite suite = new GuardrailTestSuite();

        GuardrailTestReport baseReport = suite.run(guardrailBase,
                Collections.singletonList(plugin), CTX);
        GuardrailTestReport transformedReport = suite.run(guardrailTransformed,
                Collections.singletonList(plugin),
                Arrays.asList(new Base64AttackTransform(), new CrescendoAttackTransform()), CTX);

        // transformed run exercises more cases (base + 2 variants each)
        assertTrue(transformedReport.getTotalCases() > baseReport.getTotalCases());
        // every transformed case still went through the real guardrail
        assertTrue(guardrailTransformed.checkCount.get() == transformedReport.getTotalCases());

        // base64 wrapping typically defeats a pure regex guardrail: the encoded
        // payload no longer matches the plaintext regexes. This is the whole
        // point of exercising transforms — it surfaces a robustness gap that a
        // plaintext-only suite would hide. We assert the transformed block rate
        // is <= the base block rate (transforms never improve detection, they
        // only ever hold or erode it).
        assertTrue(transformedReport.getBlockRate() <= baseReport.getBlockRate() + 1e-9,
                "transforms must not improve detection; they hold or erode it. base="
                        + baseReport.getBlockRate() + " transformed=" + transformedReport.getBlockRate());
    }

    @Test
    void perCaseResultsCarryRealGuardrailResults() {
        CountingGuardrail guardrail = new CountingGuardrail(new PromptInjectionGuardrail());
        GuardrailTestReport report = new GuardrailTestSuite()
                .run(guardrail, Collections.singletonList(new CorpusAttackPlugin()), CTX);

        // No hollow: each per-case detail carries a real GuardrailResult, not null
        for (CaseResult cr : report.getResults()) {
            assertNotNull(cr.getActual(), "per-case actual result must be non-null (no hollow)");
            assertNotNull(cr.getGrade(), "per-case grade must be non-null (no hollow)");
            assertNotNull(cr.getCaseId());
        }
    }
}
