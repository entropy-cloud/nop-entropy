package io.nop.refactor.core.operation;

import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.fix.EditPlanApplier;
import io.nop.lint.core.fix.Fix;
import io.nop.refactor.core.EditedFile;
import io.nop.refactor.core.FileEdit;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.RefactorResult;
import io.nop.refactor.core.RefactorVerifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The framework execution template (roadmap WI9 adjudication 1): check,
 * then plan, then the two framework-owned segments. Apply is the WI4
 * {@link EditPlanApplier#apply} entry — this class holds the only
 * EditPlanApplier call site in nop-refactor's src/main; verify is the WI5
 * {@link RefactorVerifier#assemble} — the only assemble call site. Every
 * operation (the codemod {@link RewriteOperation}, the rename skeleton, any
 * future language operation) lands and verifies through this one template,
 * which is the wiring proof that rename and codemod share one
 * plan/apply/verify mechanism with no second execution path.
 *
 * <p>Landing discipline (plan 09 adjudication 3, the convergence the two
 * former inline chains unify on): the plan is fully computed before the
 * first write; a mid-apply failure after partial landing surfaces the
 * already-landed files in the error (never a faked success); conflict and
 * guard-rollback drops become structured {@code NonApply} entries on the
 * same payload as everything else.</p>
 */
public enum RefactorOperationRunner {

    /**
     * The stateless singleton — the runner owns no state beyond the run
     * arguments (stateless re-execution, vision principle 2).
     */
    INSTANCE;

    /**
     * Runs the operation through the four segments. {@code dryRun = true}
     * computes and verifies everything but writes nothing (the WI4 entry's
     * dry-run); {@code false} lands atomically.
     */
    public <I> RefactorResult run(RefactorOperation<I> operation, I input, boolean dryRun) {
        operation.check(input);
        OperationPlan plan = operation.plan(input);

        List<EditedFile> files = new ArrayList<>();
        List<FileEdit> edits = new ArrayList<>();
        List<NonApply> nonApplies = new ArrayList<>(plan.nonApplies());
        List<String> landed = new ArrayList<>();
        boolean rolledBack = false;

        // segment 3 — framework apply: the single EditPlanApplier call site
        for (PlannedFile planned : plan.files()) {
            EditPlanApplier.EditPlanResult result;
            try {
                result = EditPlanApplier.apply(planned.path(), planned.original(),
                        planned.edits(), planned.language(), dryRun);
            } catch (RuntimeException e) {
                if (!dryRun && !landed.isEmpty()) {
                    throw new NopRefactorException("apply failed at '" + planned.path()
                            + "' after " + landed.size() + " file(s) already landed ("
                            + String.join(", ", landed) + "); " + (plan.files().size()
                            - landed.size()) + " file(s) not attempted; the failure is: "
                            + e.getMessage(), e);
                }
                throw e;
            }
            for (Fix skipped : result.skippedEdits()) {
                nonApplies.add(new NonApply(NonApply.Reason.CONFLICT, planned.path().toString(),
                        "overlaps an earlier-priority edit from '" + skipped.ruleId() + "'"));
            }
            if (result.rolledBack()) {
                nonApplies.add(new NonApply(NonApply.Reason.ROLLED_BACK, planned.path().toString(),
                        "guard rollback: the rewrites broke the file's syntax, the content "
                                + "was restored to its pre-edit state"));
                rolledBack = true;
                continue;
            }
            if (result.appliedFixes().isEmpty()) {
                continue;
            }
            for (Fix applied : result.appliedFixes()) {
                edits.add(new FileEdit(planned.path().toString(), applied.range(),
                        applied.description()));
            }
            files.add(new EditedFile(planned.path().toString(), planned.original(),
                    result.finalSource(), result.appliedEdits()));
            landed.add(planned.path().toString());
        }

        // segment 4 — framework verify: the single assemble call site; a
        // rolled-back file voids the rename assertion (its input never landed)
        Boolean symbolIntact = rolledBack ? null : plan.symbolIntact();
        try {
            RefactorVerifier verifier = new RefactorVerifier(plan.languageByPath()::get,
                    plan.engine(), List.of());
            return verifier.assemble(!dryRun, files, edits, nonApplies, symbolIntact);
        } catch (RuntimeException e) {
            if (!dryRun && !landed.isEmpty()) {
                throw new NopRefactorException("apply failed after " + landed.size()
                        + " file(s) already landed (" + String.join(", ", landed)
                        + "); the failure is: " + e.getMessage(), e);
            }
            throw e;
        }
    }
}
