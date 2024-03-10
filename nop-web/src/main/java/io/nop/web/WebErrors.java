/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface WebErrors {
    String ARG_PATH = "path";
    String ARG_ALLOWED_FILE_TYPES = "allowedFileTypes";

    String ARG_RESOURCE = "resource";
    String ARG_LOC = "loc";

    ErrorCode ERR_WEB_INVALID_PAGE_FILE_TYPE = define("nop.err.web.invalid-page-file-types",
            "请求路径[{path}]不是页面文件，文件后缀名必须是{allowedFileTypes}", ARG_PATH, ARG_ALLOWED_FILE_TYPES);

    ErrorCode ERR_WEB_PAGE_RESOURCE_NOT_FILE = define("nop.err.web.page-resource-not-file", "页面资源[{path}]不是文件，不允许修改");

    ErrorCode ERR_WEB_MISSING_RESOURCE =
            define("nop.err.web.missing-resource",
                    "资源文件[{path}]不存在");

    ErrorCode ERR_WEB_JS_COMMENT_NOT_END_PROPERLY =
            define("nop.err.web.js-comment-not-end-properly",
                    "JS注释没有正确结束");

    ErrorCode ERR_WEB_PAGE_NOT_ALLOW_EDIT =
            define("nop.err.web.page-not-allow-edit",
                    "没有开启编辑模式，不允许编辑页面文件");

    ErrorCode ERR_WEB_UNSUPPORTED_FILE_TYPE = define("nop.err.web.unsupported-file-type",
            "请求路径[{path}]不是允许的文件类型，文件后缀名必须是{allowedFileTypes}", ARG_PATH, ARG_ALLOWED_FILE_TYPES);

    ErrorCode ERR_WEB_DYNAMIC_FILE_MISSING_END_MOCK = define("nop.err.web.dynamic-file-missing-mock-block",
            "没有找到匹配的@enc-mock部分");
}
