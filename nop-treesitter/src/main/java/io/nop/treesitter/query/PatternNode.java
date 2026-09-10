package io.nop.treesitter.query;

import java.util.List;

/**
 * A node pattern in the query AST: either a named node type
 * ({@code (pair ...)}), the wildcard ({@code (_)}), an anonymous literal
 * ({@code ":"}), or an alternation group ({@code [ (null) (true) ]}).
 *
 * <p>A node pattern carries its own children (each possibly field-qualified)
 * and an optional capture name. Alternations carry elements instead of
 * children — an element is itself a full node pattern.</p>
 */
public sealed interface PatternNode permits PatternNode.Type, PatternNode.Wildcard,
        PatternNode.Anonymous, PatternNode.Alternation {

    /**
     * Capture name attached to this node, or null when the node is not captured.
     */
    String capture();

    /**
     * Child patterns of this node. Empty for leaves and for alternations.
     */
    List<ChildPattern> children();

    /**
     * Returns the same node with the given capture attached (null clears the capture).
     */
    PatternNode withCapture(String capture);

    /**
     * Returns the same node with the given children attached (alternations ignore children).
     */
    PatternNode withChildren(List<ChildPattern> children);

    /**
     * A named-node pattern: matches a node of the given grammar type.
     */
    record Type(String typeName, String capture, List<ChildPattern> children) implements PatternNode {
        public Type {
            children = children == null ? List.of() : List.copyOf(children);
        }

        @Override
        public PatternNode withCapture(String capture) {
            return new Type(typeName, capture, children);
        }

        @Override
        public PatternNode withChildren(List<ChildPattern> children) {
            return new Type(typeName, capture, children);
        }
    }

    /**
     * A wildcard pattern ({@code (_)}) matching any node type.
     */
    record Wildcard(String capture, List<ChildPattern> children) implements PatternNode {
        public Wildcard {
            children = children == null ? List.of() : List.copyOf(children);
        }

        @Override
        public PatternNode withCapture(String capture) {
            return new Wildcard(capture, children);
        }

        @Override
        public PatternNode withChildren(List<ChildPattern> children) {
            return new Wildcard(capture, children);
        }
    }

    /**
     * An anonymous-token pattern (e.g. {@code "function"}) matching the literal token.
     */
    record Anonymous(String text, String capture, List<ChildPattern> children) implements PatternNode {
        public Anonymous {
            children = children == null ? List.of() : List.copyOf(children);
        }

        @Override
        public PatternNode withCapture(String capture) {
            return new Anonymous(text, capture, children);
        }

        @Override
        public PatternNode withChildren(List<ChildPattern> children) {
            return new Anonymous(text, capture, children);
        }
    }

    /**
     * An alternation pattern ({@code [a b c]}) matching any one of the elements.
     */
    record Alternation(List<PatternNode> elements, String capture) implements PatternNode {
        public Alternation {
            elements = List.copyOf(elements);
        }

        @Override
        public List<ChildPattern> children() {
            return List.of();
        }

        @Override
        public PatternNode withCapture(String capture) {
            return new Alternation(elements, capture);
        }

        @Override
        public PatternNode withChildren(List<ChildPattern> children) {
            return this;
        }
    }
}