package io.nop.treesitter.query;

import java.util.List;

/**
 * AST root for a parsed tree-sitter query source: an ordered list of patterns.
 * Produced by {@link TSQueryParser#parse}; consumed by {@link TSQuery#compile}.
 */
public record Query(List<Pattern> patterns) {

    public Query {
        patterns = List.copyOf(patterns);
    }
}