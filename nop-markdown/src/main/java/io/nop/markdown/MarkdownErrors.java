package io.nop.markdown;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface MarkdownErrors {
    String ARG_TITLE = "title";

    ErrorCode ERR_MARKDOWN_MISSING_BLOCK =
            define("nop.err.markdown.missing-block", "Markdown文本中缺少要求的区块: {title}", ARG_TITLE);
}
