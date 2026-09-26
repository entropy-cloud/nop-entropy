package io.nop.refactor.core.symbol;

import java.util.List;
import java.util.Objects;

/**
 * One file's slice of a cross-file rename rewrite set (plan 11 adjudication
 * 2): the display path plus every rewrite span — the authoritative carrier
 * for multi-file renames (the first rung's single-file
 * {@link RenameResolution#occurrences()} remains the target-file
 * compatibility face).
 */
public record FileRewrite(String path, List<RenameSpan> spans) {

    public FileRewrite {
        Objects.requireNonNull(path, "path must not be null");
        spans = List.copyOf(spans);
        if (spans.isEmpty()) {
            throw new io.nop.refactor.core.NopRefactorException("a file rewrite must "
                    + "carry at least one span (an empty entry is a silent no-op; "
                    + "fail-closed)");
        }
    }
}
