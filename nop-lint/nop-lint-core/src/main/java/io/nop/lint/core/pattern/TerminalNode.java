package io.nop.lint.core.pattern;

/**
 * An unnamed source token inside a pattern (punctuation, keywords): matched
 * against candidate nodes by kind and text under the active strictness.
 *
 * @param kindId the backend symbol id of the token
 * @param text   the token's source text
 */
record TerminalNode(int kindId, String text) implements PatternNode {
}
