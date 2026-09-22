package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;

import java.util.Iterator;

/**
 * The traversal horizon of a relational matcher (design 04 §5):
 *
 * <ul>
 * <li><strong>neighbor</strong> — only the directly adjacent nodes: the
 * caller-supplied neighbor sequence, which is one traversal step from the
 * base node (the direct parent for {@code inside}, every direct child for
 * {@code has}, the single adjacent sibling for {@code follows}/
 * {@code precedes}).</li>
 * <li><strong>end</strong> — the full {@code multi} sequence.</li>
 * <li><strong>rule</strong> — the {@code multi} sequence taken up to and
 * <em>including</em> the first node matching the stop matcher
 * ({@code inclusive_until}: the stop node itself participates in the inner
 * match before the traversal halts).</li>
 * </ul>
 *
 * <p>Every inner match runs in a probe clone of the caller's environment;
 * the first success is committed, and a stop-rule match never binds
 * anything (its probe is discarded).</p>
 */
public final class StopBy {

    public enum Mode {
        NEIGHBOR, END, RULE
    }

    private final Mode mode;
    private final NodeMatcher stopRule;

    private StopBy(Mode mode, NodeMatcher stopRule) {
        this.mode = mode;
        this.stopRule = stopRule;
        if (mode == Mode.RULE && stopRule == null) {
            throw new NopLintException("StopBy rule mode requires a stop matcher (fail-closed)");
        }
    }

    public static StopBy neighbor() {
        return new StopBy(Mode.NEIGHBOR, null);
    }

    public static StopBy end() {
        return new StopBy(Mode.END, null);
    }

    public static StopBy rule(NodeMatcher stopRule) {
        return new StopBy(Mode.RULE, stopRule);
    }

    public Mode mode() {
        return mode;
    }

    /**
     * Walks the horizon-bound sequence, offering nodes to {@code finder}
     * until the first inner match commits or the horizon is exhausted
     * (rule mode: until the first stop-matching node has been offered).
     */
    public boolean find(Iterator<LintNode> neighbor, Iterator<LintNode> multi, NodeMatcher finder,
                        MetaVarEnv env) {
        if (mode == Mode.NEIGHBOR) {
            while (neighbor.hasNext()) {
                if (tryMatch(finder, neighbor.next(), env)) {
                    return true;
                }
            }
            return false;
        }
        while (multi.hasNext()) {
            LintNode node = multi.next();
            if (tryMatch(finder, node, env)) {
                return true;
            }
            if (mode == Mode.RULE && matchesStop(node)) {
                // inclusive_until: the stop node itself has just been
                // offered to the finder; the traversal halts after it.
                return false;
            }
        }
        return false;
    }

    private boolean tryMatch(NodeMatcher finder, LintNode node, MetaVarEnv env) {
        MetaVarEnv probe = env.clone();
        if (finder.matches(node, probe)) {
            env.adopt(probe);
            return true;
        }
        return false;
    }

    private boolean matchesStop(LintNode node) {
        return stopRule.matches(node, new MetaVarEnv());
    }
}
