package io.nop.markdown;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface MarkdownErrors {
    String ARG_TITLE = "title";
    String ARG_CONTENT = "content";

    ErrorCode ERR_MARKDOWN_MISSING_SECTION =
            define("nop.err.markdown.missing-section", "Markdown文本中缺少要求的段落: {title}", ARG_TITLE);

    ErrorCode ERR_MARKDOWN_NO_SECTION =
            define("nop.err.markdown.no-section", "Markdown文本为空");

    ErrorCode ERR_MARKDOWN_SECTION_NOT_DEFINED_IN_TPL =
            define("nop.err.markdown.section-not-defined-in-tpl", "Markdown文本中定义的段落在模板中未定义: {title}", ARG_TITLE);

    ErrorCode ERR_MARKDOWN_INVALID_NAME_VALUE_LINE =
            define("nop.err.markdown.invalid-name-value-line", "不是name:value这种格式", ARG_CONTENT);

    ErrorCode ERR_MARKDOWN_NOT_ALL_CHILD_ORDERED =
            define("nop.err.markdown.not-all-child-ordered", "不是所有子节点都是有序节点");

    ErrorCode ERR_MARKDOWN_NOT_ALL_CHILD_SECTION_ORDERED =
            define("nop.err.markdown.not-all-child-section-ordered", "不是所有子项目都是有序节点");
}
