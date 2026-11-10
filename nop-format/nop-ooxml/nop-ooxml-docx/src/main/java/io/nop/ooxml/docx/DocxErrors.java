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
    String ARG_STYLE_ID = "styleId";

    ErrorCode ERR_DOCX_PARSE_PACKAGE_INVALID = define("nop.err.ooxml.docx.parse-package-invalid",
            "docx包损坏或不是有效的zip文件:{partPath}", ARG_PART_PATH);

    ErrorCode ERR_DOCX_PARSE_PART_MISSING = define("nop.err.ooxml.docx.parse-part-missing",
            "docx包缺少必需的部件:{partPath}", ARG_PART_PATH);

    ErrorCode ERR_DOCX_PARSE_RESERVED_STYLE_ID = define("nop.err.ooxml.docx.parse-reserved-style-id",
            "Style id [{styleId}] uses the reserved '__' prefix for inline cell styles", ARG_STYLE_ID);
    ErrorCode ERR_DOCX_PARSE_XML_INVALID = define("nop.err.ooxml.docx.parse-xml-invalid",
            "docx部件XML结构损坏，无法解析:{partPath}", ARG_PART_PATH);

    ErrorCode ERR_DOCX_XPL_BEGIN_END_NOT_MATCH = define("nop.err.ooxml.docx.xpl-begin-end-not-match",
            "通过超链接标注的xpl标签[{tagName}]的起始部分和结束部分[{endTagName}]不匹配:{label}", ARG_TAG_NAME, ARG_END_TAG_NAME, ARG_LABEL);

    ErrorCode ERR_DOCX_XPL_END_NO_BEGIN = define("nop.err.ooxml.docx.xpl-end-no-begin",
            "通过超链接标注的xpl标签[{tagName}]没有起始部分:{label}", ARG_TAG_NAME, ARG_LABEL);

    ErrorCode ERR_DOCX_XPL_BEGIN_NO_END = define("nop.err.ooxml.docx.xpl-begin-no-end",
            "通过超链接标注的xpl标签[{tagName}]没有结束部分:{label}", ARG_TAG_NAME, ARG_LABEL);

    // ---- generation (model -> docx): model shapes that cannot be generated fail fast

    String ARG_BLOCK_TYPE = "blockType";
    String ARG_TARGET_ID = "targetId";
    String ARG_REASON = "reason";

    ErrorCode ERR_DOCX_GEN_BLOCK_TYPE_UNKNOWN = define("nop.err.ooxml.docx.gen-block-type-unknown",
            "Unknown block type cannot be generated to docx: {blockType}", ARG_BLOCK_TYPE);

    ErrorCode ERR_DOCX_GEN_RUN_TYPE_UNKNOWN = define("nop.err.ooxml.docx.gen-run-type-unknown",
            "Unknown inline run type cannot be generated to docx: {blockType}", ARG_BLOCK_TYPE);

    ErrorCode ERR_DOCX_GEN_IMAGE_INVALID = define("nop.err.ooxml.docx.gen-image-invalid",
            "Image or relationship cannot be generated to docx: {reason}, target={targetId}",
            ARG_REASON, ARG_TARGET_ID);

    ErrorCode ERR_DOCX_GEN_TITLE_LEVEL_INVALID = define("nop.err.ooxml.docx.gen-title-level-invalid",
            "Title block has no style id and its titleLevel is not within 1..9: {targetId}", ARG_TARGET_ID);

    ErrorCode ERR_DOCX_GEN_LIST_NUM_ID_MISSING = define("nop.err.ooxml.docx.gen-list-num-id-missing",
            "List block has no numId and cannot be generated to docx: {targetId}", ARG_TARGET_ID);
}
