package io.nop.git.api;

/**
 * A staged rename detected between HEAD and the index
 * ({@code git status --porcelain} renders it as {@code R  old -> new}).
 *
 * @param oldPath  path in HEAD
 * @param newPath  path in the index
 */
public record NopGitRename(String oldPath, String newPath) {
}
