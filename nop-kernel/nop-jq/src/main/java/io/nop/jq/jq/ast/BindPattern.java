package io.nop.jq.jq.ast;

import java.util.List;

/**
 * Destructuring pattern used by `expr as PATTERN | body`, reduce and foreach.
 *
 * <p>Forms: $var, [p1, p2?, ...], {key: p, $var, (expr): p}, and alternatives
 * combined with ?//. A trailing ? makes a pattern optional: destructuring errors
 * are swallowed and no bindings are produced.
 */
public abstract sealed class BindPattern {

    private BindPattern() {
    }

    /** Optional flag: errors during destructuring produce no bindings. */
    public boolean optional() {
        return false;
    }

    @Override
    public abstract String toString();

    public static final class Var extends BindPattern {
        private final String name;

        public Var(String name) {
            this.name = name;
        }

        /** Variable name including the leading $, e.g. "$foo". */
        public String name() {
            return name;
        }

        @Override public String toString() { return name; }
    }

    public static final class Array extends BindPattern {
        private final List<Element> elements;

        public Array(List<Element> elements) {
            this.elements = elements;
        }

        public List<Element> elements() {
            return elements;
        }

        public record Element(BindPattern pattern, boolean optional) {
            @Override public String toString() {
                return optional ? pattern + "?" : pattern.toString();
            }
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(elements.get(i));
            }
            return sb.append(']').toString();
        }
    }

    public static final class Object extends BindPattern {
        private final List<Entry> entries;

        public Object(List<Entry> entries) {
            this.entries = entries;
        }

        public List<Entry> entries() {
            return entries;
        }

        public record Entry(String key, JqAstNode keyExpr, String variable,
                            BindPattern value, boolean optional) {
            @Override public String toString() {
                String k = key != null ? "\"" + key + "\"" : (keyExpr != null ? "(" + keyExpr + ")" : variable);
                String v = variable != null && key == null && keyExpr == null
                        ? "" : ": " + (value == null ? "?" : value.toString());
                return k + v + (optional ? "?" : "");
            }
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(entries.get(i));
            }
            return sb.append('}').toString();
        }
    }

    public static final class Alt extends BindPattern {
        private final List<Alternative> alternatives;

        public Alt(List<Alternative> alternatives) {
            this.alternatives = alternatives;
        }

        public List<Alternative> alternatives() {
            return alternatives;
        }

        public record Alternative(BindPattern pattern, boolean optional) {
            @Override public String toString() {
                return optional ? pattern + "?" : pattern.toString();
            }
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < alternatives.size(); i++) {
                if (i > 0) sb.append(" ?// ");
                sb.append(alternatives.get(i));
            }
            return sb.toString();
        }
    }
}
