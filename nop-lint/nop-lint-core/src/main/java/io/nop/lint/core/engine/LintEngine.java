package io.nop.lint.core.engine;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.suppress.SuppressionFilter;
import io.nop.lint.core.suppress.SuppressionOutcome;
import io.nop.lint.core.xscript.LintDeadlineExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The minimal rule engine (design 03 §1.1 single-file pipeline, v1 subset;
 * design 11 §1 fixed order): language resolution → per-rule
 * {@code requires} check → compilation (form execution matrix) → kind-bit
 * filtering → matching → {@link Diagnostic} → suppression judgment →
 * {@link LintStats}. The suppression tail (design 09) runs after the rule
 * loop — the position design 03 §1.1 places after xscript and before the
 * diagnostics are emitted — in both profiles alike (design 11 §2: v1
 * suppression is never profile-trimmed), and its outcome is fully
 * observable: every removed diagnostic is counted in
 * {@code suppressedDiagnostics}, every pairing/unused meta-diagnostic is
 * emitted into the result.
 *
 * <p>Every rule leaves the loaded count through exactly one observable exit:
 * executed, skipped-by-profile (with its id in the stats), or
 * kind-filtered. There is no silent drop path, and no profile downgrades a
 * rule's declared level (roadmap hard constraint).</p>
 */
public final class LintEngine {

    private final LanguageRegistry registry;
    private final LintProfile profile;

    /**
     * @param registry the language bindings this engine resolves rule
     *                 language fields against
     * @param profile  the execution profile; capability sets gate the
     *                 {@code requires} check
     */
    public LintEngine(LanguageRegistry registry, LintProfile profile) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
    }

    /**
     * Lints {@code source} with rules declared for {@code languageId}; the
     * id goes through the registry's normalization (case-insensitive, as in
     * the rule fixtures). Unknown ids fail closed.
     */
    public LintResult lint(List<RuleDslModel> rules, String languageId, String source) {
        return lint(rules, registry.resolve(languageId), source);
    }

    /**
     * Lints {@code source} with an explicit binding.
     */
    public LintResult lint(List<RuleDslModel> rules, LintLanguage language, String source) {
        Objects.requireNonNull(source, "source must not be null");
        return lint(rules, language, language.parse(source));
    }

    /**
     * Lints a pre-parsed tree (the entry point for callers that already own
     * a {@link LintTree}, e.g. future incremental runs).
     */
    public LintResult lint(List<RuleDslModel> rules, LintLanguage language, LintTree tree) {
        Objects.requireNonNull(rules, "rules must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(tree, "tree must not be null");

        LintStats.Builder stats = LintStats.builder().rulesLoaded(rules.size());
        List<CompiledRule> compiled = new ArrayList<>(rules.size());
        for (RuleDslModel rule : rules) {
            if (!profileSatisfies(rule)) {
                stats.incRulesSkippedByProfile(rule.getId());
                continue;
            }
            compiled.add(CompiledRule.compile(rule, language));
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
     * True when the profile provides every capability the rule requires.
     * Unknown requirement tokens are unsatisfiable by construction: an
     * unshipped capability must not run as if it were present.
     */
    private boolean profileSatisfies(RuleDslModel rule) {
        for (String requirement : rule.getRequires()) {
            LintCapability capability = LintCapability.byToken(requirement);
            if (capability == null || !profile.capabilities().contains(capability)) {
                return false;
            }
        }
        return true;
    }
}
