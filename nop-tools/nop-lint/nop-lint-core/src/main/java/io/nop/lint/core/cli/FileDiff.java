package io.nop.lint.core.cli;

import java.util.Objects;

/**
 * One file's proposed rewrite in a {@code --fix-dry-run} (roadmap item 25):
 * the display path plus the ready-to-print unified diff between the on-disk
 * content and the in-memory multipass result. Files a dry-run would not
 * change produce no entry.
 */
public record FileDiff(String displayPath, String unifiedDiff) {

    public FileDiff {
        if (displayPath == null || displayPath.isBlank())
            throw new IllegalArgumentException("displayPath must not be blank");
        Objects.requireNonNull(unifiedDiff, "unifiedDiff must not be null");
    }
}
