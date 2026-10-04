package io.nop.treesitter.shell;

import java.util.List;

/**
 * One place where a shell script computes a value at run time, located by byte
 * range and never evaluated.
 *
 * <p>Evaluation is not merely out of scope, it would be the thing this layer
 * exists to prevent: working out what {@code $(...)} or {@code $((...))} produces
 * means running it.</p>
 */
public final class DynamicConstruct {

    /** The closed enumeration. */
    public enum Kind {
        /** {@code $( )} and backticks — the grammar gives both the same node type. */
        COMMAND_SUBSTITUTION("command_substitution"),
        /** {@code <( )}. */
        PROCESS_SUBSTITUTION("process_substitution"),
        /** {@code $VAR}. */
        SIMPLE_EXPANSION("simple_expansion"),
        /** <code>${VAR}</code>. */
        EXPANSION("expansion"),
        /** <code>$(( ))</code>. */
        ARITHMETIC_EXPANSION("arithmetic_expansion"),
        /**
         * A literal fragment glued to an expansion. Listed in its own right
         * because omitting it makes {@code pre"$X"post} report <b>no</b> dynamic
         * construction, which reads as "fully literal".
         */
        CONCATENATION("concatenation"),
        /** {@code FOO=bar} on its own or as a command prefix. */
        VARIABLE_ASSIGNMENT("variable_assignment"),
        /** Any redirection target. */
        FILE_REDIRECT("file_redirect"),
        /** A statement carrying a redirection. */
        REDIRECTED_STATEMENT("redirected_statement"),
        HEREDOC_REDIRECT("heredoc_redirect"),
        HEREDOC_START("heredoc_start"),
        HEREDOC_BODY("heredoc_body"),
        HEREDOC_END("heredoc_end"),
        HEREDOC_CONTENT("heredoc_content"),
        HERESTRING_REDIRECT("herestring_redirect"),
        /**
         * The background operator. It has no <b>named</b> node type in the bash
         * grammar — it is an anonymous token — so a walk that only considers named
         * nodes misses it entirely and the script reads as if nothing were left
         * running in the background.
         */
        BACKGROUND_OPERATOR("&");

        private final String nodeType;

        Kind(String nodeType) {
            this.nodeType = nodeType;
        }

        /** The grammar node type (or anonymous token) this kind is located by. */
        public String nodeType() {
            return nodeType;
        }
    }

    private final Kind kind;
    private final int startByte;
    private final int endByte;
    private final String text;

    DynamicConstruct(Kind kind, int startByte, int endByte, String text) {
        this.kind = kind;
        this.startByte = startByte;
        this.endByte = endByte;
        this.text = text == null ? "" : text;
    }

    public Kind kind() {
        return kind;
    }

    public int startByte() {
        return startByte;
    }

    public int endByte() {
        return endByte;
    }

    /** The source text of the construct, verbatim. */
    public String text() {
        return text;
    }

    /** One entry, so a list of these can cross a GraphQL boundary unchanged. */
    public List<Integer> byteRange() {
        return List.of(startByte, endByte);
    }

    @Override
    public String toString() {
        return kind + "[" + startByte + "," + endByte + ")";
    }
}
