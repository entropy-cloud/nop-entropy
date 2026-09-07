package io.nop.treesitter.query;

import java.util.List;

/**
 * One query pattern: a root node pattern plus the predicates attached to it.
 * Captures live on the {@link PatternNode}s (root, child or alternation
 * element), mirroring the upstream query syntax {@code (node @capture)} and
 * {@code [ ... ] @capture} suffix forms.
 */
public record Pattern(PatternNode root, List<Predicate> predicates) {

    public Pattern {
        predicates = List.copyOf(predicates);
    }
}