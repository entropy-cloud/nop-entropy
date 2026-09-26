package io.nop.refactor.core.operation;

import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.fix.Fix;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.RefactorRuleGates;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The codemod face as a framework operation (roadmap WI9 adjudication 2):
 * the execution chain WI6/WI7 proved in their two inline copies — load-gate
 * validation, per-target lint under the face-injected engine, the
 * transform-degraded abort, exemption-gated rewrites, and the no-rules
 * out-of-scope enumeration — converged into the {@link RefactorOperation}
 * SPI. From the load gate through assemble there is one implementation;
 * the GraphQL and CLI faces prepare their targets and delegate.
 */
public final class RewriteOperation implements RefactorOperation<RewriteRequest> {

    /**
     * Stateless singleton — the codemod chain has no per-run state (vision
     * principle 2: same input, same result, no session).
     */
    public static final RewriteOperation INSTANCE = new RewriteOperation();

    private RewriteOperation() {
    }

    /**
     * Segment 1: the shared rewrite load gate — only transform rules, only
     * requires-free rules, every language binding registered. Runs before
     * any planning, so an unshippable ruleset is rejected up front.
     */
    @Override
    public void check(RewriteRequest input) {
        RefactorRuleGates.verifyRewriteRuleset(input.loadedRuleSet(), input.registry());
    }

    /**
     * Segment 2: the per-target edit plan — lint each prepared target with
     * the injected engine, abort on a degraded transform generation (a lost
     * rewrite is an abort, never a silent skip), gate the rewrites through
     * the ruleset's exemptions, and merge the face's collection-phase drops
     * ahead of the plan-phase ones. Nothing is written here.
     */
    @Override
    public OperationPlan plan(RewriteRequest input) {
        List<PlannedFile> files = new ArrayList<>();
        List<NonApply> nonApplies = new ArrayList<>();
        Map<String, LintLanguage> languageByPath = new LinkedHashMap<>();

        for (PreparedTarget target : input.targets()) {
            List<RuleDslModel> rules = input.loadedRuleSet().rulesByLanguage()
                    .getOrDefault(target.languageId(), List.of());
            if (rules.isEmpty()) {
                nonApplies.add(new NonApply(NonApply.Reason.OUT_OF_SCOPE, target.path().toString(),
                        "no rules for language '" + target.languageId()
                                + "' in ruleset '" + input.rulesetPrefix() + "'"));
                continue;
            }
            LintLanguage language = input.registry().resolve(target.languageId());
            languageByPath.put(target.path().toString(), language);
            LintResult lint = input.engine().lint(rules, language, target.path().toString(),
                    new String(target.content(), StandardCharsets.UTF_8));
            if (lint.stats().getTransformDegraded() > 0) {
                throw new NopRefactorException("the resource gate closed transform generation for '"
                        + target.path() + "' (" + lint.stats().getTransformDegraded()
                        + " edit(s) lost; a lost rewrite on the rewrite face is an abort, "
                        + "never a silent skip)");
            }

            List<Fix> rewrites = new ArrayList<>(lint.transformFixes().size());
            for (Fix rewrite : lint.transformFixes()) {
                if (input.exemptions().suppresses(rewrite.ruleId(), target.path())) {
                    nonApplies.add(new NonApply(NonApply.Reason.OUT_OF_SCOPE,
                            target.path().toString(), "rewrite from '" + rewrite.ruleId()
                                    + "' exempted by ruleset exemption"));
                } else {
                    rewrites.add(rewrite);
                }
            }
            if (!rewrites.isEmpty()) {
                files.add(new PlannedFile(target.path(), target.content(), rewrites, language));
            }
        }

        // the face's collection-phase drops lead the enumeration, the plan
        // phase's follow — one merged list for the single assemble point
        List<NonApply> merged = new ArrayList<>(input.collectionNonApplies().size()
                + nonApplies.size());
        merged.addAll(input.collectionNonApplies());
        merged.addAll(nonApplies);
        return new OperationPlan(files, new HashMap<>(languageByPath), input.engine(), merged);
    }
}
