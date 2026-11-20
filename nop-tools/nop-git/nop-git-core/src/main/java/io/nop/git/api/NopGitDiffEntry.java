package io.nop.git.api;

/**
 * One file-level diff entry. Line counts follow the git diffstat semantics: a
 * replaced (changed) block contributes to both insertions and deletions.
 *
 * @param changeType  change kind
 * @param oldPath     path on the source side (equals newPath except for renames/copies)
 * @param newPath     path on the target side
 * @param insertions  number of added lines (0 for binary content)
 * @param deletions   number of removed lines (0 for binary content)
 * @param oldBlobSha  abbreviated source-side blob SHA, or {@code 0000000}
 *                    when the file did not exist (added); abbreviations use
 *                    the git default length (7 for small repositories)
 * @param newBlobSha  abbreviated target-side blob SHA, or {@code 0000000}
 *                    when the file no longer exists (deleted)
 * @param fileMode    target-side file mode (e.g. {@code 100644}), or the
 *                    source-side mode for deleted files
 * @param binary      true when either side is binary; insertions/deletions
 *                    are 0 in that case
 */
public record NopGitDiffEntry(
        ChangeType changeType,
        String oldPath,
        String newPath,
        int insertions,
        int deletions,
        String oldBlobSha,
        String newBlobSha,
        String fileMode,
        boolean binary) {
}
