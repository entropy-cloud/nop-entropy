package io.nop.treesitter.biz;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes raised by the treesitter GraphQL surface.
 */
public interface TreeSitterErrors {
    String ARG_LANGUAGE = "language";

    ErrorCode ERR_TREE_SITTER_UNKNOWN_LANGUAGE = define("nop.err.treesitter.unknown-language",
            "unknown tree-sitter grammar: {language}", ARG_LANGUAGE);

    String ARG_EXPECTED_LANGUAGE = "expectedLanguage";

    String ARG_MISSING_NODES = "missingNodes";

    /**
     * The grammar resolved, but it is not the one the shell analysis layer is
     * defined against. The layer's whole contract is written in terms of the bash
     * grammar's node and field inventory, so running it over any other grammar
     * would produce confident nonsense.
     */
    ErrorCode ERR_TREE_SITTER_UNSUPPORTED_SHELL_LANGUAGE = define(
            "nop.err.treesitter.unsupported-shell-language",
            "grammar {language} is not usable for shell analysis; expected {expectedLanguage}"
                    + " (missing nodes: {missingNodes})", ARG_LANGUAGE);
}
