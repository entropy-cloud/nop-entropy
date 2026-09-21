package io.nop.lint.core.lang;

import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.language.Language;

/**
 * One lint-supported language: parse entry, pattern preprocessing, and kind
 * name resolution. Language bindings (Java, TypeScript, ...) implement this
 * contract so the rule engine stays language-agnostic.
 */
public interface LintLanguage {

    /**
     * The language identifier used in rules and CLI options, e.g. "java".
     */
    String id();

    /**
     * The backend language this binding drives.
     */
    Language treeSitter();

    /**
     * Parses UTF-8 source into a facade tree.
     */
    LintTree parse(String source);

    /**
     * Parses raw UTF-8 source bytes into a facade tree.
     */
    LintTree parse(byte[] source);

    /**
     * Reparses edited source bytes, reusing the unchanged leaves of
     * {@code oldTree} through the backend's incremental entry. The edit
     * sequence is computed internally (design 03 §1.2); the result satisfies
     * the same equivalence contract as {@link #parse(byte[])}.
     *
     * <p>A null {@code oldTree} falls back to a full parse — the documented,
     * explicit cold-start branch, never a silent path (tests pin it through
     * the reuse counters). {@code oldTree} must come from this binding (the
     * backend validates the language instance) and mismatches fail closed.</p>
     *
     * @param oldTree   the previous facade tree over the old source, or null
     *                  for the explicit full-parse fallback
     * @param newSource the edited UTF-8 source bytes
     */
    LintTree parseIncremental(LintTree oldTree, byte[] newSource);

    /**
     * Rewrites pattern source so the meta-variable syntax survives the
     * language's parser: languages whose identifiers cannot contain the
     * meta-var marker replace it with a parseable placeholder (ast-grep's
     * expando character). Languages whose identifiers already accept the
     * marker return the text unchanged.
     */
    String preprocessPattern(String patternText);

    /**
     * The kind id for a grammar kind name, or -1 when the name is unknown to
     * this language. Resolution covers the backend's full name table including
     * aliased kinds (e.g. Java's {@code type_identifier}); when visible symbols
     * share a name, the named symbol's id wins, matching how patterns address
     * kinds. Built-in recovery kinds such as "ERROR" resolve to -1.
     */
    int kindId(String kindName);
}
