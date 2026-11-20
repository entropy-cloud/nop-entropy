package io.nop.git.api;

/**
 * Scope of a git config read/write: the repository config or the user
 * (global) config.
 */
public enum GitConfigScope {
    REPOSITORY, GLOBAL
}
