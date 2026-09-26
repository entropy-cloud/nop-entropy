package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.fix.FixApplier;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI8 production-rule autofix census (plan nop-refactor/08, roadmap
 * WI8): the six selected production rules carry applicable (non-suggest)
 * fix templates, one real engine run per rule renders the exact expected
 * replacement on the shared before source, and the fix pipeline's own
 * mechanical core ({@link FixApplier}, dry-run) splices that replacement
 * into a byte-identical after source. The negative anchor pins the
 * fail-closed face of the template contract: a template capture the pattern
 * never declares rejects at engine-compile time (CompiledRule.compile ->
 * TemplateFix.compile on the first lint), not at load time.
 */
public class TestProductionRuleFixes {

    private static final String BAD_CAPTURE_RULE_PATH =
            "/test/lint/fix-negative/bad-capture.rule.yml";

    /**
     * One selected rule's end-to-end row: the rule's VFS path, the before
     * source (exactly one diagnostic), the template's expected rendered
     * replacement, and the full after source the applier must produce.
     */
    private record FixCase(String ruleId, String rulePath, String before, String replacement, String after) {
    }

    private static final List<FixCase> CASES = List.of(
            new FixCase("quality/use-collection-isempty",
                    "/nop/lint/rules/quality/use-collection-isempty.rule.yml",
                    """
                            import java.util.List;

                            public class Sample {
                                boolean empty(List<String> items) {
                                    return items.size() == 0;
                                }
                            }
                            """,
                    "items.isEmpty()",
                    """
                            import java.util.List;

                            public class Sample {
                                boolean empty(List<String> items) {
                                    return items.isEmpty();
                                }
                            }
                            """),
            new FixCase("exception/no-throw-npe",
                    "/nop/lint/rules/exception/no-throw-npe.rule.yml",
                    """
                            public class Sample {
                                void load(String key) {
                                    if (key == null) {
                                        throw new NullPointerException("key must not be null");
                                    }
                                }
                            }
                            """,
                    "throw new IllegalStateException(\"key must not be null\");",
                    """
                            public class Sample {
                                void load(String key) {
                                    if (key == null) {
                                        throw new IllegalStateException("key must not be null");
                                    }
                                }
                            }
                            """),
            new FixCase("quality/replace-hashtable",
                    "/nop/lint/rules/quality/replace-hashtable.rule.yml",
                    """
                            import java.util.Hashtable;

                            public class Sample {
                                void legacy() {
                                    Hashtable table = new Hashtable();
                                    table.put("a", "b");
                                }
                            }
                            """,
                    "Map table = new HashMap();",
                    """
                            import java.util.Hashtable;

                            public class Sample {
                                void legacy() {
                                    Map table = new HashMap();
                                    table.put("a", "b");
                                }
                            }
                            """),
            new FixCase("quality/replace-vector",
                    "/nop/lint/rules/quality/replace-vector.rule.yml",
                    """
                            import java.util.Vector;

                            public class Sample {
                                void legacy() {
                                    Vector items = new Vector();
                                    items.add("x");
                                }
                            }
                            """,
                    "List items = new ArrayList();",
                    """
                            import java.util.Vector;

                            public class Sample {
                                void legacy() {
                                    List items = new ArrayList();
                                    items.add("x");
                                }
                            }
                            """),
            new FixCase("quality/random-mod",
                    "/nop/lint/rules/quality/random-mod.rule.yml",
                    """
                            import java.util.Random;

                            public class Sample {
                                int roll(Random rnd) {
                                    return rnd.nextInt() % 10;
                                }
                            }
                            """,
                    "rnd.nextInt(10)",
                    """
                            import java.util.Random;

                            public class Sample {
                                int roll(Random rnd) {
                                    return rnd.nextInt(10);
                                }
                            }
                            """),
            new FixCase("exception/equals-null",
                    "/nop/lint/rules/exception/equals-null.rule.yml",
                    """
                            public class Sample {
                                boolean broken(String text) {
                                    return text.equals(null);
                                }
                            }
                            """,
                    "text == null",
                    """
                            public class Sample {
                                boolean broken(String text) {
                                    return text == null;
                                }
                            }
                            """));

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * (a) The metadata face: every selected rule ships an applicable,
     * non-suggestion fix declaration through the real loading pipeline.
     */
    @Test
    public void everySelectedRuleCarriesApplicableFixMetadata() {
        for (FixCase fixCase : CASES) {
            RuleDslModel rule = new RuleDslParser().loadRuleModel(fixCase.rulePath());
            assertEquals(fixCase.ruleId(), rule.getId(), fixCase.rulePath());
            assertNotNull(rule.getFix(), fixCase.ruleId() + " must declare its fix template");
            assertFalse(rule.getFix().isSuggest(), fixCase.ruleId() + " must be applicable, not suggestion-only");
            assertTrue(rule.getMetadata().isAutoFixable(), fixCase.ruleId() + " must flip autoFixable");
        }
    }

