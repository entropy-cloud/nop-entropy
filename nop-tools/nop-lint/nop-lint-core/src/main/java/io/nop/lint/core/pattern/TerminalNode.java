package io.nop.lint.core.pattern;

/**
 * A leaf source token inside a pattern, named or unnamed (upstream ast-grep
 * compiles every meta-var-free leaf to this shape): matched against candidate
 * leaves by kind id <em>and</em> text — a named leaf such as {@code dao} must
 * not match {@code get}.
 *
 * @param kindId the backend symbol id of the token
 * @param text   the token's source text
 * @param named  whether the leaf is a named node (identifier, literal type,
 *               ...) as opposed to an unnamed punctuation/keyword token
 */
record TerminalNode(int kindId, String text, boolean named) implements PatternNode {
}
