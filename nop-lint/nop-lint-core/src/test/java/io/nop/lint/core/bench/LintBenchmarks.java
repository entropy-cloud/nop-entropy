package io.nop.lint.core.bench;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.cli.RuleResultCache;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.suppress.SuppressionFilter;
import io.nop.lint.core.rule.RuleDslParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Baseline benchmarks for the pattern kernel (roadmap item 13) and the rule
 * engine (roadmap item 31): rule-set compilation (cold path), full-tree
 * matching (hot path), the end-to-end parse+match flow, and the full engine
 * pipeline (gate → match → constraints → xscript → suppression tail). All
 * numbers land in nop-lint/docs/perf-baseline.md via the runner in this
 * package.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class LintBenchmarks {

    private static final String[] RULE_PATTERNS = {
            "throw new RuntimeException($$$ARGS)",
            "$OBJ.dao().$METHOD($$$ARGS)",
            "class $C extends CrudBizModel { $$$ }",
    };

    private static final String XML_RULE_PATTERN = "<auth>$$$</auth>";

    private SourcePattern[] patterns;
    private LintTree tree;
    private LintEngine engine;
    private List<RuleDslModel> engineRules;
    private LintEngine xmlEngine;
    private List<RuleDslModel> xmlRules;
    private MetaVarEnv captureEnv;
    private byte[] shaInput;
    private SuppressionFilter xmlSuppression;
    private LintTree xmlTree;

    @Setup
    public void setup() {
        patterns = new SourcePattern[RULE_PATTERNS.length];
        for (int i = 0; i < RULE_PATTERNS.length; i++) {
            patterns[i] = SourcePatternCompiler.compile(RULE_PATTERNS[i], BenchLanguage.get());
        }
        tree = BenchLanguage.get().parse(BenchCorpus.JAVA_SOURCE);

        RuleDslParser parser = new RuleDslParser();
        engineRules = List.of(
                patternRule(parser, "bench/throw-raw", RULE_PATTERNS[0]),
                patternRule(parser, "bench/dao-access", RULE_PATTERNS[1]),
                patternRule(parser, "bench/crud-extends", RULE_PATTERNS[2]));
        LanguageRegistry registry = LanguageRegistry.empty();
        registry.register(BenchLanguage.get());
        engine = new LintEngine(registry, LintProfile.STANDARD);

        LanguageRegistry xmlRegistry = LanguageRegistry.empty();
        xmlRegistry.register(io.nop.lint.core.xml.XmlLanguage.get());
        xmlEngine = new LintEngine(xmlRegistry, LintProfile.STANDARD);
        xmlRules = List.of(patternRule(parser, "bench/xml-auth", XML_RULE_PATTERN));

        captureEnv = buildCaptureEnv(tree.root());
        shaInput = BenchCorpus.JAVA_SOURCE.getBytes(StandardCharsets.UTF_8);

        xmlTree = io.nop.lint.core.xml.XmlLanguage.get().parse(BenchCorpus.XML_SOURCE_LARGE);
        xmlSuppression = new SuppressionFilter(io.nop.lint.core.xml.XmlLanguage.get()
                .suppressionProvider());
    }

    private static MetaVarEnv buildCaptureEnv(LintNode root) {
        MetaVarEnv env = new MetaVarEnv();
        List<LintNode> children = new ArrayList<>();
        for (LintNode node : root) {
            children.add(node);
            if (children.size() >= 32) {
                break;
            }
        }
        for (int i = 0; i < 8 && i * 4 < children.size(); i++) {
            env.insert("cap" + i, children.get(i * 4));
            env.insertMulti("multi" + i, List.of(children.get(i * 4),
                    children.get(Math.min(i * 4 + 1, children.size() - 1))));
        }
        return env;
    }

    private static RuleDslModel patternRule(RuleDslParser parser, String id, String pattern) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", pattern);
        model.addProp("rule", rule);
        return parser.parseRuleModel(model);
    }

    /**
     * Cold-path reference: compiling the full baseline rule set once.
     */
    @Benchmark
    public SourcePattern[] compileRuleSet() {
        SourcePattern[] compiled = new SourcePattern[RULE_PATTERNS.length];
        for (int i = 0; i < RULE_PATTERNS.length; i++) {
            compiled[i] = SourcePatternCompiler.compile(RULE_PATTERNS[i], BenchLanguage.get());
        }
        return compiled;
    }

    /**
     * Hot path: all baseline patterns over one parsed compilation unit.
     */
    @Benchmark
    public int matchAllPatterns() {
        int hits = 0;
        for (SourcePattern pattern : patterns) {
            hits += pattern.matchIn(tree.root()).size();
        }
        return hits;
    }

    /**
     * End-to-end lint of one file: parse + match. The per-op time is the
     * ms/file figure compared against the design 11 §6 budget.
     */
    @Benchmark
    public int parseAndMatch() {
        LintTree fresh = BenchLanguage.get().parse(BenchCorpus.JAVA_SOURCE);
        LintNode root = fresh.root();
        int hits = 0;
        for (SourcePattern pattern : patterns) {
            List<Match> matches = pattern.matchIn(root);
            hits += matches.size();
        }
        return hits;
    }

    /**
     * The full engine pipeline over one file (roadmap item 31's performance
     * gate): gate → kind filter → matching → budget boundary checks →
     * suppression tail → stats. The per-op time is the engine-level ms/file
     * figure the budget machinery must not move.
     */
    @Benchmark
    public int engineLint() {
        LintResult result = engine.lint(engineRules, "java", "bench/OrderService.java",
                BenchCorpus.JAVA_SOURCE);
        return result.diagnostics().size();
    }

    /**
     * The full engine pipeline over the ~2000-line Java corpus: the
     * large-file face where per-node hot-path costs (suppression tail,
     * kind filter, capture consistency) are observable (plan 08 Phase 1).
     */
    @Benchmark
    public int engineLintJavaLarge() {
        LintResult result = engine.lint(engineRules, "java", "bench/OrderServiceLarge.java",
                BenchCorpus.JAVA_SOURCE_LARGE);
        return result.diagnostics().size();
    }

    /**
     * The full engine pipeline over the large XML facade corpus: the
     * mixed-case tag face where the suppression tail's comment-kind check
     * meets case-changing kind names (plan 08 Phase 1).
     */
    @Benchmark
    public int engineLintXmlLarge() {
        LintResult result = xmlEngine.lint(xmlRules, "xml", "bench/LargeCatalog.xml",
                BenchCorpus.XML_SOURCE_LARGE);
        return result.diagnostics().size();
    }

    /**
     * Site benchmark (plan 08 Phase 2): the suppression tail's comment scan
     * over the pre-parsed large XML facade tree — the collectComments walk
     * isolated from parse cost, so the comment-kind check is directly
     * observable.
     */
    @Benchmark
    public int suppressionScanXmlLarge() {
        return xmlSuppression.evaluate(xmlTree, java.util.List.of()).suppressed().size();
    }

    /**
     * Site micro (plan 08 Phase 3): the multi-capture snapshot the xscript
     * binding layer takes per xscript match — before the fix it copies the
     * map three times (LinkedHashMap + per-value List.copyOf + Map.copyOf).
     */
    @Benchmark
    public int multiCaptureSnapshot() {
        Map<String, List<LintNode>> snapshot = captureEnv.multiCaptures();
        int size = 0;
        for (List<LintNode> nodes : snapshot.values()) {
            size += nodes.size();
        }
        return size;
    }

    /**
     * Site micro (plan 08 Phase 4): the SHA-256 hex encoding the cache and
     * baseline fingerprints run per file/diagnostic — before the fix every
     * byte goes through String.format.
     */
    @Benchmark
    public String sha256Hex() {
        return RuleResultCache.sha256(shaInput);
    }
}
