package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The pattern matching kernel (design 04 §2–§4): matches a compiled
 * {@link SourcePattern} against candidate nodes with lockstep child
 * traversal, ellipsis lookahead probes with environment rollback, and the
 * SMART/AST strictness decision matrix.
 *
 * <p>Lockstep decision per goal/candidate pair (upstream match_terminal
 * result families): a consuming match advances both sides; a failure steps
 * over skippable candidates (SMART: unnamed and comment extras; the candidate
 * comment rule applies to meta-vars too); a failing <em>unnamed</em> goal
 * terminal skips the candidate under SMART and both sides under AST; any
 * other failure aborts the match. Trailing candidates must all be skippable;
 * trailing goals must be skippable goals (AST unnamed terminals, or the
 * zero-width tail ellipsis).</p>
 */
public final class PatternMatcher {

    private PatternMatcher() {
    }

    /**
     * Finds every node in {@code root}'s subtree (root included) that matches
     * {@code pattern} under SMART strictness, pre-order.
     */
    public static List<Match> findMatches(SourcePattern pattern, LintNode root) {
        return findMatches(pattern, root, Strictness.SMART);
    }

    /**
     * Finds every node in {@code root}'s subtree (root included) that matches
     * {@code pattern} under {@code strictness}, pre-order. Kind filtering
     * uses the pattern's precomputed root kinds; a meta-var root matches
     * every named node by design.
     */
    public static List<Match> findMatches(SourcePattern pattern, LintNode root, Strictness strictness) {
        List<Match> matches = new ArrayList<>();
        for (LintNode candidate : root) {
            if (!pattern.mayMatchKind(candidate.kindId())) {
                continue;
            }
            MetaVarEnv env = new MetaVarEnv();
            if (matchRoot(pattern.root(), candidate, env, strictness)) {
                matches.add(new Match(candidate, env));
            }
        }
        return matches;
    }

    /**
     * Root-level match: strict consumption only — the skip families exist to
     * align sibling sequences inside a lockstep and never apply to the root
     * pair itself.
     */
    private static boolean matchRoot(PatternNode goal, LintNode candidate, MetaVarEnv env, Strictness strictness) {
        if (goal instanceof TerminalNode terminal) {
            return terminal.kindId() == candidate.kindId()
                    && terminal.text().equals(candidate.text());
        }
        if (goal instanceof MetaVarNode metaVar) {
            return switch (metaVar.shape()) {
                case SINGLE -> candidate.isNamed() && env.insert(metaVar.name(), candidate);
                case ANONYMOUS -> env.insert(metaVar.name(), candidate);
                case DROP -> true;
                default -> throw new NopLintException(
                        "multi meta-var cannot be a pattern root");
            };
        }
        InternalNode internal = (InternalNode) goal;
        return internal.kindId() == candidate.kindId()
                && matchChildren(internal.children(), 0, candidate.children(), 0, env, strictness);
    }

    private enum Step {
        MATCHED,
        SKIP_CANDIDATE,
        SKIP_GOAL,
        FAIL
    }

    /**
     * Lockstep traversal of one goal/candidate child sequence from the given
     * indices; bindings land in {@code env}, which is only committed on
     * overall success of the enclosing match (probe envs are clones).
     */
    private static boolean matchChildren(List<PatternNode> goals, int gi,
                                         List<LintNode> candidates, int ci,
                                         MetaVarEnv env, Strictness strictness) {
        while (true) {
            if (gi == goals.size()) {
                for (int k = ci; k < candidates.size(); k++) {
                    if (!strictness.canSkipCandidate(candidates.get(k))) {
                        return false;
                    }
                }
                return true;
            }
            PatternNode goal = goals.get(gi);
            if (goal instanceof MetaVarNode metaVar && metaVar.shape() == MetaVarNode.Shape.MULTI) {
                return matchEllipsis(metaVar, goals, gi + 1, candidates, ci, env, strictness);
            }
            if (ci >= candidates.size()) {
                return remainingGoalsSkippable(goals, gi, strictness);
            }
            switch (step(goal, candidates.get(ci), env, strictness)) {
                case MATCHED:
                    gi++;
                    ci++;
                    break;
                case SKIP_CANDIDATE:
                    ci++;
                    break;
                case SKIP_GOAL:
                    gi++;
                    break;
                default:
                    return false;
            }
        }
    }

