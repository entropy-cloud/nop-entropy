package io.nop.git.api;

import java.nio.file.Path;

/**
 * Options for {@code git worktree add}.
 *
 * @param path       target working directory for the new worktree
 * @param newBranch  create this branch first ({@code -b}), or null
 * @param commitish  commit/branch to check out, or null for HEAD
 * @param detach     check out into detached HEAD ({@code --detach})
 */
public record AddWorktreeOptions(Path path, String newBranch, String commitish, boolean detach) {
}
