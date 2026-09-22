package io.nop.lint.core.lang;

import io.nop.lint.core.engine.CompiledRule;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.suppress.SuppressionProvider;
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
     * The backend language this binding drives, or null for bindings that do
     * not sit on a tree-sitter grammar — the XNode XML path (design 01 §1/§3.5)
     * parses through the platform's own parser and has no grammar blob. A
     * null backend never reaches the tree-sitter machinery: the XML binding
     * also compiles its rules through {@link #compileRule(RuleDslModel)} and
     * rejects incremental parsing fail-closed.
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
     * backend validates the language instance) and mismatches fail closed.
     * Bindings without an incremental backend (the XML path) reject this
     * entry fail-closed instead of degrading silently.</p>
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

    /**
     * Compiles one rule model into the engine's executable form, or null to
     * fall through to the engine's default tree-sitter compilation matrix
     * ({@code CompiledRule.compileTreeSitter}). Bindings whose pattern
     * substrate is not tree-sitter (the XNode XML path, design 01 §3.5)
     * override this and return a precompiled rule through
     * {@code CompiledRule.precompiled}; the engine treats both paths
     * identically downstream — kind filtering, matching, xscript, the
     * suppression tail, and the stats counters see no difference, so a rule
     * keeps exactly one observable exit whichever substrate compiled it.
     */
    default CompiledRule compileRule(RuleDslModel model) {
        return null;
    }

    /**
     * The language's annotation-carried suppression extractor (design 09 §3,
     * e.g. Java's {@code @SuppressWarnings}), or null when the language
     * declares none. Lifecycle-coupled to the binding on purpose: annotation
     * scope extraction requires the language's grammar, so a provider is
     * only ever meaningful together with its own binding — the core engine
     * consumes the contract without any grammar knowledge.
     */
    default SuppressionProvider suppressionProvider() {
        return null;
    }
}
