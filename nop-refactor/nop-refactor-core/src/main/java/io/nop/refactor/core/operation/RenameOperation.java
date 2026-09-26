package io.nop.refactor.core.operation;

import io.nop.refactor.core.NopRefactorException;

/**
 * The rename skeleton operation (roadmap WI9, plan 09 adjudication 5): it
 * rides the same framework lifecycle as the codemod face — check validates
 * the input triple's shape, and plan fails closed with the exact
 * not-yet-implemented marker until the resolution semantics land (WI10:
 * locals/parameters single-file; WI11: fields/methods/types module-scoped).
 * The fail-closed throw is the Minimum Rules #24-compliant explicit failure:
 * a caller can never mistake an unimplemented rename for a no-op success.
 * Apply and verify are inherited unchanged from the framework's single
 * path — a rename never grows a second landing mechanism.
 */
public final class RenameOperation implements RefactorOperation<RenameRequest> {

    /**
     * The exact plan-phase marker (plan 09 adjudication 5: the message is
     * pinned by test — a drifted marker is a drifted contract).
     */
    public static final String NOT_IMPLEMENTED_MESSAGE =
            "not yet implemented: rename symbol resolution lands in WI10";

    /**
     * Stateless singleton, like the codemod operation — one framework, no
     * per-run state.
     */
    public static final RenameOperation INSTANCE = new RenameOperation();

    private RenameOperation() {
    }

    /**
     * Segment 1: the input triple's shape validation — the locator form is
     * enforced by {@code SymbolTarget}'s own fail-closed constructor; the
     * name and scope checks complete the triple here.
     */
    @Override
    public void check(RenameRequest input) {
        // construction of RenameRequest already validated name and scope;
        // the locator form check runs here so check alone is a complete gate
        if (input.target() == null) {
            throw new NopRefactorException("a rename requires a non-null target locator");
        }
    }

    /**
     * Segment 2: fail-closed until WI10 lands the resolution semantics —
     * never a silent no-op plan.
     */
    @Override
    public OperationPlan plan(RenameRequest input) {
        throw new NopRefactorException(NOT_IMPLEMENTED_MESSAGE);
    }
}
