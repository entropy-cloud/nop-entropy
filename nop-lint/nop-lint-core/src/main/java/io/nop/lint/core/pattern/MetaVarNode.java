package io.nop.lint.core.pattern;

/**
 * A meta-variable occurrence inside a compiled pattern.
 *
 * @param shape the syntactic shape of the occurrence
 * @param name  the capture name, or null for the bare forms ({@code $},
 *              {@code $$}, {@code $$$}, {@code $_}); names starting with
 *              {@code _} are non-capturing by convention
 * @param text  the original source text of the occurrence (diagnostics)
 */
record MetaVarNode(Shape shape, String name, String text) implements PatternNode {

    enum Shape {
        /**
         * {@code $VAR} — matches a single named node, captures into env.
         */
        SINGLE,
        /**
         * {@code $$VAR} — matches a single named or anonymous node.
         */
        ANONYMOUS,
        /**
         * {@code $_VAR} — matches but never captures.
         */
        DROP,
        /**
         * {@code $$$VAR} — matches a sequence of zero or more nodes.
         */
        MULTI
    }

    boolean captures() {
        return name != null && !name.startsWith("_");
    }
}
