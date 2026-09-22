package io.nop.lint.core.engine;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;
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
 * not cover a {@code requires} token), or degraded (roadmap item 20 — the
 * ceiling declares L2 but the run cannot serve it: no resolver wired, the
 * resolver's environment probe fails, or the run lints an unnamed source).
 * A rule that passes the gate can still degrade mid-run, when its type
 * queries fail during constraint evaluation; the runner counts that in the
 * same {@code degraded} statistic, and a degraded rule produces no
 * diagnostics and never continues on a lower level's answers (roadmap hard
 * constraint).</p>
 */
public final class LintEngine {

    private static final Logger LOG = LoggerFactory.getLogger(LintEngine.class);

    private final LanguageRegistry registry;
    private final LintProfile profile;
    private final TypeResolver typeResolver;

    /**
     * @param registry the language bindings this engine resolves rule
     *                 language fields against
     * @param profile  the execution profile; capability ceilings gate the
     *                 {@code requires} check
     */
    public LintEngine(LanguageRegistry registry, LintProfile profile) {
        this(registry, profile, null);
    }

    /**
     * @param registry     the language bindings this engine resolves rule
     *                     language fields against
     * @param profile      the execution profile; capability ceilings gate the
     *                     {@code requires} check
     * @param typeResolver the run family's L2 provider (roadmap item 20), or
     *                     null for an L2-less engine — rules requiring L2
     *                     then degrade instead of running; the resolver is
     *                     consulted lazily (its availability probe must not
     *                     start anything, per design 11 §3)
     */
    public LintEngine(LanguageRegistry registry, LintProfile profile, TypeResolver typeResolver) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.typeResolver = typeResolver;
    }

    /**
     * Lints {@code source} with rules declared for {@code languageId}; the
     * id goes through the registry's normalization (case-insensitive, as in
     * the rule fixtures). Unknown ids fail closed. The source is unnamed —
     * L2-requiring rules degrade (a type query needs a file path).
     */
    public LintResult lint(List<RuleDslModel> rules, String languageId, String source) {
        return lint(rules, registry.resolve(languageId), null, source);
    }

    /**
     * Lints an unnamed source with an explicit binding; L2-requiring rules
     * degrade (a type query needs a file path).
     */
    public LintResult lint(List<RuleDslModel> rules, LintLanguage language, String source) {
        Objects.requireNonNull(source, "source must not be null");
        return lint(rules, language, null, language.parse(source));
    }

    /**
     * Lints the named file's source with an explicit binding; the file path
     * is what L2 type queries resolve positions against.
     */
    public LintResult lint(List<RuleDslModel> rules, LintLanguage language, String filePath, String source) {
        Objects.requireNonNull(source, "source must not be null");
        return lint(rules, language, filePath, language.parse(source));
    }

    /**
     * Lints an unnamed pre-parsed tree (the entry point for callers that
     * already own a {@link LintTree}); L2-requiring rules degrade.
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
                    LOG.warn("nop.lint.l2.rule-degraded:ruleId={},reason=type-resolver-unavailable",
                            rule.getId());
                }
                case RUN -> compiled.add(CompiledRule.compile(rule, language, typeQueries));
            }
        }
        // Route A (design 07 §4): make sure the deadline wrapper owns the
        // global executor slot before any script runs. Idempotent, and
        // transparent for evaluations without a lint deadline in scope.
        LintDeadlineExecutor.install();
        List<Diagnostic> candidates = RuleSetRunner.run(compiled, tree, stats, profile);
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
     * present.
     */
    private Gate gate(RuleDslModel rule, String filePath) {
        boolean needsL2 = false;
        for (String requirement : rule.getRequires()) {
            LintCapability capability = LintCapability.byToken(requirement);
            if (capability == null || !profile.capabilities().contains(capability)) {
                return Gate.SKIP;
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
     * True when this run could answer a type query: a resolver is wired,
     * its environment probe passes (lazy — the probe never starts the
     * backend), and the run named the file it lints.
     */
    private boolean l2Ready(String filePath) {
        return filePath != null && !filePath.isBlank()
                && typeResolver != null && typeResolver.isAvailable();
    }

    private enum Gate {
        RUN, SKIP, DEGRADE
    }
}
