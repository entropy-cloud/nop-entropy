package io.nop.lint.core.pattern;

import java.util.List;

/**
 * A named language construct inside a pattern: matched by kind, then by
 * lockstep traversal of its children (named tokens, terminals, meta-vars in
 * source order).
 *
 * @param kindId   the backend symbol id (the fast filter value)
 * @param children the converted children in source order, unnamed tokens and
 *                 meta-vars included
 */
record InternalNode(int kindId, List<PatternNode> children) implements PatternNode {

    InternalNode {
        children = List.copyOf(children);
    }
}
