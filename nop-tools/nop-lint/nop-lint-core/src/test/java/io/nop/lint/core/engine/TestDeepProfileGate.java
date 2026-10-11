package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.semantic.TypeResolver;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deep-profile gate matrix (roadmap item 31 Phase 1, design 11 §2 deep
 * row, plan Decisions 1/6): an analyzer-requiring rule takes exactly one
 * observable exit per run — profile skip when the ceiling does not cover
 * the token, gate degrade when the ceiling covers it but no live availability
 * probe answers, run when the probe is wired and live. The L2 path is
 * unchanged by the deep extension (its own gate branch keeps the resolver
 * contract), and unknown tokens stay fail-closed in every profile.
 */
public class TestDeepProfileGate {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private static final String SOURCE = "class Demo { void m() { foo.bar(); } }";

    private final RuleDslParser parser = new RuleDslParser();
    private final LanguageRegistry registry = LanguageRegistry.empty();
    private final Map<LintCapability, Boolean> live = new EnumMap<>(LintCapability.class);

    public TestDeepProfileGate() {
        registry.register(JAVA);
    }

    // ==================== skip: fast / standard ceilings ====================

    @Test
    public void fastAndStandardSkipDeepAnalyzerRules() {
        for (LintProfile profile : List.of(LintProfile.FAST, LintProfile.STANDARD)) {
            live.put(LintCapability.L3, true);
            LintResult result = lint(profile, rule("demo/l3-rule", "L3"));

            assertEquals(1, result.stats().getRulesSkippedByProfile(),
                    profile + " must skip the L3 rule by ceiling");
            assertEquals(0, result.stats().getRulesExecuted());
            assertEquals(0, result.stats().getRulesDegraded());
        }
    }

    // ==================== degrade: ceiling covers, no live probe ====================

    @Test
    public void deepWithoutWiredProbesDegradesDeepRules() {
        LintResult result = lint(LintProfile.DEEP, rule("demo/l3-rule", "L3"));

        assertEquals(1, result.stats().getRulesDegraded(),
                "the fail-closed default: no probe wired means not served");
        assertEquals(0, result.stats().getRulesExecuted());
        assertEquals(0, result.diagnostics().size(),
                "degraded rules produce no diagnostics (roadmap hard constraint)");
        assertEquals(List.of("demo/l3-rule"), result.stats().getDegradedRuleIds());
    }

    @Test
    public void deepWithProbeAnsweringNotLiveDegrades() {
        live.put(LintCapability.L3, false);

        LintResult result = lint(LintProfile.DEEP, rule("demo/l3-rule", "L3"));

        assertEquals(1, result.stats().getRulesDegraded());
        assertEquals(0, result.stats().getRulesExecuted());
    }

    // ==================== run: live probe serves the capability ====================

    @Test
    public void deepWithLiveProbeRunsTheRule() {
        live.put(LintCapability.L3, true);

        LintResult result = lint(LintProfile.DEEP, rule("demo/l3-rule", "L3"));

        assertEquals(1, result.stats().getRulesExecuted(),
                "a live probe must let the pattern rule run");
        assertEquals(0, result.stats().getRulesDegraded());
    }

    @Test
    public void deepCapabilitiesAreCaseInsensitiveTokens() {
        live.put(LintCapability.L4, true);

        LintResult result = lint(LintProfile.DEEP, rule("demo/l4-lower", "l4"));

        assertEquals(1, result.stats().getRulesExecuted());
    }

    // ==================== combined requirements ====================

    @Test
    public void l2PlusDeepRuleNeedsBothPathsLive() {
        live.put(LintCapability.L3, true);
        FakeResolver resolver = new FakeResolver(true);

        LintResult served = new LintEngine(registry, LintProfile.DEEP, resolver, deep())
                .lint(List.of(rule("demo/both", List.of("L2", "L3"))), JAVA, "demo/S.java", SOURCE);
        assertEquals(1, served.stats().getRulesExecuted(),
                "live resolver + live provider + named file = run");

        live.remove(LintCapability.L3);
        LintResult noProvider = new LintEngine(registry, LintProfile.DEEP, resolver, deep())
                .lint(List.of(rule("demo/both", List.of("L2", "L3"))), JAVA, "demo/S.java", SOURCE);
        assertEquals(1, noProvider.stats().getRulesDegraded(),
                "a missing provider degrades even with a live resolver");

        live.put(LintCapability.L3, true);
        LintResult noResolver = new LintEngine(registry, LintProfile.DEEP, null, deep())
                .lint(List.of(rule("demo/both", List.of("L2", "L3"))), JAVA, "demo/S.java", SOURCE);
        assertEquals(1, noResolver.stats().getRulesDegraded(),
                "a missing resolver degrades even with a live provider");
    }

    // ==================== fail-closed vocabulary ====================

