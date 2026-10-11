package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleSetModel;
import io.nop.lint.core.suppress.ExemptionFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ruleset exemption matrix (roadmap item 27 Phase 1, plan
 * 2026-09-24-0050-1): ruleset loading with inline rules and exemptions, the
 * fail-closed rejections (missing reason, bad glob, unknown exemption rule
 * id, dual-source duplicate id), the CheckRunner-level filtering with the
 * exempted counter and the exit-code data flow, the stateless-predicate
 * behavior under the fix flow, and the RuleTester isolation red line
 * (exemptions live at the CheckRunner layer only).
 */
public class TestRuleSetExemptions {

    private static final String RULES_PREFIX = "/test/lint/cli-rules-exempt";

    @TempDir
    Path dir;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private RuleSetLoader.LoadedRuleSet load() {
        return new RuleSetLoader().loadRuleSet(RULES_PREFIX);
    }

    // ==================== ruleset loading ====================

    @Test
    public void rulesetLoadsInlineRulesAndExemptions() {
        RuleSetLoader.LoadedRuleSet loaded = load();

        assertEquals(2, loaded.rulesByLanguage().get("java").size(),
                "the inline ruleset rule and the standalone rule share one run");
        assertEquals(1, loaded.exemptions().size());
        RuleSetModel.Exemption exemption = loaded.exemptions().get(0);
        assertEquals("demo/exempt-target", exemption.ruleId());
        assertEquals("legacy module, refactor planned", exemption.reason());
        assertEquals(List.of("**/legacy/**"), exemption.files());
    }

