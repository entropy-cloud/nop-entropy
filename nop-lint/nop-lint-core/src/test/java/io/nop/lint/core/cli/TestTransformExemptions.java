package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transform channel's CLI-layer safety matrix (nop-refactor WI3 Phase 2):
 * an exempted rule's rewrite does not apply (the same rule-set exemption
 * predicate gates both channels), a non-exempted file gets every rewrite,
 * and the report face stays oblivious — transform matches count in their own
 * stats and never appear as diagnostics.
 */
public class TestTransformExemptions {

    private static final String RULES_PREFIX = "/test/lint/cli-rules-transform";

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

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

    @Test
    public void exemptedTransformRuleDoesNotRewrite() throws IOException {
        Files.createDirectories(dir.resolve("legacy"));
        Files.writeString(dir.resolve("legacy/Bad.java"),
                "class Bad { void m() { System.out.println(\"x\"); inlineCall(1); } }\n");

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        CheckOutcome outcome = new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                .run(scan, LintProfile.STANDARD, CliOptions.FixMode.APPLY);

        String content = Files.readString(dir.resolve("legacy/Bad.java"));
        assertTrue(content.contains("directCall(1)"),
                "the non-exempted inline rewrite applies: " + content);
        assertTrue(content.contains("System.out.println"),
                "the exempted rule's rewrite must not apply (same exemption predicate "
                        + "as the fix channel): " + content);
        assertEquals(0, outcome.findings().get(0).diagnostics().size(),
                "transform rules never report, even when their rewrites were exempted");
    }

    @Test
    public void nonExemptedFileGetsBothRewrites() throws IOException {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/Ok.java"),
                "class Ok { void m() { System.out.println(\"x\"); inlineCall(1); } }\n");

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                .run(scan, LintProfile.STANDARD, CliOptions.FixMode.APPLY);

        String content = Files.readString(dir.resolve("src/Ok.java"));
        assertTrue(content.contains("logger.log(\"x\")"), content);
        assertTrue(content.contains("directCall(1)"), content);
        assertTrue(!content.contains("System.out.println") && !content.contains("inlineCall"),
                "both rewrites applied outside the exempted glob: " + content);
    }

    @Test
    public void reportFaceStaysObliviousToRewrites() {
        // the engine path: transform matches land in their own channel and
        // their own counters — never in diagnostics, never suppressed
        RuleDslParser parser = new RuleDslParser();
        RuleDslModel target = parser.loadRuleModel(RULES_PREFIX + "/demo/rewrite-target.rule.yml");
        LintEngine engine = new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
        LintResult result = engine.lint(List.of(target), JAVA, null,
                "class X { void m() { System.out.println(\"x\"); } }");

        assertEquals(0, result.diagnostics().size(),
                "a transform rule never reports");
        assertEquals(1, result.transformFixes().size(), "the rewrite rides its own channel");
        assertEquals("logger.log(\"x\")", result.transformFixes().get(0).replacement(),
                "the template rendered the captured argument slice");
        assertEquals(1, result.stats().getTransformMatches(),
                "the rewrite is counted in its own counter, not in diagnostics");
        assertEquals(0, result.stats().getDiagnostics());
    }
}
