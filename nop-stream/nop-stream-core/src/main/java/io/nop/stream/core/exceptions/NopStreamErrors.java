/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.exceptions;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopStreamErrors {
    String ARG_ARG_NAME = "argName";
    String ARG_DETAIL = "detail";
    String ARG_OPERATOR_NAME = "operatorName";
    String ARG_STATE_NAME = "stateName";
    String ARG_CLASS_NAME = "className";
    String ARG_CONFIG_KEY = "configKey";
    String ARG_OPERATION = "operation";
    String ARG_VERTEX_ID = "vertexId";
    String ARG_TASK_INDEX = "taskIndex";
    String ARG_TASK_LOCATION = "taskLocation";
    String ARG_JOB_ID = "jobId";
    String ARG_CHECKPOINT_ID = "checkpointId";
    String ARG_EPOCH_ID = "epochId";
    String ARG_VALUE_TYPE = "valueType";
    String ARG_STATE_VERSION = "stateVersion";
    String ARG_STATE_TYPE = "stateType";
    String ARG_DESCRIPTOR_NAME = "descriptorName";

    ErrorCode ERR_STREAM_NULL_ARG =
            define("nop.err.stream.null-arg", "Argument {argName} must not be null", ARG_ARG_NAME);

    ErrorCode ERR_STREAM_INVALID_STATE =
            define("nop.err.stream.invalid-state", "Invalid stream state: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CONFIG_ERROR =
            define("nop.err.stream.config-error", "Stream configuration error: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_UNSUPPORTED =
            define("nop.err.stream.unsupported", "Unsupported operation: {operation}", ARG_OPERATION);

    ErrorCode ERR_STREAM_SERIALIZATION =
            define("nop.err.stream.serialization", "Serialization failed: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_OPERATOR_ERROR =
            define("nop.err.stream.operator-error", "Operator {operatorName} execution error: {detail}",
                    ARG_OPERATOR_NAME, ARG_DETAIL);

    ErrorCode ERR_STREAM_CHECKPOINT_ERROR =
            define("nop.err.stream.checkpoint-error", "Checkpoint error: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_STATE_ERROR =
            define("nop.err.stream.state-error", "State management error: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_INVALID_ARG =
            define("nop.err.stream.invalid-arg", "Invalid value for argument {argName}: {detail}",
                    ARG_ARG_NAME, ARG_DETAIL);

    ErrorCode ERR_STREAM_INIT_ERROR =
            define("nop.err.stream.init-error", "Initialization failed: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_NOT_INITIALIZED =
            define("nop.err.stream.checkpoint-executor-not-initialized", "Checkpoint executor not initialized");

    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_ALREADY_STARTED =
            define("nop.err.stream.checkpoint-executor-already-started", "Checkpoint executor already started");

    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED =
            define("nop.err.stream.checkpoint-executor-failed", "Checkpoint executor failed: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_SAVEPOINT_FAILED =
            define("nop.err.stream.checkpoint-executor-savepoint-failed", "Failed to trigger terminal savepoint", ARG_DETAIL);

    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED =
            define("nop.err.stream.checkpoint-executor-restore-failed", "Checkpoint restore failed: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_SNAPSHOT_FAILED =
            define("nop.err.stream.checkpoint-executor-snapshot-failed", "Checkpoint snapshot failed: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_EXECUTE_FAILED =
            define("nop.err.stream.checkpoint-executor-execute-failed", "Task execution failed: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_JOB_GRAPH_INVALID =
            define("nop.err.stream.checkpoint-executor-job-graph-invalid", "Invalid job graph: no TaskLocation for vertex={vertexId} subtask={taskIndex}", ARG_VERTEX_ID, ARG_TASK_INDEX);

    ErrorCode ERR_STREAM_CHAINING_OUTPUT_EXCEPTION =
            define("nop.err.stream.chaining-output-exception", "Error in chaining output: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CHAINING_OUTPUT_CLOSE_FAILED =
            define("nop.err.stream.chaining-output-close-failed", "Failed to close chaining output");

    ErrorCode ERR_STREAM_CHAINING_OUTPUT_FLUSH_FAILED =
            define("nop.err.stream.chaining-output-flush-failed", "Failed to flush chaining output");

    ErrorCode ERR_STREAM_CHAINING_OUTPUT_SNAPSHOT_FAILED =
            define("nop.err.stream.chaining-output-snapshot-failed", "Failed to snapshot chaining output: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CHAINING_OUTPUT_RESTORE_FAILED =
            define("nop.err.stream.chaining-output-restore-failed", "Failed to restore chaining output: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CODEC_VALUE_TYPE_LOAD_FAILED =
            define("nop.err.stream.codec-value-type-load-failed", "Failed to load valueType class: {className}", ARG_CLASS_NAME);

    ErrorCode ERR_STREAM_WINDOW_TRIGGER_STATE_ACCUMULATOR_FAILED =
            define("nop.err.stream.window-trigger-state-accumulator-failed", "Failed to create trigger state accumulator: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_WINDOW_AGGREGATOR_NOT_INITIALIZED =
            define("nop.err.stream.window-aggregator-not-initialized", "Window aggregator not initialized: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_WINDOW_AGGREGATOR_INVALID_STATE =
            define("nop.err.stream.window-aggregator-invalid-state", "Invalid window aggregator state: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_WINDOW_AGGREGATOR_STATE_RESTORE_FAILED =
            define("nop.err.stream.window-aggregator-state-restore-failed", "Window aggregator state restore failed: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_CLASS_NOT_ALLOWED =
            define("nop.err.stream.class-not-allowed", "Class not allowed: {className}", ARG_CLASS_NAME);

    ErrorCode ERR_STREAM_INVALID_TIMESTAMP =
            define("nop.err.stream.invalid-timestamp", "Invalid timestamp for argument {argName}: {detail}",
                    ARG_ARG_NAME, ARG_DETAIL);

    String ARG_EXPECTED = "expected";
    String ARG_JOB_NAME = "jobName";
    String ARG_REASON = "reason";

    ErrorCode ERR_STREAM_NULL_NAME =
            define("nop.err.stream.null-name", "Name must not be null");

    ErrorCode ERR_STREAM_SKIP_NO_MATCH =
            define("nop.err.stream.skip-no-match", "Could not skip to first element of a match");

    ErrorCode ERR_STREAM_PARTITION_KEY_FAILED =
            define("nop.err.stream.partition-key-failed", "Failed to extract key for partitioning");

    ErrorCode ERR_STREAM_BARRIER_INJECTION_FAILED =
            define("nop.err.stream.barrier-injection-failed", "Failed to inject barrier: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_ACCUMULATOR_CREATE_FAILED =
            define("nop.err.stream.accumulator-create-failed", "Failed to create accumulator: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_INTERRUPTED_WRITE =
            define("nop.err.stream.interrupted-write", "Interrupted while writing {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_HASH_NOT_AVAILABLE =
            define("nop.err.stream.hash-not-available", "SHA-256 algorithm not available");

    ErrorCode ERR_STREAM_TASK_FAILED =
            define("nop.err.stream.task-failed", "Task failed");

    ErrorCode ERR_STREAM_JOB_EXECUTE_FAILED =
            define("nop.err.stream.job-execute-failed", "Failed to execute job: {jobName}", ARG_JOB_NAME);

    ErrorCode ERR_STREAM_CHECKPOINT_ABORTED =
            define("nop.err.stream.checkpoint-aborted", "Checkpoint aborted: {reason}", ARG_REASON);
}
