package io.nop.lint.core.pattern;

import io.nop.lint.core.lang.LintLanguage;

/**
 * A compiled source pattern: the root {@link PatternNode} subtree, the
 * pattern text it was compiled from, and the precomputed set of root kind ids
 * the matcher can use as an O(1) node filter.
 *
 * <p>Matching semantics land with the matcher kernel; this type is the
 * compiled data that kernel consumes.</p>
 */
public final class SourcePattern {

    private final LintLanguage language;
    private final String patternText;
    private final PatternNode root;
    private final int[] possibleKindIds;

    SourcePattern(LintLanguage language, String patternText, PatternNode root, int[] possibleKindIds) {
        this.language = language;
        this.patternText = patternText;
        this.root = root;
        this.possibleKindIds = possibleKindIds;
    }

    /**
     * The language the pattern was compiled for; matching is bound to it.
     */
    public LintLanguage language() {
        return language;
    }

    /**
     * The pattern text this was compiled from (diagnostics, rule tracing).
     */
    public String patternText() {
        return patternText;
    }

    /**
     * The root pattern node — the match target. An {@link InternalNode} here
     * matches any candidate node of its kind whose children lockstep-match;
     * a {@link MetaVarNode} matches arbitrary (named) nodes.
     */
    public PatternNode root() {
        return root;
    }

    /**
     * Kind ids the root node can possibly match, for O(1) filtering; empty
     * when the root is a meta-variable and any kind can match. Ids come from
     * {@link LintLanguage#kindId(String)} resolution space (backend symbol
     * table, aliases included).
     */
    public int[] possibleKindIds() {
        return possibleKindIds;
    }

    /**
     * True when a candidate node with the given kind id can possibly match
     * this pattern's root (fast pre-filter; empty filter means no opinion).
     */
    public boolean mayMatchKind(int kindId) {
        if (possibleKindIds.length == 0) {
            return true;
        }
        for (int candidate : possibleKindIds) {
            if (candidate == kindId) {
                return true;
            }
        }
        return false;
    }
}
