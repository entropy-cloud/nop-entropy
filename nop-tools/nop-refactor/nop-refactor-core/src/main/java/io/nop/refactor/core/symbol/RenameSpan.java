package io.nop.refactor.core.symbol;

import io.nop.lint.core.node.SourceRange;

import java.util.Objects;

/**
 * One rewrite span of a rename: the byte range to replace and the exact
 * replacement text. Most spans replace with the new simple name; TYPE-face
 * imports and qualified mentions replace with the new FQN (or its prefixed
 * form) — the per-span text is what makes the cross-file carrier honest
 * (plan 11 adjudication 2).
 */
public record RenameSpan(SourceRange range, String replacement) {

    public RenameSpan {
        Objects.requireNonNull(range, "range must not be null");
        Objects.requireNonNull(replacement, "replacement must not be null");
    }
}
