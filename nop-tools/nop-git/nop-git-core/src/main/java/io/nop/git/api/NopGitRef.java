package io.nop.git.api;

/**
 * A git ref. Only local branches are reported in Wave 1;
 * {@code branch --all} support arrives with Wave 2.
 *
 * @param name       full ref name, e.g. {@code refs/heads/master}
 * @param shortName  short name, e.g. {@code master}
 * @param objectId   object the ref points to, or null for a symbolic ref
 *                   with no commit yet
 * @param isBranch   true for refs under {@code refs/heads/}
 */
public record NopGitRef(String name, String shortName, String objectId, boolean isBranch) {
}
