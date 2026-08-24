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
    String ARG_MODULE_NAME = "moduleName";
    String ARG_BIZ_OBJ_NAME = "bizObjName";
    String ARG_MAX_COUNT = "maxCount";
    String ARG_CURRENT_COUNT = "currentCount";

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

    ErrorCode ERR_DYN_BIZ_MODEL_NOT_EXISTS =
            define("nop.err.dyn.biz-model-not-exists", "未知的动态业务对象:{bizObjName}", ARG_BIZ_OBJ_NAME);

    ErrorCode ERR_DYN_MODULE_NAME_EXISTS =
            define("nop.err.dyn.module-name-exists", "同名动态模块已存在:{moduleName}", ARG_MODULE_NAME);

    ErrorCode ERR_DYN_MAX_BIZ_OBJECTS_EXCEED =
            define("nop.err.dyn.max-biz-objects-exceed", "动态对象总数{currentCount}超过最大允许数量{maxCount}",
                    ARG_CURRENT_COUNT, ARG_MAX_COUNT);
}
