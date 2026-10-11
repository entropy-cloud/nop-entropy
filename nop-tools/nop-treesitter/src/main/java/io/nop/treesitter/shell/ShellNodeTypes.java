package io.nop.treesitter.shell;

import java.util.Set;

/**
 * The closed node vocabularies of the shell analysis layer, spelled out against
 * the vendored bash grammar's {@code node-types.json} inventory
 * ({@code ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md}).
 *
 * <p>Both sets are <b>positive</b> lists on purpose. An exclusion list fails
 * open: a node kind added by a future grammar version is admitted silently. A
 * positive list fails closed: a new kind is rejected until someone decides
 * whether it can be proven literal.</p>
 */
final class ShellNodeTypes {

    /**
     * Named node kinds that may appear in a script the analysis layer is willing
     * to prove. Anything outside this set — control flow, substitutions,
     * expansions, redirects, heredocs, declarations, comments — makes the script
     * unprovable.
     */
    static final Set<String> PROVABLE_KINDS = Set.of(
            "program",
            "list",
            "pipeline",
            "command",
            "command_name",
            "word",
            "number",
            "string",
            "string_content",
            "raw_string",
            "concatenation");

    /**
     * The only anonymous tokens a provable script may contain: the connectors
     * between commands and the two quote characters. Notably absent: the
     * redirection operators, the background {@code &}, the subshell braces and
     * parens, {@code >|}, and {@code (} / {@code )} used as literal
     * subexpressions.
     */
    static final Set<String> PROVABLE_PUNCTUATION = Set.of(
            "&&",
            "||",
            ";",
            "|",
            "\"",
            "'");

    /**
     * Named node kinds that stand for a value the layer refuses to compute at
     * analysis time. Reported by the dynamic-construction capability with a byte
     * range; never evaluated.
     */
    static final Set<String> DYNAMIC_KINDS = Set.of(
            "command_substitution",
            "process_substitution",
            "simple_expansion",
            "expansion",
            "arithmetic_expansion",
            "concatenation",
            "variable_assignment",
            "variable_assignments",
            "file_redirect",
            "redirected_statement",
            "heredoc_redirect",
            "heredoc_start",
            "heredoc_body",
            "heredoc_end",
            "herestring_redirect",
            "postfix_expression",
            "ternary_expression",
            "array");

    /**
     * The background {@code &} has no node type at all in the bash grammar, so a
     * node walk can never find it. It is detected by scanning the source between
     * sibling nodes for an {@code &} that is not part of {@code &&}, {@code >&},
     * {@code &>}, {@code &-} or a numeric descriptor prefix.
     */
    static final String BACKGROUND_AMPERSAND = "&";

    private ShellNodeTypes() {
    }
}
