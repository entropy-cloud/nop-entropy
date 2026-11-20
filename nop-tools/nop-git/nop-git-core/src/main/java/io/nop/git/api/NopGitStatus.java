package io.nop.git.api;

import java.util.List;

/**
 * Working tree / index status, mirroring the information reported by
 * {@code git status}. All path lists are repo-relative and sorted.
 *
 * @param branch           current branch name, or null when the repository has
 *                         no commit yet (unborn HEAD)
 * @param stagedAdded      paths added to the index (new since HEAD)
 * @param stagedChanged    paths whose index content differs from HEAD
 * @param stagedRemoved    paths removed from the index but present in HEAD
 * @param modified         tracked paths modified in the working tree
 * @param deleted          tracked paths deleted from the working tree
 * @param untracked        untracked paths
 */
public record NopGitStatus(
        String branch,
        List<String> stagedAdded,
        List<String> stagedChanged,
        List<String> stagedRemoved,
        List<String> modified,
        List<String> deleted,
        List<String> untracked) {

    /**
     * @return true when there is no staged, unstaged or untracked change
     */
    public boolean isClean() {
        return stagedAdded.isEmpty() && stagedChanged.isEmpty() && stagedRemoved.isEmpty()
                && modified.isEmpty() && deleted.isEmpty() && untracked.isEmpty();
    }
}
