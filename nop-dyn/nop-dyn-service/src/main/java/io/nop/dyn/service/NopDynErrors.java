/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopDynErrors {
    String ARG_PATH = "path";
    String ARG_MODULE_ID = "moduleId";

    String ARG_PAGE_NAME = "pageName";
    ErrorCode ERR_DYN_PAGE_NOT_EXISTS =
            define("nop.err.dyn.page-not-exists", "页面不存在:{path}", ARG_PATH);

    ErrorCode ERR_DYN_INVALID_PAGE_NAME =
            define("nop.err.dyn.invalid-page-name", "页面名称必须是合法的相对路径文件名，例如a/b", ARG_PAGE_NAME);

    ErrorCode ERR_DYN_INVALID_PAGE_PATH =
            define("nop.err.dyn.invalid-page-path",
                    "文件路径必须是/为开始的虚拟文件路径名，格式必须是/[moduleId]/pages/[pageName].page.json");

    ErrorCode ERR_DYN_UNKNOWN_MODULE =
            define("nop.err.dyn.unknown-module", "未知的模块:{moduleId}", ARG_MODULE_ID);
}
