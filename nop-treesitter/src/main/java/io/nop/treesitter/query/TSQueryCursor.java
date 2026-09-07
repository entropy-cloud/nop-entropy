package io.nop.treesitter.query;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.cursor.TSTreeCursor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Query executor: walks a parse tree in pre-order via {@link TSTreeCursor}
 * (the same navigation the tree renders its s-expression with — no second tree
 * representation), tries every pattern at every visible node, and emits the
 * matches with their capture sets in walk order.
 *
 * <p>Pattern matching follows the upstream step-machine semantics for the
 * supported subset: a pattern matches at a node when its root matcher matches
 * the node (type / wildcard / anonymous token / alternation) and each child
 * matcher matches a child of the node — field-qualified children are located
 * through the grammar's field map, positional children scan the node's visible
 * children forward (non-matching children are skipped, and a failed deeper
 * step backtracks to the next candidate, mirroring the C runtime's split-state
 * search). A pattern is a partial match: it need not cover all of the node's
 * children. Captures are collected in pattern order — the root step's captures
 * first, then its children left to right.</p>
 *
 * <p>Predicates are evaluated at match time against the text of the captured
 * node; a failing predicate suppresses the match. Node text is sliced from the
 * source bytes by the node's byte range ({@code startByte() .. endByte()},
 * the arena's real padding and size columns).</p>
 */
public final class TSQueryCursor {

    private final TSQuery query;
    private final TSTree tree;
    private final byte[] source;
    private final TSTreeCursor walker;
    private final List<TSQueryMatch> pending = new ArrayList<>();
    private int pendingIndex;
    private boolean started;
    private boolean done;

    /**
     * Creates a cursor over the whole tree. {@code source} must be the exact
     * bytes the tree was parsed from (used for predicate text comparison).
     */
    public TSQueryCursor(TSQuery query, TSTree tree, byte[] source) {
        this.query = query;
        this.tree = tree;
        this.source = source;
        this.walker = tree.cursor();
    }

    /**
     * Creates a cursor over the whole tree with the source text the tree was
     * parsed from.
     */
    public TSQueryCursor(TSQuery query, TSTree tree, String source) {
        this(query, tree, source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The next match in walk order, or null when the tree is exhausted.
     */
    public TSQueryMatch nextMatch() {
        while (pendingIndex >= pending.size()) {
            if (!advance()) {
                return null;
            }
            pending.clear();
            pendingIndex = 0;
            TSNode node = walker.currentNode();
            for (int i = 0; i < query.patternCount(); i++) {
                TSQuery.Pattern pattern = query.pattern(i);
                List<TSQueryMatch.Capture> captures = matchAt(pattern, node);
                if (captures != null) {
                    pending.add(new TSQueryMatch(pattern.patternIndex(), captures));
                }
            }
        }
        return pending.get(pendingIndex++);
    }

    private boolean advance() {
        if (done) {
            return false;
        }
        if (!started) {
            started = true;
            return true;
        }
        if (walker.gotoFirstChild()) {
            return true;
        }
        while (true) {
            if (walker.gotoNextSibling()) {
                return true;
            }
            if (!walker.gotoParent()) {
                done = true;
                return false;
            }
        }
    }

    private List<TSQueryMatch.Capture> matchAt(TSQuery.Pattern pattern, TSNode node) {
        List<TSQueryMatch.Capture> captures = new ArrayList<>();
        if (!matchMatcher(pattern.root(), node, captures)) {
            return null;
        }
        for (TSQuery.Predicate predicate : pattern.predicates()) {
            String text = captureText(predicate.captureId(), captures);
            if (text == null) {
                return null;
            }
            boolean ok = predicate.regex()
                    ? predicate.compiled().matcher(text).matches()
                    : predicate.text().equals(text);
            if (!ok) {
                return null;
            }
        }
        return captures;
    }

    private boolean matchMatcher(TSQuery.Matcher matcher, TSNode node, List<TSQueryMatch.Capture> captures) {
        if (matcher instanceof TSQuery.TypeMatcher type) {
            if (node.effectiveSymbol() != type.symbolId()) {
                return false;
            }
            capture(type.captureId(), node, captures);
            return matchChildren(type.children(), node, captures);
        }
        if (matcher instanceof TSQuery.WildcardMatcher wildcard) {
            if (!node.named()) {
                return false;
            }
            capture(wildcard.captureId(), node, captures);
            return matchChildren(wildcard.children(), node, captures);
        }
        if (matcher instanceof TSQuery.AnonymousMatcher anonymous) {
            if (node.effectiveSymbol() != anonymous.symbolId()) {
                return false;
            }
            capture(anonymous.captureId(), node, captures);
            return matchChildren(anonymous.children(), node, captures);
        }
        if (matcher instanceof TSQuery.AlternationMatcher alternation) {
            for (TSQuery.Matcher element : alternation.elements()) {
                int checkpoint = captures.size();
                if (matchMatcher(element, node, captures)) {
                    capture(alternation.captureId(), node, captures);
                    return true;
                }
                truncate(captures, checkpoint);
            }
            return false;
        }
        throw new TreeSitterException("unknown matcher kind " + matcher.getClass().getName());
    }

    private boolean matchChildren(List<TSQuery.ChildMatcher> steps, TSNode parent,
                                  List<TSQueryMatch.Capture> captures) {
        if (steps.isEmpty()) {
            return true;
        }
        TSTreeCursor cursor = parent.cursor();
        if (!cursor.gotoFirstChild()) {
            return false;
        }
        return matchSteps(parent, steps, 0, cursor, captures);
    }

    private boolean matchSteps(TSNode parent, List<TSQuery.ChildMatcher> steps, int stepIdx,
                               TSTreeCursor cursor, List<TSQueryMatch.Capture> captures) {
        if (stepIdx >= steps.size()) {
            return true;
        }
        TSQuery.ChildMatcher step = steps.get(stepIdx);
        boolean atEnd = false;
        while (!atEnd) {
            TSNode child = cursor.currentNode();
            if (!parent.equals(child.parent())) {
                break;
            }
            boolean fieldOk = step.fieldId() == 0 || cursor.currentFieldId() == step.fieldId();
            if (fieldOk) {
                int checkpoint = captures.size();
                if (matchMatcher(step.matcher(), child, captures)) {
                    atEnd = !cursor.gotoNextSibling();
                    if (matchSteps(parent, steps, stepIdx + 1, cursor, captures)) {
                        return true;
                    }
                    truncate(captures, checkpoint);
                    continue;
                }
            }
            atEnd = !cursor.gotoNextSibling();
        }
        return false;
    }

    private void capture(int captureId, TSNode node, List<TSQueryMatch.Capture> captures) {
        if (captureId < 0) {
            return;
        }
        captures.add(new TSQueryMatch.Capture(captureId, query.captureName(captureId), node));
    }

    private static void truncate(List<TSQueryMatch.Capture> captures, int size) {
        captures.subList(size, captures.size()).clear();
    }

    private String captureText(int captureId, List<TSQueryMatch.Capture> captures) {
        for (TSQueryMatch.Capture capture : captures) {
            if (capture.captureId() == captureId) {
                return nodeText(capture.node(), source);
            }
        }
        return null;
    }

    /**
     * Text of a node sliced from the source bytes by its byte range
     * ({@link TSNode#startByte()} .. {@link TSNode#endByte()}).
     */
    static String nodeText(TSNode node, byte[] source) {
        return new String(source, node.startByte(), node.endByte() - node.startByte(),
                StandardCharsets.UTF_8);
    }
}