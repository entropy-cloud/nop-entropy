package io.nop.git.api;

/**
 * One entry of {@code git worktree list}.
 *
 * @param path      absolute (real) path of the working tree
 * @param head      commit sha the worktree HEAD resolves to, or null
 * @param branch    short branch name (e.g. {@code feat1}), or null when
 *                  detached/bare
 * @param bare      true for the bare main worktree
 * @param detached  true when HEAD does not point at a branch
 * @param locked    true when a {@code locked} marker exists
 * @param lockedReason  reason text from the locked file (may be empty)
 * @param prunable  true when the worktree gitdir is missing/unusable
 * @param prunableReason    native-style reason text (e.g.
 *                  {@code gitdir file points to non-existent location})
 */
public record NopGitWorktree(
        String path,
        String head,
        String branch,
        boolean bare,
        boolean detached,
        boolean locked,
        String lockedReason,
        boolean prunable,
        String prunableReason) {
}
