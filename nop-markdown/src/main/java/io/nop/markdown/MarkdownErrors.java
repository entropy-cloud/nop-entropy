package io.nop.markdown;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface MarkdownErrors {
    String ARG_TITLE = "title";

    ErrorCode ERR_MARKDOWN_MISSING_BLOCK =
            define("nop.err.markdown.missing-block", "Markdown文本中缺少要求的区块: {title}", ARG_TITLE);

    ErrorCode ERR_MARKDOWN_NO_BLOCK =
            define("nop.err.markdown.no-block", "Markdown文本为空");

    ErrorCode ERR_MARKDOWN_BLOCK_NOT_DEFINED_IN_TPL =
            define("nop.err.markdown.block-not-defined-in-tpl", "Markdown文本中定义的区块在模板中未定义: {title}", ARG_TITLE);
}
