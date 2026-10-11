package io.nop.refactor.core.operation;

import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.suppress.ExemptionFilter;
import io.nop.refactor.core.NonApply;

import java.util.List;
import java.util.Objects;

/**
 * The codemod operation's input (plan 09 adjudication 2, the R1-pinned
 * face/operation boundary): the face has loaded the ruleset, built the
 * exemption filter, wired its own engine (profile choice is face-owned —
 * both production faces pin STANDARD), read the targets behind its own caps
 * into {@link PreparedTarget}s, and pre-collected its collection-phase
 * non-applied entries (unsupported extensions and the like). The operation
 * takes over from the load gate through assemble.
 */
public record RewriteRequest(RuleSetLoader.LoadedRuleSet loadedRuleSet,
                             ExemptionFilter exemptions,
                             LintEngine engine,
                             LanguageRegistry registry,
                             List<PreparedTarget> targets,
                             List<NonApply> collectionNonApplies,
                             String rulesetPrefix) {

    public RewriteRequest {
        Objects.requireNonNull(loadedRuleSet, "loadedRuleSet must not be null");
        Objects.requireNonNull(exemptions, "exemptions must not be null");
        Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(targets, "targets must not be null");
        targets = List.copyOf(targets);
        Objects.requireNonNull(collectionNonApplies,
                "collectionNonApplies must not be null (empty when the face dropped nothing)");
        collectionNonApplies = List.copyOf(collectionNonApplies);
        Objects.requireNonNull(rulesetPrefix, "rulesetPrefix must not be null");
    }
}
