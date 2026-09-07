package io.nop.treesitter.query;

/**
 * A child pattern inside a node pattern: an optional field name (the
 * {@code key:} prefix in {@code (pair key: (_))}) plus the child node pattern.
 * A null field name means the child is matched positionally.
 */
public record ChildPattern(String fieldName, PatternNode node) {
}