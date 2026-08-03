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
    String ARG_CURRENT_STATE = "currentState";
    String ARG_TARGET_STATE = "targetState";
    String ARG_ATTEMPT_NUMBER = "attemptNumber";
    String ARG_CAUSE = "cause";
    String ARG_NODE_ID = "nodeId";
    String ARG_POINT_ID = "pointId";
    String ARG_FROM_EPOCH = "fromEpoch";

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

    ErrorCode ERR_STREAM_CHECKPOINT_FAILED =
            define("nop.err.stream.checkpoint-failed", "Checkpoint failed: {reason}", ARG_REASON);

    String ARG_TIMEOUT_MS = "timeoutMs";

    ErrorCode ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT =
            define("nop.err.stream.barrier-alignment-timeout",
                    "Barrier alignment timed out after {timeoutMs}ms: not all input channels delivered barriers within the configured timeout",
                    ARG_TIMEOUT_MS);

    /**
     * Stage 43: a {@code RemoteInputChannel} detected producer failure via the
     * channel heartbeat protocol — neither data, nor heartbeat, nor EOS arrived
     * within {@code channelTimeout}. This is faster than waiting for the coarse
     * lease timeout (~15-20s) and indicates producer death or network partition.
     */
    ErrorCode ERR_STREAM_CHANNEL_TIMEOUT =
            define("nop.err.stream.channel-timeout",
                    "RemoteInputChannel timed out after {timeoutMs}ms with no data, heartbeat, or end-of-stream: producer is presumed dead or partitioned",
                    ARG_TIMEOUT_MS);

    String ARG_EXPECTED_TYPE = "expectedType";
    String ARG_ACTUAL_TYPE = "actualType";

    ErrorCode ERR_STREAM_TYPE_MISMATCH =
            define("nop.err.stream.type-mismatch", "Type mismatch: expected {expectedType} but got {actualType}",
                    ARG_EXPECTED_TYPE, ARG_ACTUAL_TYPE);

    String ARG_EXPECTED_CHECKSUM = "expectedChecksum";
    String ARG_ACTUAL_CHECKSUM = "actualChecksum";

    /**
     * Stage 29: state schema fingerprint mismatch detected at {@code getState()} time.
     * The current descriptor's schema checksum differs from the restored state's
     * descriptor checksum. Stage 29 fails fast (no migration). Stage 33 will extend
     * this path to check for registered {@code StateMigrationFunction}s before failing.
     */
    ErrorCode ERR_STREAM_STATE_SCHEMA_MISMATCH =
            define("nop.err.stream.state-schema-mismatch",
                    "State schema mismatch for state '{stateName}': restored schema checksum differs from current descriptor. "
                            + "expected={expectedChecksum}, actual={actualChecksum}",
                    ARG_STATE_NAME, ARG_EXPECTED_CHECKSUM, ARG_ACTUAL_CHECKSUM);

    ErrorCode ERR_STREAM_CYCLIC_JOB_GRAPH =
            define("nop.err.stream.cyclic-job-graph", "Cyclic job graph detected: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT =
            define("nop.err.stream.window-non-accumulator-merge-conflict",
                    "Cannot merge multiple non-accumulator window values; conflicting values detected", ARG_DETAIL);

    String ARG_ELEMENT_TYPE = "elementType";

    ErrorCode ERR_STREAM_UNSUPPORTED_ELEMENT_TYPE =
            define("nop.err.stream.unsupported-element-type", "Unsupported StreamElement type: {elementType}", ARG_ELEMENT_TYPE);

    String ARG_WATERMARK = "watermark";
    String ARG_WINDOW = "window";

    ErrorCode ERR_STREAM_WINDOW_MERGE_INVALID_WATERMARK =
            define("nop.err.stream.window-merge-invalid-watermark",
                    "Event-time window end timestamp cannot become earlier than current watermark by merging. Current watermark: {watermark}, window: {window}",
                    ARG_WATERMARK, ARG_WINDOW);

    String ARG_PROCESSING_TIME = "processingTime";

    ErrorCode ERR_STREAM_WINDOW_MERGE_INVALID_PROCESSING_TIME =
            define("nop.err.stream.window-merge-invalid-processing-time",
                    "Processing-time window end timestamp cannot become earlier than current processing time by merging. Current processing time: {processingTime}, window: {window}",
                    ARG_PROCESSING_TIME, ARG_WINDOW);

    String ARG_FIELD = "field";

    ErrorCode ERR_STREAM_TUPLE_FIELD_REQUIRED =
            define("nop.err.stream.tuple-field-required", "Aggregation with field index {field} != 0 requires Tuple types", ARG_FIELD);

    ErrorCode ERR_STREAM_NUMBER_REQUIRED =
            define("nop.err.stream.number-required", "Aggregation requires Number elements");

    ErrorCode ERR_STREAM_COMPARABLE_REQUIRED =
            define("nop.err.stream.comparable-required", "Aggregation requires Comparable elements");

    String ARG_EXPECTED_TOKEN = "expectedToken";
    String ARG_ACTUAL_TOKEN = "actualToken";

    /**
     * P0-6: a stale fencing token was presented to a TaskManager RPC entry
     * point. The contract documented on {@code TaskManager} is that any
     * operation carrying an old fencing token is rejected; the prior
     * implementation only warned and returned, silently swallowing the
     * operation (No-Silent-No-Op violation).
     */
    ErrorCode ERR_STREAM_FENCING_TOKEN_MISMATCH =
            define("nop.err.stream.fencing-token-mismatch",
                    "Fencing token mismatch: expected={expectedToken}, actual={actualToken}",
                    ARG_EXPECTED_TOKEN, ARG_ACTUAL_TOKEN);

    String ARG_CHECKPOINT_VERTEX_IDS = "checkpointVertexIds";
    String ARG_CURRENT_VERTEX_IDS = "currentVertexIds";
    String ARG_MISSING_VERTEX_IDS = "missingVertexIds";

    /**
     * P0-7: reverse-direction savepoint vertex differential. The checkpoint
     * contains vertices that are not present in the current graph — i.e. a
     * stateful vertex was removed. Per {@code checkpoint-design.md} §8.6 the
     * safe default is to reject such a restore rather than silently dropping
     * the orphan state.
     */
    ErrorCode ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL =
            define("nop.err.stream.savepoint-vertex-differential",
                    "Checkpoint contains stateful vertices absent from the current graph (likely deleted): "
                            + "missing={missingVertexIds}; checkpoint-vertices={checkpointVertexIds}; current-vertices={currentVertexIds}",
                    ARG_MISSING_VERTEX_IDS, ARG_CHECKPOINT_VERTEX_IDS, ARG_CURRENT_VERTEX_IDS);

    String ARG_DISCOVERY_ONLY = "discoveryOnly";
    String ARG_REGISTRY_ONLY = "registryOnly";

    String ARG_OLD_PARALLELISM = "oldParallelism";
    String ARG_NEW_PARALLELISM = "newParallelism";

    /**
     * Stage 47 (unaligned checkpoint + rescale interaction): a rescale restore
     * (parallelism change) detected that the source checkpoint carries non-empty
     * channel state (in-flight data captured during an unaligned checkpoint).
     * Channel state cannot be redistributed across a new parallelism in the first
     * version (no {@code InflightDataRescalingDescriptor}); silently dropping it
     * would break exactly-once. The restore path therefore fails fast and asks
     * the user to recover from an aligned checkpoint instead. See
     * {@code checkpoint-design.md} §2.11.8.
     */
    ErrorCode ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED =
            define("nop.err.stream.channel-state-rescale-unsupported",
                    "Cannot rescale (parallelism change) from a checkpoint that carries unaligned channel state: "
                            + "in-flight data cannot be redistributed across the new parallelism. "
                            + "vertex={vertexId}, oldParallelism={oldParallelism}, newParallelism={newParallelism}. "
                            + "Recover from an aligned checkpoint instead.",
                    ARG_VERTEX_ID, ARG_OLD_PARALLELISM, ARG_NEW_PARALLELISM);

    /**
     * Stage 41 D7 (Option B coexistence): the optional discovery-read cross-check
     * detected divergence between the platform discovery view and the
     * {@code ClusterRegistry} runtime source of truth. The two views are
     * eventually consistent (same DB, different tables, non-transactional), but
     * persistent divergence indicates a missed registration or a stale lease. The
     * checker fails loud rather than silently swallowing the drift (No-Silent-No-Op).
     */
    ErrorCode ERR_STREAM_DISCOVERY_DRIFT =
            define("nop.err.stream.discovery-drift",
                    "Discovery/registry drift detected: instances only in discovery={discoveryOnly}, "
                            + "nodes only in registry={registryOnly}",
                    ARG_DISCOVERY_ONLY, ARG_REGISTRY_ONLY);

    /**
     * Stage 44 successor 1 (materialization point mechanism, option B): a write
     * was attempted on a sealed {@code IMaterializationPoint}. Sealed points are
     * immutable; the producer must not continue dual-writing after seal. Fails
     * fast rather than silently dropping the element (No-Silent-No-Op).
     */
    ErrorCode ERR_STREAM_MATERIALIZE_POINT_SEALED =
            define("nop.err.stream.materialize-point-sealed",
                    "Materialization point {pointId} is sealed: {detail}", ARG_POINT_ID, ARG_DETAIL);

    /**
     * Stage 44 successor 1 (materialization point mechanism, option B): the
     * consumer-side replay path was invoked on a channel whose underlying
     * {@code ResultPartition} has no materialization point attached (i.e. the
     * {@code JobEdge} materialization marker is off). Fails fast rather than
     * silently returning an empty replay (No-Silent-No-Op): a replay request on
     * a non-materialized edge is a programming error in the recovery path.
     */
    ErrorCode ERR_STREAM_MATERIALIZE_POINT_NOT_ATTACHED =
            define("nop.err.stream.materialize-point-not-attached",
                    "No materialization point attached to this channel/partition: cannot replay. {detail}",
                    ARG_DETAIL);

    /**
     * Stage 44 successor 1: dual-write bypass was enabled (a materialization
     * point is attached) but the bypass write to the materialization store
     * failed. The producer fails fast rather than continuing with a divergent
     * main-queue/materialization-store pair (which would break recovery).
     */
    ErrorCode ERR_STREAM_MATERIALIZE_WRITE_FAILED =
            define("nop.err.stream.materialize-write-failed",
                    "Materialization bypass write failed for point {pointId}: {detail}",
                    ARG_POINT_ID, ARG_DETAIL);
}