    @Test
    public void missingReasonFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new RuleSetLoader().loadRuleSet("/test/lint/cli-rules-exempt-badreason"));
        assertTrue(ex.getMessage().contains("reason"), ex.getMessage());
    }

    @Test
    public void uncompilableGlobFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new RuleSetLoader().loadRuleSet("/test/lint/cli-rules-exempt-badglob"));
        assertTrue(ex.getMessage().contains("glob"), ex.getMessage());
    }

    @Test
    public void duplicateRuleIdAcrossSourcesFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new RuleSetLoader().loadRuleSet("/test/lint/cli-rules-exempt-dupid"));
        assertTrue(ex.getMessage().contains("demo/dup-rule"), ex.getMessage());
        assertTrue(ex.getMessage().contains("twice"), ex.getMessage());
    }

    @Test
    public void unknownExemptionRuleIdFailsClosed() {
        // a ruleset referencing a rule nobody in the prefix declares — the
        // delayed validation must still reject it after the full id set is
        // collected
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new RuleSetLoader().loadRuleSet("/test/lint/cli-rules-exempt-orphan"));
        assertTrue(ex.getMessage().contains("demo/never-declared"), ex.getMessage());
        assertTrue(ex.getMessage().contains("does not declare"), ex.getMessage());
    }

    // ==================== ExemptionFilter ====================

    @Test
    public void filterMatchesGlobAndNormalizesPath() {
        ExemptionFilter filter = ExemptionFilter.of(load().exemptions());

        assertTrue(filter.suppresses("demo/exempt-target", dir.resolve("a/b/legacy/X.java")),
                "the legacy glob matches nested paths");
        assertTrue(filter.suppresses("demo/exempt-target", dir.resolve("legacy/Y.java")),
                "the ** prefix matches zero leading segments");
        assertTrue(filter.suppresses("demo/exempt-target",
                        dir.resolve("legacy" + java.io.File.separator + "Y.java")),
                "platform separators normalize to the canonical form");
        assertEquals(false, filter.suppresses("demo/exempt-target", dir.resolve("src/ok/Y.java")),
                "a non-legacy path is not exempted");
        assertEquals(false, filter.suppresses("demo/exempt-inline", dir.resolve("legacy/Y.java")),
                "another rule is not exempted by this exemption");
    }

    @Test
    public void emptyFilterSuppressesNothing() {
        assertEquals(false, ExemptionFilter.of(List.of()).suppresses("demo/x", dir.resolve("legacy/Y.java")));
        assertEquals(0, ExemptionFilter.of(List.of()).size());
    }

    // ==================== CheckRunner filtering ====================

    private CheckOutcome runCheck(String relPath, String content) throws IOException {
        Files.createDirectories(dir.resolve(relPath).getParent());
        Files.writeString(dir.resolve(relPath), content);

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        return new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                .run(scan, LintProfile.STANDARD, CliOptions.FixMode.NONE);
    }

    @Test
    public void exemptedFileIsCleanAndCounted() throws IOException {
        CheckOutcome outcome = runCheck("legacy/Bad.java",
                "class Bad { void m() { System.out.println(\"x\"); inlineCall(1); } }\n");

        // both rules fire on the original content; the exemption removes
        // exactly the target rule's hit, the inline rule's stays
        FileFindings legacy = outcome.findings().get(0);
        assertEquals(1, legacy.diagnostics().size());
        assertEquals("demo/exempt-inline", legacy.diagnostics().get(0).ruleId());
        assertEquals(1, outcome.summary().getExemptedDiagnostics(),
                "the exempted diagnostic is counted, never dropped silently");
    }

    @Test
    public void nonExemptedFileStillReports() throws IOException {
        CheckOutcome outcome = runCheck("src/ok/Ok.java",
                "class Ok { void m() { System.out.println(\"x\"); } }\n");

        assertEquals(1, outcome.findings().get(0).diagnostics().size(),
                "outside the exempted glob the rule still fires");
        assertEquals(0, outcome.summary().getExemptedDiagnostics());
    }

    @Test
    public void exitCodeDataFlowCountsOnlyPostFilterResidual() throws IOException {
        // demo/exempt-target is ERROR-severity: on the exempted file the
        // residual is empty, so the run must exit clean — the exit-code
        // pipeline must never see the exempted diagnostic
        CheckOutcome outcome = runCheck("legacy/Bad.java",
                "class Bad { void m() { System.out.println(\"x\"); } }\n");

        assertEquals(0, outcome.findings().get(0).diagnostics().size());
        assertEquals(false, outcome.hasErrorDiagnostics(),
                "the exempted error diagnostic must not drive the exit code");
    }

    @Test
    public void fixFlowNeverProducesFixesForExemptedDiagnostics() throws IOException {
        // the exemption rides the applier's lint entry: the exempted file's
        // fix is never generated even though the rule fires there
        Files.createDirectories(dir.resolve("legacy"));
        Files.writeString(dir.resolve("legacy/Bad.java"),
                "class Bad { void m() { System.out.println(\"x\"); } }\n");

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        CheckOutcome outcome = new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                .run(scan, LintProfile.STANDARD, CliOptions.FixMode.APPLY);

        assertEquals("class Bad { void m() { System.out.println(\"x\"); } }\n",
                Files.readString(dir.resolve("legacy/Bad.java")),
                "the exempted diagnostic produces no fix (design 09 §6)");
    }

    // ==================== RuleTester isolation red line ====================

    @Test
    public void enginePathIsUnaffectedByExemptions() {
        // the RuleTester/LintEngine surface has no exemption concept: the
        // same source still reports the exempted rule's diagnostic when
        // linted directly (exemptions live at the CheckRunner layer only,
        // design 09 §6)
        RuleDslModel rule = new RuleDslParser().loadRuleModel(RULES_PREFIX + "/demo/exempt-target.rule.yml");
        LintEngine engine = new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
        LintResult result = engine.lint(List.of(rule),
                LanguageRegistry.discoverDefaults().resolve("java"),
                "class X { void m() { System.out.println(\"x\"); } }");

        assertEquals(1, result.diagnostics().size(),
                "the engine itself does not know about ruleset exemptions");
    }
}