    /**
     * (b) The rendering face: one real engine run per rule over the shared
     * before source yields exactly one diagnostic whose rendered replacement
     * is character-identical to the expected rewrite (the TemplateFix
     * product, riding the diagnostic).
     */
    @Test
    public void engineRunRendersExactExpectedReplacement() {
        for (FixCase fixCase : CASES) {
            RuleDslModel rule = new RuleDslParser().loadRuleModel(fixCase.rulePath());
            LintEngine engine = new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
            LintLanguage java = LanguageRegistry.discoverDefaults().resolve("java");

            LintResult result = engine.lint(List.of(rule), java, fixCase.before());

            assertEquals(1, result.diagnostics().size(),
                    fixCase.ruleId() + " must match exactly once on the before source");
            Diagnostic diagnostic = result.diagnostics().get(0);
            assertNotNull(diagnostic.fix(), fixCase.ruleId() + " must ride its fix on the diagnostic");
            assertEquals(fixCase.replacement(), diagnostic.fix().replacement(),
                    fixCase.ruleId() + " template must render the exact expected replacement");
        }
    }

    /**
     * (c) The application face: the fix pipeline's single mechanical core
     * (FixApplier, dry-run — nothing touches disk) splices the rendered
     * replacement into the before source and lands on a byte-identical after
     * source, applying exactly one edit with no conflicts and no rollback
     * (the after source is syntax-complete Java, so the re-parse guard holds).
     */
    @Test
    public void dryRunApplierLandsOnByteIdenticalAfterSource() {
        for (FixCase fixCase : CASES) {
            RuleDslModel rule = new RuleDslParser().loadRuleModel(fixCase.rulePath());
            LintEngine engine = new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
            LintLanguage java = LanguageRegistry.discoverDefaults().resolve("java");

            FixApplier applier = new FixApplier(
                    source -> engine.lint(List.of(rule), java, new String(source, StandardCharsets.UTF_8)),
                    java);

            FixApplier.FixResult result = applier.run(Path.of("Sample.java"),
                    fixCase.before().getBytes(StandardCharsets.UTF_8), true);

            assertArrayEquals(fixCase.after().getBytes(StandardCharsets.UTF_8), result.finalSource(),
                    fixCase.ruleId() + " dry-run must land on the byte-identical after source");
            assertEquals(1, result.stats().applied(), fixCase.ruleId() + " must apply exactly one edit");
            assertEquals(0, result.stats().conflicts(), fixCase.ruleId() + " has no overlapping edits");
            assertEquals(0, result.stats().rollbacks(), fixCase.ruleId() + " must not trip the syntax guard");
        }
    }

    /**
     * The negative anchor: a template referencing a capture the pattern
     * never declares loads fine (parse-only) but fails closed when the rule
     * is compiled into the engine on the first lint — never a silent
     * placeholder rewrite.
     */
    @Test
    public void undeclaredTemplateCaptureRejectsAtEngineCompile() {
        RuleDslModel broken = new RuleDslParser().loadRuleModel(BAD_CAPTURE_RULE_PATH);
        assertEquals("demo/bad-capture", broken.getId(), "the negative fixture loads (parse-only)");
        assertNotNull(broken.getFix(), "the negative fixture carries its broken template");

        LintEngine engine = new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
        LintLanguage java = LanguageRegistry.discoverDefaults().resolve("java");

        NopLintException thrown = assertThrows(NopLintException.class,
                () -> engine.lint(List.of(broken), java, "public class Sample { boolean b(String t) { return t.equals(null); } }"),
                "an undeclared template capture must reject at engine-compile time");
        assertTrue(thrown.getMessage().toLowerCase(Locale.ROOT).contains("undeclared")
                        || thrown.getMessage().contains("UNDECLARED"),
                "the rejection names the offending capture: " + thrown.getMessage());
    }
}
