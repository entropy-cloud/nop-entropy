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

    String ARG_SKIP_COUNT = "skipCount";

    String ARG_RETRY_COUNT = "retryCount";

    String ARG_BATCH_SIZE = "batchSize";

    String ARG_TASK_NAME = "taskName";
    String ARG_TASK_ID = "taskId";
    String ARG_TASK_KEY = "taskKey";

    String ARG_PARAM_NAME = "paramName";
    String ARG_PARAM_VALUE = "paramValue";
    String ARG_MAX_CONCURRENCY_CAP = "maxConcurrencyCap";

    String ARG_LOAD_BATCH_SIZE = "loadBatchSize";
    String ARG_FETCH_THREAD_COUNT = "fetchThreadCount";

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

    ErrorCode ERR_BATCH_CONCURRENCY_EXCEEDS_CAP = define("nop.err.batch.concurrency-exceeds-cap",
            "并发参数[{paramName}]的值[{paramValue}]超过装配期上界[{maxConcurrencyCap}]，任务不允许装配。"
                    + "如确需更高并发，请调大配置项nop.batch.max-concurrency-cap（同时需要评估平台共享cached池上界"
                    + "nop.commons.concurrent.cached-pool.maxPoolSize）",
            ARG_PARAM_NAME, ARG_PARAM_VALUE, ARG_MAX_CONCURRENCY_CAP);

    ErrorCode ERR_BATCH_DISPATCH_LOAD_BATCH_SIZE_INVALID = define("nop.err.batch.dispatch-load-batch-size-invalid",
            "批处理dispatch读取批大小[{loadBatchSize}]非法：loadBatchSize必须大于0，任务不允许装配。"
                    + "loadBatchSize<=0会使dispatch队列容量（capacity = loadBatchSize × loadBatchMultiplyFactor，"
                    + "AsyncFetch缺省×30）退化为0，orm-reader全表单页加载将对Semaphore(0)永久阻塞，"
                    + "其他reader会静默产出空任务导致数据丢失",
            ARG_LOAD_BATCH_SIZE, ARG_FETCH_THREAD_COUNT);

    /** 1.7-C7-005（plan 2546 W-01）：负 maxSkipCount 在 shouldSkip 中与 0 同义（不设上限），
     *  与多数惯例（-1=禁用）相反——静默配置陷阱，装配期拒绝。 */
    ErrorCode ERR_BATCH_SKIP_COUNT_INVALID = define("nop.err.batch.skip-count-invalid",
            "批处理跳过策略的maxSkipCount={skipCount}非法：负值语义为不设上限（与-1=禁用的惯例相反）。"
                    + "如需不设上限请显式配置0", ARG_SKIP_COUNT);

    /** 1.7-C7-008（plan 2546 W-04）：loader 收到非正 batchSize（chunk 钳位之前拦截）。 */
    ErrorCode ERR_BATCH_BATCH_SIZE_INVALID = define("nop.err.batch.batch-size-invalid",
            "批处理loader收到的batchSize={batchSize}非法：必须大于0", ARG_BATCH_SIZE);

    /** 1.7-C8-009（plan 2546 W-10）：dispatch 流控常数非法（factor/maxLock 必须 >=1）。 */
    ErrorCode ERR_BATCH_DISPATCH_FACTOR_INVALID = define("nop.err.batch.dispatch-factor-invalid",
            "批处理dispatch流控参数[{paramName}]的值[{paramValue}]非法：必须>=1。"
                    + "loadBatchMultiplyFactor<=0会使队列容量退化为0导致挂死", ARG_PARAM_NAME, ARG_PARAM_VALUE);

    ErrorCode ERR_BATCH_SAVE_STATE_WITH_DISPATCHER_UNSUPPORTED = define(
            "nop.err.batch.save-state-with-dispatcher-unsupported",
            "批处理任务同时配置saveState（断点续传）与dispatcher.fetchThreadCount>0是不支持的组合："
                    + "fetchThreadCount={fetchThreadCount}。分区派发下load与consume在时间与线程上解耦，"
                    + "reader的chunk级压实回调（标记processingItems完成并推进completedIndex）无法附着到"
                    + "实际消费该批数据的chunk上下文，会导致completedIndex永不推进、任务必然以"
                    + "nop.err.batch.processing-items-not-empty失败。请改用concurrency（不配置dispatcher）"
                    + "或关闭saveState。",
            ARG_FETCH_THREAD_COUNT);
}
