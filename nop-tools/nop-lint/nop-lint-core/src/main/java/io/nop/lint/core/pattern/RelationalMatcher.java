package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The four relational operators (design 04 §5), each wrapping one inner
 * node matcher and a {@link StopBy} horizon:
 *
 * <ul>
 * <li><strong>inside</strong> — the candidate sits on the inner matcher's
 * match among its ancestors (the direct parent for the neighbor horizon).</li>
 * <li><strong>has</strong> — the inner matcher matches somewhere among the
 * candidate's descendants (every direct child for the neighbor horizon;
 * pre-order DFS otherwise).</li>
 * <li><strong>follows</strong> — the inner matcher matches a preceding
 * sibling (nearest first).</li>
 * <li><strong>precedes</strong> — the inner matcher matches a following
 * sibling (nearest first).</li>
 * </ul>
 *
 * <p>The optional {@code field} constraint narrows the child slot the
 * relation may traverse (design 04 §5): for {@code has} the search is
 * rooted at the candidate's field child only (neighbor tests that child's
 * direct children, end/rule descend through the child itself); for
 * {@code inside} an ancestor qualifies only when the candidate occupies its
 * declared field slot. {@code follows}/{@code precedes} reject a field at
 * construction — sibling position has no field slot (fail-closed).</p>
 */
public final class RelationalMatcher implements NodeMatcher {

    public enum Op {
        INSIDE, HAS, FOLLOWS, PRECEDES
    }

    private final Op op;
    private final NodeMatcher inner;
    private final StopBy stopBy;
    private final String field;

    public RelationalMatcher(Op op, NodeMatcher inner, StopBy stopBy, String field) {
        this.op = op;
        this.inner = inner;
        this.stopBy = stopBy;
        this.field = field == null || field.isBlank() ? null : field;
        if (field != null && (op == Op.FOLLOWS || op == Op.PRECEDES)) {
            throw new NopLintException("field constraint is only supported on inside/has, not on "
                    + op.name().toLowerCase() + " (sibling relations have no field slot)");
        }
    }

    public Op op() {
        return op;
    }

    public StopBy stopBy() {
        return stopBy;
    }

    public String field() {
        return field;
    }

    @Override
    public boolean matches(LintNode node, MetaVarEnv env) {
        return switch (op) {
            case INSIDE -> matchInside(node, env);
            case HAS -> matchHas(node, env);
            case FOLLOWS -> matchSiblings(node, env, RelationalSequences.previousSiblings(node));
            case PRECEDES -> matchSiblings(node, env, RelationalSequences.nextSiblings(node));
        };
    }

    private boolean matchInside(LintNode node, MetaVarEnv env) {
        LintNode parent = node.parent();
        if (parent == null) {
            return false;
        }
        Iterator<LintNode> ancestors = RelationalSequences.ancestors(node);
        NodeMatcher finder = field == null ? inner : (ancestor, probe) ->
                node.equals(ancestor.childByField(field)) && inner.matches(ancestor, probe);
        return stopBy.find(Collections.singletonList(parent).iterator(), ancestors, finder, env);
    }

    private boolean matchHas(LintNode node, MetaVarEnv env) {
        if (field != null) {
            LintNode fieldChild = node.childByField(field);
            if (fieldChild == null) {
                return false;
            }
            return stopBy.find(fieldChild.children().iterator(),
                    RelationalSequences.descendants(List.of(fieldChild)), inner, env);
        }
        List<LintNode> children = node.children();
        if (children.isEmpty()) {
            return false;
        }
        return stopBy.find(children.iterator(), RelationalSequences.descendants(children), inner, env);
    }

    private boolean matchSiblings(LintNode node, MetaVarEnv env, List<LintNode> siblings) {
        if (siblings.isEmpty()) {
            return false;
        }
        return stopBy.find(Collections.singletonList(siblings.get(0)).iterator(),
                siblings.iterator(), inner, env);
    }
}
