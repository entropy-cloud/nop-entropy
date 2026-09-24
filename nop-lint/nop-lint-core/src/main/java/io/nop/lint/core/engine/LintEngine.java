package io.nop.lint.core.engine;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.semantic.MetricsResolver;
import io.nop.lint.core.semantic.ScopeResolver;
import io.nop.lint.core.semantic.TypeQuerySupport;
import io.nop.lint.core.semantic.TypeResolver;
import io.nop.lint.core.suppress.SuppressionFilter;
import io.nop.lint.core.suppress.SuppressionOutcome;
import io.nop.lint.core.xscript.LintDeadlineExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The minimal rule engine (design 03 §1.1 single-file pipeline, v1 subset;
 * design 11 §1 fixed order): language resolution → per-rule {@code requires}
 * gate → compilation (form execution matrix) → kind-bit filtering →
 * matching → {@link Diagnostic} → suppression judgment → {@link LintStats}.
 * The suppression tail (design 09) runs after the rule loop — the position
 * design 03 §1.1 places after xscript and before the diagnostics are
 * emitted — in both profiles alike (design 11 §2: v1 suppression is never
 * profile-trimmed), and its outcome is fully observable: every removed
 * diagnostic is counted in {@code suppressedDiagnostics}, every
 * pairing/unused meta-diagnostic is emitted into the result.
 *
 * <p>The gate partitions the loaded rules into exactly one observable exit:
 * run (compiled), skipped-by-profile (the profile's capability ceiling does
 * not cover a {@code requires} token), or degraded (the ceiling declares a
 * capability but the run cannot serve it — L2: no resolver wired, the
 * resolver's environment probe fails, or the run lints an unnamed source,
 * roadmap item 20; deep analyzers L3/L4/SCOPE/METRICS: no live provider in
 * the run's {@link DeepResolvers} or an unnamed file, roadmap items
 * 32–34). A rule that passes the gate can still degrade mid-run, when its
 * type queries fail during constraint evaluation; the runner counts that in
 * the same {@code degraded} statistic, and a degraded rule produces no
 * diagnostics and never continues on a lower level's answers (roadmap hard
 * constraint).</p>
 */
public final class LintEngine {

    private static final Logger LOG = LoggerFactory.getLogger(LintEngine.class);

    private final LanguageRegistry registry;
    private final LintProfile profile;
    private final TypeResolver typeResolver;
    private final DeepResolvers deep;
    private final LongSupplier clock;

    /**
     * @param registry the language bindings this engine resolves rule
     *                 language fields against
     * @param profile  the execution profile; capability ceilings gate the
     *                 {@code requires} check
     */
    public LintEngine(LanguageRegistry registry, LintProfile profile) {
        this(registry, profile, null, DeepResolvers.NONE);
    }

    /**
     * @param typeResolver the run family's L2 provider (roadmap item 20), or
     *                     null for an L2-less engine — rules requiring L2
     *                     then degrade instead of running; the resolver is
     *                     consulted lazily (its availability probe must not
     *                     start anything, per design 11 §3)
     */
    public LintEngine(LanguageRegistry registry, LintProfile profile, TypeResolver typeResolver) {
        this(registry, profile, typeResolver, DeepResolvers.NONE);
    }

    /**
     * @param deep the run family's deep-analyzer providers (roadmap items
     *             32–34), or {@link DeepResolvers#NONE} for a run that
     *             serves none — rules requiring the covered capabilities
     *             then degrade instead of running (fail-closed).
     *             ServiceLoader-discovered for the CLI default wiring.
     */
    public LintEngine(LanguageRegistry registry, LintProfile profile, DeepResolvers deep) {
        this(registry, profile, null, deep);
    }

