package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;

/**
 * Classifies identifier-shaped pattern tokens as meta-variables.
 *
 * <p>Rules (deliberate deviations from ast-grep are marked):</p>
 * <ol>
 * <li>Longest-prefix order {@code $$$} → {@code $$} → {@code $}; the rest of
 * the text is the candidate name.</li>
 * <li>A candidate name is valid only when it starts with {@code [A-Z_]} and
 * continues with {@code [A-Z_0-9]}; an invalid name (empty except for the
 * bare forms, containing {@code $}, or starting with a digit — e.g.
 * {@code $A$B}, {@code $1}, {@code $$$$VAR}) means the text is <em>not</em> a
 * meta-variable at all and parses as a literal identifier.</li>
 * <li>Deviation from ast-grep: the bare forms {@code $}, {@code $$}, {@code $$$},
 * {@code $_} are accepted as non-capturing meta-variables (ast-grep treats them
 * as literal identifiers) — rule bodies like a class-body {@code $$$} depend on
 * the bare multi form.</li>
 * <li>{@code $_VAR} is a drop variable (matches, never captures); by
 * convention any name starting with {@code _} is non-capturing.</li>
 * <li>{@code $@} / {@code $!} (typed / literal meta-variables) are rejected —
 * they need the L2 type layer.</li>
 * </ol>
 */
final class MetaVarSyntax {

    private MetaVarSyntax() {
    }

    /**
     * The classification result for one token text.
     *
     * @param shape the meta-var shape
     * @param name  capture name, null for bare forms
     */
    record Spec(MetaVarNode.Shape shape, String name) {
    }

    /**
     * Classifies {@code text}; null when the text is not a meta-variable (the
     * caller keeps the node as a literal). Throws {@link NopLintException}
     * for the reserved-but-unsupported {@code $@}/{@code $!} forms.
     */
    static Spec parse(String text) {
        if (text.isEmpty() || text.charAt(0) != '$') {
            return null;
        }
        // Longest prefix first: $$$ before $$ before $.
        int markerLen;
        MetaVarNode.Shape bareShape;
        if (text.startsWith("$$$")) {
            markerLen = 3;
            bareShape = MetaVarNode.Shape.MULTI;
        } else if (text.startsWith("$$")) {
            markerLen = 2;
            bareShape = MetaVarNode.Shape.ANONYMOUS;
        } else {
            markerLen = 1;
            bareShape = MetaVarNode.Shape.SINGLE;
        }

        if (text.length() == markerLen) {
            // Bare form: $ / $$ / $$$ — accepted non-capturing (deviation
            // from ast-grep; rule bodies like a class-body $$$ depend on it).
            return new Spec(bareShape, null);
        }
        if (text.equals("$_")) {
            return new Spec(MetaVarNode.Shape.DROP, null);
        }

        String name = text.substring(markerLen);
        if (!isValidName(name)) {
            return null;
        }
        MetaVarNode.Shape shape = switch (bareShape) {
            case SINGLE -> name.startsWith("_") ? MetaVarNode.Shape.DROP : MetaVarNode.Shape.SINGLE;
            case ANONYMOUS -> MetaVarNode.Shape.ANONYMOUS;
            default -> MetaVarNode.Shape.MULTI;
        };
        return new Spec(shape, name);
    }

    private static boolean isValidName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        char first = name.charAt(0);
        if (!isNameStart(first)) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isNamePart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNameStart(char c) {
        return (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isNamePart(char c) {
        return isNameStart(c) || (c >= '0' && c <= '9');
    }

    /**
     * Throws for the reserved {@code $@}/{@code $!} prefixes (checked before
     * classification, since a typed meta-var must not fall through to a
     * literal silently).
     */
    static void rejectReserved(String text) {
        if (text.length() >= 2 && text.charAt(0) == '$'
                && (text.charAt(1) == '@' || text.charAt(1) == '!')) {
            throw new NopLintException(
                    "typed/literal meta-variables (" + text + ") are not supported yet");
        }
    }
}
