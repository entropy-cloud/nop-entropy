/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.common;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface OfficeErrors {
    String ARG_MSG = "msg";
    String ARG_TARGET = "target";
    String ARG_LABEL = "label";

    String ARG_NODE = "node";
    String ARG_SOURCE = "source";

    String ARG_NAME = "name";
    String ARG_CELL_POS = "cellPos";
    String ARG_ALLOWED_NAMES = "allowedNames";

    String ARG_NS = "ns";
    String ARG_NS_LIST = "nsList";
    String ARG_LIB_PATH = "libPath";

    String ARG_PATH = "path";

    String ARG_FILE_EXT = "fileExt";

    ErrorCode ERR_OOXML_INVALID_FORMAT = define("nop.err.ooxml.invalid-format", "文件格式不正确，解析失败");

    ErrorCode ERR_OOXML_LINK_SOURCE_NOT_XML = define("nop.err.ooxml.link-source-not-xml",
            "超链接的内容不是XML格式:source={source},label={label}", ARG_SOURCE, ARG_LABEL);

    ErrorCode ERR_EXPORT_INVALID_CONFIG_NAME = define("nop.err.excel.export.invalid-config-name", "未定义的配置项名称:{name}",
            ARG_CELL_POS, ARG_NAME);

    ErrorCode ERR_OOXML_LIB_NAMESPACE_CONFLICT_WITH_INTERNAL_NAMESPACE = define(
            "nop.err.ooxml.lib-namespace-conflict-with-internal-namespace",
            "标签库[{libPath}]的namespace[{namespace}]与内置的名字空间冲突，以下名字空间已被占用:{nsList}", ARG_LIB_PATH, ARG_NS, ARG_NS_LIST,
            ARG_NODE);

    ErrorCode ERR_OOXML_FILE_PATH_MUST_HAS_EXT = define("nop.err.ooxml.file-path-no-ext", "文件必须具有扩展名:{path}", ARG_PATH);

    ErrorCode ERR_OOXML_UNSUPPORTED_CONTENT_TYPE = define("nop.err.ooxml.unsupported-content-type", "不支持的文件类型:{path}",
            ARG_PATH);
}