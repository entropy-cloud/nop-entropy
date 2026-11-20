package io.nop.git.api;

/**
 * A git ref.
 *
 * @param name            full ref name, e.g. {@code refs/heads/master} or
 *                        {@code refs/remotes/origin/main}
 * @param shortName       short name, e.g. {@code master} or {@code origin/main}
 * @param objectId        object the ref points to, or null for a symbolic ref
 *                        with no commit yet
 * @param isBranch        true for refs under {@code refs/heads/}
 * @param isRemote        true for refs under {@code refs/remotes/}
 * @param symbolicTarget  full name of the ref this symbolic ref points to
 *                        (e.g. {@code refs/remotes/origin/main} for
 *                        {@code refs/remotes/origin/HEAD}), else null
 */
public record NopGitRef(String name, String shortName, String objectId, boolean isBranch,
                        boolean isRemote, String symbolicTarget) {
}
