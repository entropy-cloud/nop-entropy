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

    String ARG_PROCESSING_ITEMS = "processingItems";

    String ARG_TASK_NAME = "taskName";
    String ARG_TASK_ID = "taskId";
    String ARG_TASK_KEY = "taskKey";

    ErrorCode ERR_BATCH_PERSIST_VAR_CONVERT_TYPE_FAIL = define("nop.err.batch.persist-var-convert-type-fail",
            "状态变量[{varName}]的类型转换失败", ARG_VAR_NAME);

    ErrorCode ERR_BATCH_CANCEL_PROCESS = define("nop.err.batch.cancel-process", "批处理执行被取消");

    ErrorCode ERR_BATCH_CANCEL_LOAD = define("nop.err.batch.cancel-load", "批处理读取被取消");

    ErrorCode ERR_BATCH_WRITE_FILE_FAIL = define("nop.err.batch.write-file-fail", "输出到文件失败", ARG_RESOURCE_PATH);

    ErrorCode ERR_BATCH_TOO_MANY_PROCESSING_ITEMS = define("nop.err.batch.too-many-processing-items",
            "正在处理的记录过多，程序可能存在内存泄露");

    ErrorCode ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY = define("nop.err.batch.processing-items-not-empty",
            "任务完成时仍存在未处理完毕的记录，记录状态与实际处理进度不一致", ARG_PROCESSING_ITEMS, ARG_READ_COUNT,
            ARG_RESOURCE_PATH);

    ErrorCode ERR_BATCH_OUTPUT_FILE_EXISTS_ON_RECOVERY = define("nop.err.batch.output-file-exists-on-recovery",
            "断点续传时输出文件已存在且非空，继续执行会截断丢失已完成记录的产出。请先转移或删除输出文件，或者更换输出路径",
            ARG_RESOURCE_PATH);

    ErrorCode ERR_BATCH_RATE_LIMIT_ACQUIRE_TIMEOUT = define("nop.err.batch.rate-limit-acquire-timeout",
            "在限流超时时间内未能获取到足够的许可，限流配置可能过低", ARG_ITEM_COUNT);
}