    @Test
    public void unknownTokenStaysUnsatisfiableUnderDeep() {
        live.put(LintCapability.L3, true);

        LintResult result = lint(LintProfile.DEEP, rule("demo/future", "L3, tsc"));

        assertEquals(1, result.stats().getRulesSkippedByProfile(),
                "an unknown token cannot run even next to a served one");
        assertEquals(0, result.stats().getRulesExecuted());
    }

    @Test
    public void everyDeepTokenIsMarkedDeepAnalyzerAndGatesAlike() {
        for (LintCapability capability : List.of(LintCapability.L3, LintCapability.L4,
                LintCapability.SCOPE, LintCapability.METRICS)) {
            assertTrue(capability.isDeepAnalyzer(), capability + " is a deep analyzer");
            assertFalse(LintCapability.L1.isDeepAnalyzer());
            assertFalse(LintCapability.L2.isDeepAnalyzer());

            LintResult degraded = lint(LintProfile.DEEP, rule("demo/" + capability, capability.name()));
            assertEquals(1, degraded.stats().getRulesDegraded(), capability + " without probe degrades");
        }
    }

    // ==================== profile budget constants (design 11 §2) ====================

    @Test
    public void fileBudgetsMatchDesign11() {
        assertEquals(20, LintProfile.FAST.fileBudgetMs());
        assertEquals(500, LintProfile.STANDARD.fileBudgetMs());
        assertEquals(5000, LintProfile.DEEP.fileBudgetMs());
    }

    @Test
    public void deepCeilingIsTheCapabilitySuperset() {
        assertEquals(Set.of(LintCapability.L1, LintCapability.L2, LintCapability.L3,
                LintCapability.L4, LintCapability.SCOPE, LintCapability.METRICS),
                new HashSet<>(LintProfile.DEEP.capabilities()));
        assertFalse(LintProfile.STANDARD.capabilities().contains(LintCapability.L3));
    }

    // ==================== helpers ====================

    /**
     * The resolver-path fakes (roadmap items 32-34): every deep capability
     * gates through its provider's availability — live or not per the
     * {@code live} map.
     */
    private DeepResolvers deep() {
        return new DeepResolvers(
                metricsResolver(live.getOrDefault(LintCapability.METRICS, Boolean.FALSE)),
                scopeResolver(live.getOrDefault(LintCapability.SCOPE, Boolean.FALSE)),
                semanticResolver(live.getOrDefault(LintCapability.L4, Boolean.FALSE)),
                dataflowResolver(live.getOrDefault(LintCapability.L3, Boolean.FALSE)));
    }

    private LintResult lint(LintProfile profile, RuleDslModel rule) {
        return new LintEngine(registry, profile, deep())
                .lint(List.of(rule), JAVA, "demo/S.java", SOURCE);
    }

    private io.nop.lint.core.semantic.MetricsResolver metricsResolver(boolean available) {
        return new io.nop.lint.core.semantic.MetricsResolver() {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public int cyclomatic(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public int cognitive(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public long npath(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }
        };
    }

    private io.nop.lint.core.semantic.ScopeResolver scopeResolver(boolean available) {
        return new io.nop.lint.core.semantic.ScopeResolver() {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public long definitionOf(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public java.util.List<String> declaredNames(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public String scopeKind(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public boolean shadows(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }
        };
    }

    private io.nop.lint.core.semantic.SemanticResolver semanticResolver(boolean available) {
        return new io.nop.lint.core.semantic.SemanticResolver() {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public boolean implementsInterface(String filePath, int line, int col, String name) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public boolean isOverridable(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public boolean isLoggerCall(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }
        };
    }

    private io.nop.lint.core.semantic.DataflowResolver dataflowResolver(boolean available) {
        return new io.nop.lint.core.semantic.DataflowResolver() {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public String constantValue(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public long useCount(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }

            @Override
            public boolean isSelfAssigned(String filePath, int line, int col) {
                throw new UnsupportedOperationException("not queried by these tests");
            }
        };
    }

    private RuleDslModel rule(String id, Object requires) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "$A.bar()");
        model.addProp("rule", rule);
        model.addProp("requires", requires);
        return parser.parseRuleModel(model);
    }

    /**
     * Minimal live resolver: never queried by these pattern-only rules —
     * its presence alone decides the L2 gate branch.
     */
    private static final class FakeResolver implements TypeResolver {

        private final boolean available;

        FakeResolver(boolean available) {
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void initProject(java.nio.file.Path tsConfigPath) {
            throw new UnsupportedOperationException("not queried by these tests");
        }

        @Override
        public boolean isAssignableTo(String filePath, int line, int column, String expectedType) {
            throw new UnsupportedOperationException("not queried by these tests");
        }

        @Override
        public String typeNameAt(String filePath, int line, int col) {
            throw new UnsupportedOperationException("not queried by these tests");
        }
    }
}
