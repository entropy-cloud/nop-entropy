package io.nop.refactor.core.operation;

/**
 * The refactor operation SPI (roadmap WI9, baseline §一.3's four-segment
 * contract as a framework): an operation owns exactly the first two
 * segments — {@link #check(Object)} rejects infeasible input with a
 * structured failure, {@link #plan(Object)} computes the per-file edit plan
 * and pre-collects every non-applied entry without writing anything. The
 * last two segments are framework-owned single points: apply goes through
 * the WI4 {@code EditPlanApplier} entry and verify through the WI5
 * {@code RefactorVerifier#assemble}, both driven by
 * {@link RefactorOperationRunner} — an operation never lands or verifies on
 * its own, so rename and codemod share one plan/apply/verify mechanism with
 * no second execution path (roadmap WI9 closure adjudication).
 *
 * <p>The interface is language-agnostic: the input type carries whatever
 * the operation needs (the codemod face's {@link RewriteRequest}, the
 * rename face's定位 triple); core never sees a language AST.</p>
 *
 * @param <I> the operation's input model
 */
public interface RefactorOperation<I> {

    /**
     * Feasibility validation (segment 1): a structured failure for input the
     * operation cannot serve — never a silent pass-through that plan would
     * later fumble.
     */
    void check(I input);

    /**
     * The edit plan (segment 2): per-file rewrites plus pre-collected
     * non-applied entries, computed entirely in memory. Landing and payload
     * assembly happen after this returns, in the framework.
     */
    OperationPlan plan(I input);
}
