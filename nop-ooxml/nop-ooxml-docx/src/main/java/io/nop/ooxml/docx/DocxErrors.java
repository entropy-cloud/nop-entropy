/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.docx;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface DocxErrors {
    String ARG_TAG_NAME = "tagName";
    String ARG_END_TAG_NAME = "endTagName";

    String ARG_LABEL = "label";
    String ARG_LINK_TARGET = "linkTarget";
    String ARG_LINK_NODE = "linkNode";

    ErrorCode ERR_DOCX_XPL_BEGIN_END_NOT_MATCH = define("nop.err.ooxml.docx.xpl-begin-end-not-match",
            "通过超链接标注的xpl标签[{tagName}]的起始部分和结束部分[{endTagName}]不匹配:{label}", ARG_TAG_NAME, ARG_END_TAG_NAME, ARG_LABEL);

    ErrorCode ERR_DOCX_XPL_END_NO_BEGIN = define("nop.err.ooxml.docx.xpl-end-no-begin",
            "通过超链接标注的xpl标签[{tagName}]没有起始部分:{label}", ARG_TAG_NAME, ARG_LABEL);

    ErrorCode ERR_DOCX_XPL_BEGIN_NO_END = define("nop.err.ooxml.docx.xpl-begin-no-end",
            "通过超链接标注的xpl标签[{tagName}]没有结束部分:{label}", ARG_TAG_NAME, ARG_LABEL);
}
