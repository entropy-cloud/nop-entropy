package io.nop.lint.java;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.semantic.TypeResolutionException;
import io.nop.lint.core.semantic.TypeResolver;
import io.nop.lint.java.semantic.JavaTypeResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java L2 degrade ladder end-to-end (roadmap item 26, plan
 * 2026-09-24-0500-1, Minimum Rules #22/#23): a {@code requires: [L2]} +
 * {@code typeOf} rule runs through the real {@code LintEngine} —
 * <ul>
 * <li>with the real {@link JavaTypeResolver}: resolvable JDK receivers
 * produce real diagnostics (the position→index→symbolsolver chain is
 * consumed at run time); unresolvable project types fail the query and
 * degrade the rule mid-run (the resolver's own fail-closed face is pinned
 * by {@code TestJavaTypeResolver})</li>
 * <li>with a throwing resolver: the mid-run failure counts as degradation
 * and produces no faked diagnostics</li>
 * <li>with a missing resolver (the production CLI's documented state): the
 * gate degrades the rule before any run</li>
 * </ul>
 */
public class TestL2DegradeLadder {

    private static final String RULE_PATH = "/test/lint/java-l2/demo/l2-string-only.rule.yml";

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

    private LanguageRegistry registry() {
        return LanguageRegistry.discoverDefaults();
    }

    private Path writeFixture(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    public void jdkReceiverProducesRealDiagnostics() throws IOException {
        Path file = writeFixture("Str.java",
                "class Str {\n    void m() {\n        String s = \"x\";\n        s.toString();\n    }\n}\n");
        TypeResolver resolver = new JavaTypeResolver();
        LintEngine engine = new LintEngine(registry(), LintProfile.STANDARD, resolver);

        LintResult result = engine.lint(List.of(new RuleDslParser().loadRuleModel(RULE_PATH)),
                registry().resolve("java"), file.toString(), Files.readString(file));

        assertEquals(1, result.diagnostics().size(),
                "the String receiver passes the typeOf constraint — a real diagnostic");
        assertEquals("demo/l2-string-only", result.diagnostics().get(0).ruleId());
        assertEquals(0, result.stats().getRulesDegraded(),
                "no degradation: the query answered");
        assertEquals(1, result.stats().getRulesExecuted());
    }

    /**
     * A deterministic failing resolver (the unresolvable-type face of the
     * ladder): every query throws {@link TypeResolutionException} — the
     * engine must degrade the rule mid-run with zero diagnostics.
     */
    private static TypeResolver throwingResolver() {
        return new TypeResolver() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void initProject(Path projectRoot) {
                // no state
            }

            @Override
            public boolean isAssignableTo(String filePath, int line, int col, String expectedType) {
                throw new TypeResolutionException("throwing resolver: the project type at "
                        + filePath + " " + line + ":" + col + " is unresolvable");
            }

            @Override
            public String typeNameAt(String filePath, int line, int col) {
                throw new TypeResolutionException("throwing resolver: " + filePath
                        + " " + line + ":" + col);
            }
        };
    }

    @Test
    public void throwingResolverDegradesMidRunWithZeroDiagnostics() throws IOException {
        Path file = writeFixture("Custom.java",
                "class Custom {\n    void m() {\n        c.toString();\n    }\n}\n");
        LintEngine engine = new LintEngine(registry(), LintProfile.STANDARD, throwingResolver());

        LintResult result = engine.lint(List.of(new RuleDslParser().loadRuleModel(RULE_PATH)),
                registry().resolve("java"), file.toString(), Files.readString(file));

        assertEquals(0, result.diagnostics().size(),
                "a degraded rule produces no faked diagnostics");
        assertEquals(1, result.stats().getRulesDegraded(),
                "the mid-run failure degrades the rule (the ladder's rung 2)");
        assertTrue(result.stats().getDegradedRuleIds().contains("demo/l2-string-only"));
    }

    @Test
    public void missingResolverDegradesAtTheGate() throws IOException {
        Path file = writeFixture("Str2.java",
                "class Str2 {\n    void m() {\n        String s = \"x\";\n        s.toString();\n    }\n}\n");
        // no resolver wired — the production CLI's documented state
        LintEngine engine = new LintEngine(registry(), LintProfile.STANDARD);

        LintResult result = engine.lint(List.of(new RuleDslParser().loadRuleModel(RULE_PATH)),
                registry().resolve("java"), file.toString(), Files.readString(file));

        assertEquals(0, result.diagnostics().size());
        assertEquals(1, result.stats().getRulesSkippedByProfile() + result.stats().getRulesDegraded(),
                "the gate skips or degrades the L2 rule — never runs it with faked answers");
    }

    @Test
    public void nonL2RulesRunWithoutAResolver() throws IOException {
        // the L2 gate must not touch rules that do not require L2
        Path file = writeFixture("Any.java",
                "class Any {\n    void m() {\n        anyCall();\n    }\n}\n");
        io.nop.lint.core.rule.RuleDslModel plain = new RuleDslParser()
                .loadRuleModel("/test/lint/java-l2/demo/plain-any.rule.yml");

        LintEngine engine = new LintEngine(registry(), LintProfile.STANDARD);
        LintResult result = engine.lint(List.of(plain), registry().resolve("java"),
                file.toString(), Files.readString(file));

        assertEquals(1, result.diagnostics().size());
        assertEquals(0, result.stats().getRulesDegraded());
        assertEquals("demo/plain-any", result.diagnostics().get(0).ruleId());
    }
}
