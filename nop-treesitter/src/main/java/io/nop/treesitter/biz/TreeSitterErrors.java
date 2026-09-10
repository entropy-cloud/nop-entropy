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
}
