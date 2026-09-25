package io.nop.refactor.core;

/**
 * The self-verification face of a {@link RefactorResult} (baseline §四
 * {@code Verification}): everything the AI needs to judge "did this edit do
 * what I intended" from one response — syntax integrity of every edited
 * file after the rewrite, the residual diagnostics of a configurable rule
 * subset, and (for semantic operations) symbol-reference intactness.
 *
 * @param parseOk             true when every edited file re-parsed with zero
 *                            ERROR/missing recovery nodes (per-file predicate:
 *                            the file's own post-edit state, not a delta
 *                            against the pre-edit parse — a source that was
 *                            already broken stays parseOk=false even when the
 *                            edit added no new breakage; that delta judgement
 *                            is the applier's rollback guard, a different
 *                            semantic face)
 * @param errorNodeCount      total ERROR/missing recovery nodes across the
 *                            edited files after the rewrite
 * @param residualDiagnostics residual findings of the configured rule subset
 *                            over the edited content (0 when no subset is
 *                            configured — the distinguishing signal is
 *                            {@link RefactorStats#residualRuleCount()})
 * @param symbolIntact        rename-class operations only: the target
 *                            symbol's reference count is identical before
 *                            and after the rewrite; null for the codemod
 *                            face (computed by WI9-WI12)
 */
public record Verification(boolean parseOk, int errorNodeCount, int residualDiagnostics,
                           Boolean symbolIntact) {
}
