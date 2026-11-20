package io.nop.git.api;

/**
 * One file-level diff entry. Line counts follow the git diffstat semantics: a
 * replaced (changed) block contributes to both insertions and deletions.
 * Binary files report 0/0.
 *
 * @param changeType  change kind
 * @param oldPath     path on the source side (equals newPath except for renames/copies)
 * @param newPath     path on the target side
 * @param insertions  number of added lines (0 for binary content)
 * @param deletions   number of removed lines (0 for binary content)
 */
public record NopGitDiffEntry(
        ChangeType changeType,
        String oldPath,
        String newPath,
        int insertions,
        int deletions) {
}
