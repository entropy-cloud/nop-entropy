package io.nop.batch.dsl;

import io.nop.api.core.exceptions.ErrorCode;

public interface BatchDslErrors {
    String ARG_BATCH_TASK_NAME = "batchTaskName";

    String ARG_PROCESSOR_NAME = "processorName";

    String ARG_CONSUMER_NAME = "consumerName";

    String ARG_BEAN_NAME = "beanName";

    String ARG_VALUE = "value";

    String ARG_INPUT_NAME = "inputName";

    ErrorCode ERR_BATCH_TASK_INVALID_LOADER =
            ErrorCode.define("nop.err.batch.task-invalid-loader", "批处理的loader类型不合法", ARG_BATCH_TASK_NAME);

    ErrorCode ERR_BATCH_TASK_INVALID_PROCESSOR =
            ErrorCode.define("nop.err.batch.task-invalid-processor", "批处理的processor类型不合法", ARG_BATCH_TASK_NAME);

    ErrorCode ERR_BATCH_TASK_INVALID_CONSUMER =
            ErrorCode.define("nop.err.batch.task-invalid-consumer", "批处理的consumer类型不合法", ARG_BATCH_TASK_NAME);

    ErrorCode ERR_BATCH_TASK_NO_LOADER =
            ErrorCode.define("nop.err.batch.task-no-loader", "批处理任务没有定义loader", ARG_BATCH_TASK_NAME);

    ErrorCode ERR_BATCH_TASK_NAME_EMPTY =
            ErrorCode.define("nop.err.batch.task-name-empty", "批处理任务名称为空", ARG_BATCH_TASK_NAME);

    ErrorCode ERR_BATCH_TASK_PROCESSOR_IS_NULL =
            ErrorCode.define("nop.err.batch.processor-is-null", "批处理任务的处理器配置不允许为null", ARG_PROCESSOR_NAME);

    ErrorCode ERR_BATCH_TASK_INVALID_HISTORY_STORE_BEAN =
            ErrorCode.define("nop.err.batch.invalid-history-store-bean", "批处理任务定义的historyStore的bean配置无效", ARG_BEAN_NAME);

    ErrorCode ERR_BATCH_INPUT_MANDATORY_NOT_PROVIDED =
            ErrorCode.define("nop.err.batch.input-mandatory-not-provided", "批处理任务的input参数{inputName}是必需的，但未提供", ARG_BATCH_TASK_NAME, ARG_INPUT_NAME);
}
