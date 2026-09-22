package io.nop.lint.core.xml;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.pattern.NodeMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * The XNode matching kernel (design 01 §3.5): matches a compiled
 * {@link XNodePattern} against element candidates with the dimension
 * contract of §3.5 — tag equality (the kind match), the open-world attribute
 * constraint set, trimmed text matching with the bare-{@code $$$} wildcard,
 * and closed lockstep children matching with comment/text trivia skipped
 * (Smart strictness; XNode has no other trivial-node layer).
 *
 * <p>Meta-var semantics are the tree-sitter path's: captures go through
 * {@link MetaVarEnv} (probe-clone and commit-on-success, same-name
 * consistency, non-capturing underscore names), so a capture behaves
 * identically on both paths. Comment nodes never match and never block a
 * match — the §3.5 Smart row.</p>
 */
public final class XNodePatternMatcher implements NodeMatcher {

    private final XNodePattern pattern;

    public XNodePatternMatcher(XNodePattern pattern) {
        this.pattern = pattern;
    }

    public XNodePattern pattern() {
        return pattern;
    }

    /**
     * Finds every element in {@code root}'s subtree (root included) that
     * matches this pattern, pre-order.
     */
    public List<Match> findMatches(LintNode root) {
        List<Match> matches = new ArrayList<>();
        for (LintNode candidate : root) {
            if (candidate.kindId() != pattern.tagKindId()) {
                continue;
            }
            MetaVarEnv env = new MetaVarEnv();
            if (matchGoal(pattern, candidate, env)) {
                matches.add(new Match(candidate, env));
            }
        }
        return matches;
    }

    @Override
    public boolean matches(LintNode node, MetaVarEnv env) {
        if (node.kindId() != pattern.tagKindId()) {
            return false;
        }
        MetaVarEnv probe = env.clone();
        if (matchGoal(pattern, node, probe)) {
            env.adopt(probe);
            return true;
        }
        return false;
    }

    /**
     * Matches one pattern goal (root or child) against one candidate element:
     * tag, then the attribute constraint set, then the text constraint, then
     * the children sequence.
     */
    private boolean matchGoal(XNodePattern goal, LintNode candidate, MetaVarEnv env) {
        if (candidate.kindId() != goal.tagKindId()) {
            return false;
        }
        if (!(candidate instanceof XNodeLintNode element)
                || element.flavor() != XNodeLintNode.Flavor.ELEMENT) {
            return false;
        }
        return matchAttrs(goal, element, env) && matchText(goal, element, env)
                && matchChildren(goal.children(), element.namedChildren(), env);
    }

    private boolean matchAttrs(XNodePattern goal, XNodeLintNode element, MetaVarEnv env) {
        for (XNodePattern.AttrSpec attr : goal.attrs()) {
            String value = element.attrValue(attr.name());
            switch (attr.spec()) {
                case LITERAL -> {
                    if (value == null || !value.equals(attr.literal())) {
                        return false;
                    }
                }
                case CAPTURE -> {
                    if (value == null
                            || !env.insert(attr.captureName(), new XmlValueNode(value, element.range()))) {
                        return false;
                    }
                }
                case DROP -> {
                    // Present or absent, any value (§3.5: "缺省属性不匹配，
                    // 除非用 $_").
                }
                case WILDCARD -> {
                    // Bare $$$: the attribute must exist, the value is free.
                    if (value == null) {
                        return false;
                    }
                }
                default -> throw new IllegalStateException(
                        "unreachable attribute value spec: " + attr.spec());
            }
        }
        return true;
    }

    private boolean matchText(XNodePattern goal, XNodeLintNode element, MetaVarEnv env) {
        return switch (goal.textSpec()) {
            case ANY, WILDCARD, DROP -> true;
            case LITERAL -> element.contentText().trim().equals(goal.literalText());
            case CAPTURE -> env.insert(goal.textCaptureName(),
                    new XmlValueNode(element.contentText().trim(), element.range()));
        };
    }

    /**
     * Closed lockstep over the element children: the pattern's children must
     * pair up one-to-one in order (trivia never appears in
     * {@code namedChildren}; an undeclared sequence is the open-world
     * wildcard). The sequence matches in a probe clone that commits only on
     * overall success, so a failing child pair never leaks its captures.
     */
    private boolean matchChildren(List<XNodePattern> goals, List<LintNode> candidates,
                                  MetaVarEnv env) {
        if (goals.isEmpty()) {
            return true;
        }
        if (goals.size() != candidates.size()) {
            return false;
        }
        MetaVarEnv probe = env.clone();
        for (int i = 0; i < goals.size(); i++) {
            if (!matchGoal(goals.get(i), candidates.get(i), probe)) {
                return false;
            }
        }
        env.adopt(probe);
        return true;
    }
}
