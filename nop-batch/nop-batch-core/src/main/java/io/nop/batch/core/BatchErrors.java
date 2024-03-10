/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface BatchErrors {
    String ARG_VAR_NAME = "varName";
    String ARG_RESOURCE_PATH = "resourcePath";

    String ARG_ITEM_COUNT = "itemCount";

    String ARG_READ_COUNT = "readCount";

    ErrorCode ERR_BATCH_PERSIST_VAR_CONVERT_TYPE_FAIL = define("nop.err.batch.persist-var-convert-type-fail",
            "状态变量[{varName}]的类型转换失败", ARG_VAR_NAME);

    ErrorCode ERR_BATCH_CANCEL_PROCESS = define("nop.err.batch.cancel-process", "批处理执行被取消");

    ErrorCode ERR_BATCH_CANCEL_LOAD = define("nop.err.batch.cancel-load", "批处理读取被取消");

    ErrorCode ERR_BATCH_WRITE_FILE_FAIL = define("nop.err.batch.write-file-fail", "输出到文件失败", ARG_RESOURCE_PATH);

    ErrorCode ERR_BATCH_TOO_MANY_PROCESSING_ITEMS = define("nop.err.batch.too-many-processing-items",
            "正在处理的记录过多，程序可能存在内存泄露");
}
