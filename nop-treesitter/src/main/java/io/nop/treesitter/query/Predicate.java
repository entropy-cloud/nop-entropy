package io.nop.treesitter.query;

/**
 * A predicate attached to a query pattern ({@code (#eq? @cap "text")} /
 * {@code (#match? @cap "regex")}). Predicates are evaluated at match time
 * against the text of the referenced capture; a failing predicate suppresses
 * the match, mirroring upstream tree-sitter predicate semantics.
 */
public sealed interface Predicate permits Predicate.Eq, Predicate.Match {

    /**
     * The capture name the predicate inspects.
     */
    String captureName();

    /**
     * The expected text ({@code #eq?}) or the regular expression ({@code #match?}).
     */
    String value();

    record Eq(String captureName, String text) implements Predicate {
        @Override
        public String value() {
            return text;
        }
    }

    record Match(String captureName, String regex) implements Predicate {
        @Override
        public String value() {
            return regex;
        }
    }
}