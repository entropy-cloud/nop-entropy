package io.nop.refactor.core;

import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.rule.RuleDslModel;

import java.util.List;

/**
 * The shared ruleset load gate of the rewrite faces (nop-refactor WI6/WI7,
 * single implementation): the rewrite payload face consumes transform rules
 * only, and only rules whose evaluation is unconditional under any profile.
 *
 * <ul>
 *   <li>(a) a rule with no {@code transform} declaration (a reporting /
 *       fix / xscript rule) is rejected — its findings would be silently
 *       dropped by a rewrite-only payload face;</li>
 *   <li>(b) a transform rule with a non-empty {@code requires} is rejected —
 *       the profile gate could SKIP or DEGRADE it and its rewrites would
 *       never be computed (silent missed rewrites). An empty requires always
 *       runs, so "empty or reject" is the conservative zero-duplication
 *       equivalent of the engine's private gate. The gate always judges
 *       against STANDARD regardless of any injected profile.</li>
 * </ul>
 */
public final class RefactorRuleGates {

    private RefactorRuleGates() {
    }

    /**
     * Rejects the loaded ruleset fail-closed when any rule violates the
     * rewrite-face gate; the rule id names the offender in every message.
     */
    public static void verifyRewriteRuleset(RuleSetLoader.LoadedRuleSet loaded,
                                            LanguageRegistry registry) {
        for (List<RuleDslModel> rules : loaded.rulesByLanguage().values()) {
            for (RuleDslModel rule : rules) {
                if (rule.getTransform() == null) {
                    throw new NopRefactorException("ruleset rule '" + rule.getId()
                            + "' is not a transform rule (the rewrite face's payload "
                            + "consumes rewrites only; a reporting rule's findings would be "
                            + "silently dropped — fail-closed)");
                }
                if (!rule.getRequires().isEmpty()) {
                    throw new NopRefactorException("transform rule '" + rule.getId()
                            + "' declares requires " + rule.getRequires()
                            + " (a profile gate could skip or degrade the rule and its "
                            + "rewrites would never be computed; the rewrite face accepts "
                            + "empty-requires transform rules only — fail-closed)");
                }
            }
        }
        for (String language : loaded.rulesByLanguage().keySet()) {
            if (!registry.registeredIds().contains(language)) {
                throw new NopRefactorException("no language binding registered for ruleset "
                        + "language '" + language + "' (registered bindings: "
                        + registry.registeredIds() + "; fail-closed)");
            }
        }
    }
}