    /**
     * @param registry     the language bindings this engine resolves rule
     *                     language fields against
     * @param profile      the execution profile; capability ceilings gate the
     *                     {@code requires} check
     * @param typeResolver the run family's L2 provider (roadmap item 20), or
     *                     null for an L2-less engine
     * @param deep         the run family's deep-analyzer providers (roadmap
     *                     items 32–34), or {@link DeepResolvers#NONE}
     */
    public LintEngine(LanguageRegistry registry, LintProfile profile, TypeResolver typeResolver,
                      DeepResolvers deep) {
        this(registry, profile, typeResolver, deep, System::nanoTime);
    }

    /**
     * The legacy provider-pair constructor (the roadmap items 32/33 shape):
     * equivalent to a {@link DeepResolvers} carrying just the metrics and
     * scope providers. Kept for the item 32/33 call sites.
     */
    public LintEngine(LanguageRegistry registry, LintProfile profile, TypeResolver typeResolver,
                      MetricsResolver metricsResolver, ScopeResolver scopeResolver) {
        this(registry, profile, typeResolver,
                new DeepResolvers(metricsResolver, scopeResolver, null, null));
    }

    /**
     * The test seam for deterministic budget exhaustion (item 31 plan
     * Decision 8): the budget's monotonic clock is injectable; public API
     * surface stays the other constructors. The in-script deadline
     * enforcement ({@code LintDeadlineExecutor}) always reads the real
     * clock — fake clocks must start aligned with {@link System#nanoTime()}
     * and jump only at assertion points.
     */
    LintEngine(LanguageRegistry registry, LintProfile profile, TypeResolver typeResolver,
               DeepResolvers deep, LongSupplier clock) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.typeResolver = typeResolver;
        this.deep = deep == null ? DeepResolvers.NONE : deep;
        this.clock = clock;
    }

    /**
     * Lints {@code source} with rules declared for {@code languageId}; the
     * id goes through the registry's normalization (case-insensitive, as in
     * the rule fixtures). Unknown ids fail closed. The source is unnamed —
     * position-keyed rules degrade (their queries need a file path).
     */
    public LintResult lint(List<RuleDslModel> rules, String languageId, String source) {
        return lint(rules, registry.resolve(languageId), null, source);
    }

    /**
     * Lints an unnamed source with an explicit binding; position-keyed
     * rules degrade (their queries need a file path).
     */
    public LintResult lint(List<RuleDslModel> rules, LintLanguage language, String source) {
        Objects.requireNonNull(source, "source must not be null");
        return lint(rules, language, null, language.parse(source));
    }

    /**
     * Lints the named file's source with an explicit binding; the file path
     * is what position-keyed queries resolve against.
     */
    public LintResult lint(List<RuleDslModel> rules, LintLanguage language, String filePath, String source) {
        Objects.requireNonNull(source, "source must not be null");
        return lint(rules, language, filePath, language.parse(source));
    }

    /**
     * Lints the named file's source with rules declared for
     * {@code languageId} (resolved through the registry, case-insensitive).
     */
    public LintResult lint(List<RuleDslModel> rules, String languageId, String filePath, String source) {
        return lint(rules, registry.resolve(languageId), filePath, source);
    }

    /**
     * Lints an unnamed pre-parsed tree (the entry point for callers that
     * already own a {@link LintTree}); position-keyed rules degrade.
     */
    public LintResult lint(List<RuleDslModel> rules, LintLanguage language, LintTree tree) {
        return lint(rules, language, null, tree);
    }

    /**
     * Lints a named pre-parsed tree.
     */
    public LintResult lint(List<RuleDslModel> rules, LintLanguage language, String filePath, LintTree tree) {
        Objects.requireNonNull(rules, "rules must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(tree, "tree must not be null");

        LintStats.Builder stats = LintStats.builder().rulesLoaded(rules.size());
        TypeQuerySupport typeQueries = new TypeQuerySupport(typeResolver, filePath, tree.source());
        List<CompiledRule> compiled = new ArrayList<>(rules.size());
        for (RuleDslModel rule : rules) {
            Gate gate = gate(rule, filePath);
            switch (gate) {
                case SKIP -> stats.incRulesSkippedByProfile(rule.getId());
                case DEGRADE -> {
                    stats.incRulesDegraded(rule.getId());
                    LOG.warn("nop.lint.l2.rule-degraded:ruleId={},reason=analyzer-unavailable",
                            rule.getId());
                }
                case RUN -> compiled.add(CompiledRule.compile(rule, language, typeQueries));
            }
        }
        // Roadmap item 31: one budget per lint call (design 11 §2 soft
        // budget; a --fix multipass performs several lint calls per file,
        // each with a fresh budget, item 31 plan Decision 7).
        FileBudget budget = FileBudget.start(profile, clock);
        // Route A (design 07 §4): make sure the deadline wrapper owns the
        // global executor slot before any script runs. Idempotent, and
        // transparent for evaluations without a lint deadline in scope.
        LintDeadlineExecutor.install();
        List<Diagnostic> candidates = RuleSetRunner.run(compiled, tree, stats, profile, budget,
                deep, l2Ready(filePath), filePath);
        // design 03 §1.1 pipeline tail: the suppression judgment sits after
        // xscript (all rules have run) and before the diagnostics are
        // emitted. The language's annotation provider joins the always-on
        // inline-comment scan; a language without one suppresses through
        // comments only.
        SuppressionFilter filter = new SuppressionFilter(language.suppressionProvider());
        SuppressionOutcome outcome = filter.evaluate(tree, candidates);
        stats.incSuppressedDiagnostics(outcome.suppressed().size());
        stats.diagnostics(outcome.diagnostics().size());
        return new LintResult(outcome.diagnostics(), stats.build());
    }

    /**
     * The gate decision for one rule (see the class javadoc for the exit
     * contract). Unknown requirement tokens are unsatisfiable by
     * construction: an unshipped capability must not run as if it were
     * present. Every deep-analyzer capability follows the resolver path
     * (roadmap items 32–34): inside the profile ceiling it still needs a
     * live provider in {@link DeepResolvers} plus a named file, and a rule
     * whose provider is missing or not live degrades instead of running.
     */
    private Gate gate(RuleDslModel rule, String filePath) {
        boolean needsL2 = false;
        for (String requirement : rule.getRequires()) {
            LintCapability capability = LintCapability.byToken(requirement);
            if (capability == null || !profile.capabilities().contains(capability)) {
                return Gate.SKIP;
            }
            if (capability.isDeepAnalyzer() && !deepAnalyzerLive(capability, filePath)) {
                return Gate.DEGRADE;
            }
            if (capability == LintCapability.L2) {
                needsL2 = true;
            }
        }
        if (needsL2 && !l2Ready(filePath)) {
            return Gate.DEGRADE;
        }
        return Gate.RUN;
    }

    /**
     * True when this run could answer a deep-analyzer capability's queries:
     * its provider is wired in {@link DeepResolvers} and live, and the run
     * named the file it lints (position-keyed queries resolve against the
     * path).
     */
    private boolean deepAnalyzerLive(LintCapability capability, String filePath) {
        return switch (capability) {
            case METRICS -> DeepResolvers.live(deep.metrics()) && named(filePath);
            case SCOPE -> DeepResolvers.live(deep.scope()) && named(filePath);
            case L4 -> DeepResolvers.live(deep.semantic()) && named(filePath);
            case L3 -> DeepResolvers.live(deep.dataflow()) && named(filePath);
            default -> false;
        };
    }

    private static boolean named(String filePath) {
        return filePath != null && !filePath.isBlank();
    }

    /**
     * True when this run could answer an L2 type query: a resolver is
     * wired, its environment probe passes (lazy — the probe never starts
     * the backend), and the run named the file it lints.
     */
    private boolean l2Ready(String filePath) {
        return typeResolver != null && typeResolver.isAvailable() && named(filePath);
    }

    private enum Gate {
        RUN, SKIP, DEGRADE
    }
}
