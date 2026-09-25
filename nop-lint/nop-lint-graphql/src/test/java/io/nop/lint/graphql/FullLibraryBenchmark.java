package io.nop.lint.graphql;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.engine.CompiledRule;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.rule.RuleDslModel;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The full-production-library face (plan 10 Phase 3): the same 62-rule set
 * lints one file through the per-call compile entry versus the precompiled
 * entry — the delta between the two benchmarks IS the per-file compile cost
 * the precompiled path removes. Lives next to {@link GraphQlLintBenchmark}
 * because the production rule YAML rides nop-lint-nop on this module's
 * test classpath.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class FullLibraryBenchmark {

    LintEngine engine;
    List<RuleDslModel> models;
    List<CompiledRule> precompiled;
    String source;

    @Setup
    public void setup() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        engine = new LintEngine(registry, LintProfile.STANDARD);

        RuleSetLoader.LoadedRuleSet loaded = new RuleSetLoader().loadRuleSet("/nop/lint/rules");
        // one lint call serves ONE language's rule group (cross-language
        // groups belong to their own documents) — the java group is the
        // production library's bulk (59 of 62 rules; 3 are XNode XML rules
        // for XML documents)
        models = loaded.rulesByLanguage().getOrDefault("java", List.of());
        LintLanguage language = registry.resolve("java");
        precompiled = models.stream().map(m -> CompiledRule.compile(m, language)).toList();

        StringBuilder sb = new StringBuilder("package demo;\n\nclass OrderService {\n");
        for (int i = 0; i < 30; i++) {
            sb.append("    void method").append(i)
                    .append("(String a, String b) {\n        System.out.println(a + b);\n    }\n\n");
        }
        sb.append("}\n");
        source = sb.toString();
    }

    /**
     * The historical face: every lint call recompiles the full library
     * (62 pattern parses per op).
     */
    @Benchmark
    public int fullLibraryCompilePerCall() {
        return engine.lint(models, "java", "bench/full.java", source).diagnostics().size();
    }

    /**
     * The precompiled face (plan 10): gate re-judgment + matching only.
     */
    @Benchmark
    public int fullLibraryPrecompiled() {
        return engine.lintCompiled(precompiled, "java", "bench/full.java", source)
                .diagnostics().size();
    }
}
