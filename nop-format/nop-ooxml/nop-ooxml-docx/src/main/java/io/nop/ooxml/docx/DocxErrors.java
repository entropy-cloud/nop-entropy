/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
    String ARG_PART_PATH = "partPath";

    ErrorCode ERR_DOCX_PARSE_PACKAGE_INVALID = define("nop.err.ooxml.docx.parse-package-invalid",
            "docx包损坏或不是有效的zip文件:{partPath}", ARG_PART_PATH);

    ErrorCode ERR_DOCX_PARSE_PART_MISSING = define("nop.err.ooxml.docx.parse-part-missing",
            "docx包缺少必需的部件:{partPath}", ARG_PART_PATH);

    ErrorCode ERR_DOCX_PARSE_XML_INVALID = define("nop.err.ooxml.docx.parse-xml-invalid",
            "docx部件XML结构损坏，无法解析:{partPath}", ARG_PART_PATH);

    ErrorCode ERR_DOCX_XPL_BEGIN_END_NOT_MATCH = define("nop.err.ooxml.docx.xpl-begin-end-not-match",
            "通过超链接标注的xpl标签[{tagName}]的起始部分和结束部分[{endTagName}]不匹配:{label}", ARG_TAG_NAME, ARG_END_TAG_NAME, ARG_LABEL);

    ErrorCode ERR_DOCX_XPL_END_NO_BEGIN = define("nop.err.ooxml.docx.xpl-end-no-begin",
            "通过超链接标注的xpl标签[{tagName}]没有起始部分:{label}", ARG_TAG_NAME, ARG_LABEL);

    ErrorCode ERR_DOCX_XPL_BEGIN_NO_END = define("nop.err.ooxml.docx.xpl-begin-no-end",
            "通过超链接标注的xpl标签[{tagName}]没有结束部分:{label}", ARG_TAG_NAME, ARG_LABEL);
}