    private static Step step(PatternNode goal, LintNode candidate, MetaVarEnv env, Strictness strictness) {
        if (goal instanceof TerminalNode terminal) {
            if (terminal.kindId() == candidate.kindId()
                    && terminal.text().equals(candidate.text())) {
                return Step.MATCHED;
            }
            // Candidate-keyed stepping: whether a mismatched pair may part
            // ways depends on the CANDIDATE's skippability — otherwise a
            // lone unnamed goal would hop over named siblings (pattern f()
            // would match f(1, 2)).
            if (strictness.canSkipCandidate(candidate)) {
                return Step.SKIP_CANDIDATE;
            }
            // AST additionally drops failing unnamed goal terminals.
            if (!terminal.named() && strictness.skipGoalUnnamed()) {
                return Step.SKIP_GOAL;
            }
            return Step.FAIL;
        }
        if (goal instanceof MetaVarNode metaVar) {
            // A comment in front of a capture position is stepped over
            // (SMART); the capture itself still requires a consumable node.
            if (candidate.isExtra() && strictness == Strictness.SMART) {
                return Step.SKIP_CANDIDATE;
            }
            switch (metaVar.shape()) {
                case SINGLE:
                    if (!candidate.isNamed()) {
                        return Step.FAIL;
                    }
                    return env.insert(metaVar.name(), candidate) ? Step.MATCHED : Step.FAIL;
                case ANONYMOUS:
                    return env.insert(metaVar.name(), candidate) ? Step.MATCHED : Step.FAIL;
                case DROP:
                    return Step.MATCHED;
                default:
                    throw new NopLintException("multi meta-var outside child sequence");
            }
        }
        InternalNode internal = (InternalNode) goal;
        if (internal.kindId() == candidate.kindId()
                && matchChildren(internal.children(), 0, candidate.children(), 0, env, strictness)) {
            return Step.MATCHED;
        }
        return Step.FAIL;
    }

    /**
     * Ellipsis matching with lookahead probes (design 04 §3): accumulate
     * candidates until the next goal matches, then commit the accumulated
     * capture; probe failures extend the accumulation. A trailing ellipsis
     * swallows all remaining candidates; a non-trailing one fails when
     * candidates run out; consecutive ellipses close the first after exactly
     * one candidate (upstream consume-one semantics).
     */
    private static boolean matchEllipsis(MetaVarNode metaVar, List<PatternNode> goals, int giNext,
                                         List<LintNode> candidates, int ci,
                                         MetaVarEnv env, Strictness strictness) {
        boolean last = giNext == goals.size();
        if (last) {
            env.insertMulti(metaVar.name(), candidates.subList(ci, candidates.size()));
            return true;
        }
        PatternNode next = goals.get(giNext);
        if (next instanceof MetaVarNode nextVar && nextVar.shape() == MetaVarNode.Shape.MULTI) {
            if (ci >= candidates.size()) {
                return matchChildren(goals, giNext, candidates, ci, env, strictness);
            }
            env.insertMulti(metaVar.name(), candidates.subList(ci, ci + 1));
            return matchChildren(goals, giNext, candidates, ci + 1, env, strictness);
        }
        for (int split = ci; split < candidates.size(); split++) {
            MetaVarEnv probe = env.clone();
            probe.insertMulti(metaVar.name(), candidates.subList(ci, split));
            if (matchNodeAt(next, candidates.get(split), probe, strictness)
                    && matchChildren(goals, giNext + 1, candidates, split + 1, probe, strictness)) {
                env.adopt(probe);
                return true;
            }
        }
        // Exhausted candidates: the remaining goals must all be skippable.
        if (remainingGoalsSkippable(goals, giNext, strictness)) {
            MetaVarEnv probe = env.clone();
            probe.insertMulti(metaVar.name(), candidates.subList(ci, candidates.size()));
            env.adopt(probe);
            return true;
        }
        return false;
    }

    /**
     * Root-strict single-node match with a probe environment (used by the
     * ellipsis lookahead for the next goal).
     */
    private static boolean matchNodeAt(PatternNode goal, LintNode candidate, MetaVarEnv env, Strictness strictness) {
        return step(goal, candidate, env, strictness) == Step.MATCHED;
    }

    private static boolean remainingGoalsSkippable(List<PatternNode> goals, int gi, Strictness strictness) {
        for (int k = gi; k < goals.size(); k++) {
            PatternNode goal = goals.get(k);
            if (goal instanceof MetaVarNode metaVar && metaVar.shape() == MetaVarNode.Shape.MULTI) {
                continue;
            }
            if (goal instanceof TerminalNode terminal && !terminal.named()
                    && strictness.skipGoalUnnamed()) {
                continue;
            }
            return false;
        }
        return true;
    }
}
