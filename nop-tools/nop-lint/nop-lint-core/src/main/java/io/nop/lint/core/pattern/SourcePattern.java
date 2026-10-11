package io.nop.lint.core.pattern;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Matches this pattern against every node of {@code root}'s subtree
     * (root included) under SMART strictness.
     */
    public List<Match> matchIn(LintNode root) {
        return PatternMatcher.findMatches(this, root);
    }

    /**
     * {@link #matchIn(LintNode)} with an explicit strictness.
     */
    public List<Match> matchIn(LintNode root, Strictness strictness) {
        return PatternMatcher.findMatches(this, root, strictness);
    }

    /**
     * The single-node capture names this pattern declares ({@code $VAR} and
     * {@code $$VAR} occurrences with a capturing name — drop names starting
     * with {@code _} are excluded by convention). The constraint compiler
     * checks its capture references against this set (roadmap item 22).
     */
    public Set<String> captureNames() {
        Set<String> names = new HashSet<>();
        collectCaptures(root, names, null);
        return names;
    }

    /**
     * The sequence capture names ({@code $$$VAR}) this pattern declares.
     * Constraints cannot reference sequence captures; the set is kept
     * separate so the constraint compiler can reject such references with a
     * precise reason instead of a generic "not declared".
     */
    public Set<String> multiCaptureNames() {
        Set<String> names = new HashSet<>();
        collectCaptures(root, null, names);
        return names;
    }

    private static void collectCaptures(PatternNode node, Set<String> singleNames, Set<String> multiNames) {
        if (node instanceof MetaVarNode metaVar) {
            if (metaVar.captures()) {
                if (metaVar.shape() == MetaVarNode.Shape.MULTI) {
                    if (multiNames != null)
                        multiNames.add(metaVar.name());
                } else if (singleNames != null) {
                    singleNames.add(metaVar.name());
                }
            }
            return;
        }
        if (node instanceof InternalNode internal) {
            for (PatternNode child : internal.children()) {
                collectCaptures(child, singleNames, multiNames);
            }
        }
    }
}
