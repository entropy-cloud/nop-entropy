package io.nop.git.api;

/**
 * Change type of a diff entry, aligned with git's A/M/D/R/C status letters.
 */
public enum ChangeType {
    ADDED, MODIFIED, DELETED, RENAMED, COPIED
}
