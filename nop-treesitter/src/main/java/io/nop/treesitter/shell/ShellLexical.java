package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;

/**
 * The lexical half of "is this literal?", applied to the node kinds the positive
 * whitelist allows.
 *
 * <p>These rules exist because a node's <em>type</em> does not settle whether its
 * text is a constant. A {@code word} cannot contain an expansion (the grammar
 * would have produced a {@code concatenation} instead), but it can still be a
 * glob, a brace, a tilde or a {@code =}-assignment. A {@code string} whose named
 * children are all {@code string_content} has no expansion — yet double quotes
 * still remove backslash escapes, so {@code "a\tb"} is not the literal text
 * {@code a\tb}. Getting this wrong in either direction is a safety problem: too
 * strict and provable scripts are lost, too lax and a script that computes
 * something at runtime is reported as fully literal.</p>
 */
final class ShellLexical {

    /**
     * Characters that make a {@code word} non-literal: glob metacharacters,
     * grouping, quoting, escaping, tilde expansion, history and comment
     * characters, and the dollar sign. Deliberately excludes {@code -}, so
     * {@code -rf} stays a literal flag, and {@code =}, which is handled
     * separately because only a <em>leading</em> {@code =} matters (it would make
     * the token a variable assignment rather than an argument).
     */
    private static final String NON_LITERAL_WORD_CHARS = "{}*?[]\\~^#$`";

    private ShellLexical() {
    }

    /**
     * Whether a {@code word} or {@code number} node denotes a literal token.
     *
     * <p>Three conditions, all required: no named child (the grammar would not
     * call it a {@code word} if it had structure), no leading {@code =}, and none
     * of the non-literal characters anywhere in it.</p>
     */
    static boolean isLiteralWord(TSNode node, String text) {
        if (node.namedChildCount() != 0) {
            return false;
        }
        if (text.startsWith("=")) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (NON_LITERAL_WORD_CHARS.indexOf(text.charAt(i)) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a double-quoted {@code string} denotes a literal.
     *
     * <p>Two conditions. Every named child must be {@code string_content} — any
     * other named child is an expansion or a substitution. And the raw text must
     * contain no backslash escaping one of {@code $}, backtick, {@code "},
     * {@code \} or newline: double quotes suppress globbing but still remove the
     * escape, so such a string is not equal to its source text. The other bash
     * escapes ({@code \t}, {@code \n}, {@code \e} ...) are <em>kept</em> by double
     * quotes, so they do not make the string non-literal — but they do mean the
     * string's value is not its source text, which is why only the five
     * quote-affecting characters are listed.</p>
     */
    static boolean isLiteralDoubleQuotedString(TSNode node, String text) {
        int childCount = node.childCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = ShellAnalysis.childOrFail(node, i);
            if (child.named() && !"string_content".equals(child.type())) {
                return false;
            }
        }
        return !hasQuoteAffectingEscape(text);
    }

    /**
     * Whether a single-quoted {@code raw_string} denotes a literal. Single quotes
     * are fully literal in bash: no expansion, no escape processing. Only a
     * named child would break that, and the grammar produces none.
     */
    static boolean isLiteralRawString(TSNode node) {
        int childCount = node.childCount();
        for (int i = 0; i < childCount; i++) {
            if (ShellAnalysis.childOrFail(node, i).named()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a backslash in the text escapes one of the characters that double
     * quotes still process. A trailing backslash cannot escape anything, so it is
     * not counted.
     */
    static boolean hasQuoteAffectingEscape(String text) {
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '\\') {
                continue;
            }
            char next = text.charAt(i + 1);
            if (next == '$' || next == '`' || next == '"' || next == '\\' || next == '\n') {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a {@code concatenation} is entirely literal.
     *
     * <p>All parts must be literal, checked recursively. This is the rule that
     * decides whether a fragment like {@code pre"$X"post} reads as a constant; the
     * grammar models that fragment as one {@code concatenation} node, so without
     * this rule the command would report no dynamic construction at all and look
     * fully literal.</p>
     */
    static boolean isLiteralConcatenation(TSNode node, ShellSource source) {
        int childCount = node.childCount();
        for (int i = 0; i < childCount; i++) {
            TSNode part = ShellAnalysis.childOrFail(node, i);
            if (part.named() && !isLiteralNode(part, source)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a node denotes a literal, recursing through {@code concatenation}
     * parts.
     *
     * <p>Five structural kinds — {@code program}, {@code list},
     * {@code pipeline}, {@code command}, {@code command_name} — and
     * {@code string_content} report literal, because they carry no text of their
     * own and are decided by their children.
     *
     * <p><b>Everything else is not literal.</b> That default is the whole point: a
     * node kind this method has never heard of is a node kind whose value is
     * computed at run time as far as this layer can tell, and reporting it as
     * literal would let an unknown construct read as a constant. An earlier
     * permissive default made {@code rm -rf $TARGET /data} harvest
     * {@code $TARGET} as a plain argument, because {@code simple_expansion} fell
     * through to "structural" and came back true.</p>
     */
    static boolean isLiteralNode(TSNode node, ShellSource source) {
        String type = node.type();
        switch (type) {
            case "word":
            case "number":
                return isLiteralWord(node, text(node, source));
            case "string":
                return isLiteralDoubleQuotedString(node, text(node, source));
            case "raw_string":
                return isLiteralRawString(node);
            case "concatenation":
                return isLiteralConcatenation(node, source);
            case "string_content":
            case "program":
            case "list":
            case "pipeline":
            case "command":
            case "command_name":
                return true;
            default:
                return false;
        }
    }

    /** The node's own source text, sliced by byte offset. */
    static String text(TSNode node, ShellSource source) {
        return source.textOf(node);
    }
}
