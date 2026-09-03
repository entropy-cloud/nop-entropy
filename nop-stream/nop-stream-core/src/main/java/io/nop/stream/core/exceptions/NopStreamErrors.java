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
    String ARG_OUTPUT_TAG = "outputTag";

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

    /**
     * RL-7 (R15-AR-4): side output has no registered consumer in the chained execution — fail
     * fast instead of silently dropping (plan guide #24).
     */
    ErrorCode ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER =
            define("nop.err.stream.side-output-no-consumer", "Side output {outputTag} has no registered consumer: {detail}",
                    ARG_OUTPUT_TAG, ARG_DETAIL);

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

    String ARG_REGION_ID = "regionId";
    String ARG_MAX_RESTARTS = "maxRestarts";

    /**
     * Stage 44 successor 3 (supervision loop): a task failed mid-execution and
     * the supervision loop detected it. This error surfaces the failure for the
     * single-region case (where scoped restart is not applicable — there is no
     * materialization boundary to contain the blast radius) and for the
     * global-recovery fallback path.
     */
    ErrorCode ERR_STREAM_SUPERVISION_TASK_FAILED =
            define("nop.err.stream.supervision-task-failed",
                    "Supervision loop detected task failure for vertex={vertexId} taskIndex={taskIndex} region={regionId}: {detail}",
                    ARG_VERTEX_ID, ARG_TASK_INDEX, ARG_REGION_ID, ARG_DETAIL);

    /**
     * Stage 44 successor 3 (supervision loop): the per-region restart budget
     * was exhausted. The supervision loop attempted to restart the failing
     * region {regionId} but it exceeded the configured maxRestarts={maxRestarts}.
     * Falls back to global recovery (whole-job) — the caller (typically
     * GraphModelCheckpointExecutor) surfaces this as a hard failure so the
     * existing recovery path can take over.
     */
    ErrorCode ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED =
            define("nop.err.stream.supervision-restart-exhausted",
                    "Region restart budget exhausted for region={regionId}: attempts exceeded maxRestarts={maxRestarts}. Falling back to global recovery.",
                    ARG_REGION_ID, ARG_MAX_RESTARTS);

    String ARG_TASK_KEY = "taskKey";

    /**
     * P1 hardening (Phase 4): a task did not reach a terminal state within the
     * cooperative-cancel budget during a region-scoped restart. The previous
     * behavior silently fell through after a WARN and rebuilt/resubmitted a
     * second task instance, producing a zombie (two producers writing the same
     * {@code ResultPartition}, racing on {@code currentMaterializationEpoch},
     * breaking exactly-once). This error fails loud so the caller surfaces the
     * failure for recovery (local/embedded path via {@code env.execute()};
     * distributed path via FAILED report + {@code autoRecoverOnFailedReport})
     * instead of silently creating a zombie.
     *
     * <p>Distinct from {@link #ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED} (which is
     * the region-restart budget exhaustion at {@code SupervisionLoop:255}) so the
     * two conditions remain distinguishable on an ops dashboard.
     */
    ErrorCode ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT =
            define("nop.err.stream.supervision-zombie-task-timeout",
                    "Task {taskKey} did not reach a terminal state within the cancel budget "
                            + "during region restart (vertex={vertexId} taskIndex={taskIndex} region={regionId}). "
                            + "Refusing to rebuild a second task instance (would create a zombie). Failing loud for recovery.",
                    ARG_TASK_KEY, ARG_VERTEX_ID, ARG_TASK_INDEX, ARG_REGION_ID);

    /**
     * Stage 44 successor 3 (supervision loop): a region-scoped restart was
     * attempted but the region contains producer vertices that cannot be safely
     * restarted without the drain/reconnect protocol (successor plan 4). The
     * supervision loop falls back to global recovery rather than silently
     * producing an inconsistent state (No-Silent-No-Op).
     */
    ErrorCode ERR_STREAM_REGION_RESTART_UNSUPPORTED =
            define("nop.err.stream.region-restart-unsupported",
                    "Region {regionId} cannot be safely restarted: it contains producer vertices requiring drain/reconnect (successor plan 4). Falling back to global recovery.",
                    ARG_REGION_ID);

    String ARG_SINK_NAME = "sinkName";
    String ARG_PARALLELISM = "parallelism";

    /**
     * Fail-fast gate (CONN-01 P1): a {@code TwoPhaseCommitSinkFunction} sink deployed at
     * effective parallelism > 1 is rejected at planning time. The built-in 2PC sinks
     * (JdbcTwoPhaseCommitSink, FileTwoPhaseCommitSink) silently lose data at parallelism > 1
     * because (a) their idempotency guard keys on the job-global epochId only, (b) the UDF is
     * shared across subtasks so the base-class pendingCommits map collides, and (c) the sink
     * receives no operatorId/subtaskIndex at runtime. Full parallel exactly-once requires a
     * sink identity-injection layer (see {@code checkpoint-design.md} §6.4.1) and is deferred
     * to a successor plan. parallelism=1 is the proven, supported path.
     */
    ErrorCode ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED =
            define("nop.err.stream.2pc-sink-parallelism-not-supported",
                    "Two-phase-commit sink '{sinkName}' does not support parallelism > 1: "
                            + "requested parallelism={parallelism}. Exactly-once output is only proven at parallelism=1; "
                            + "parallelism>1 would silently lose data. Use parallelism=1 or wait for the parallel-2PC successor capability.",
                    ARG_SINK_NAME, ARG_PARALLELISM);

    // ------------------------------------------------------------------
    // nop-stream-flow DSL contract error codes (P1-XDSL-5 / P1-XDSL-6 / P1-09-02)
    // ------------------------------------------------------------------

    String ARG_ID = "id";
    String ARG_ELEMENT = "element";
    String ARG_ATTR_NAME = "attrName";
    String ARG_ATTR_VALUE = "attrValue";
    String ARG_EDGE_ID = "edgeId";
    String ARG_TRANSFORM_ID = "transformId";
    String ARG_REF_TYPE = "refType";
    String ARG_REF_NAME = "refName";
    String ARG_PARTITION = "partition";
    String ARG_BEAN_NAME = "beanName";
    String ARG_EXPECTED_STREAM_TYPE = "expectedStreamType";
    String ARG_ACTUAL_STREAM_TYPE = "actualStreamType";

    ErrorCode ERR_STREAM_DUPLICATE_ID =
            define("nop.err.stream.duplicate-id",
                    "Duplicate Stream DSL {element} id: {id}", ARG_ELEMENT, ARG_ID);

    ErrorCode ERR_STREAM_REQUIRED_ATTR =
            define("nop.err.stream.required-attr",
                    "Stream DSL {element} requires attribute {attrName}", ARG_ELEMENT, ARG_ATTR_NAME);

    ErrorCode ERR_STREAM_REQUIRED_BODY =
            define("nop.err.stream.required-body",
                    "Stream DSL {element} must declare either bean=\"...\" or inline <source>xpl</source>",
                    ARG_ELEMENT);

    ErrorCode ERR_STREAM_REF_UNKNOWN =
            define("nop.err.stream.ref-unknown",
                    "Stream DSL {element} references unknown {refType} '{refName}'",
                    ARG_ELEMENT, ARG_REF_TYPE, ARG_REF_NAME);

    ErrorCode ERR_STREAM_NOT_IMPLEMENTED =
            define("nop.err.stream.not-implemented", "not yet implemented: {detail}", ARG_DETAIL);

    ErrorCode ERR_STREAM_UPSTREAM_TYPE =
            define("nop.err.stream.upstream-type",
                    "Stream DSL {element} requires a {expectedStreamType} upstream, got {actualStreamType}",
                    ARG_ELEMENT, ARG_EXPECTED_STREAM_TYPE, ARG_ACTUAL_STREAM_TYPE);

    ErrorCode ERR_STREAM_UPSTREAM_NULL =
            define("nop.err.stream.upstream-null",
                    "Stream DSL {element} upstream is null", ARG_ELEMENT);

    ErrorCode ERR_STREAM_UPSTREAM_NOT_STREAM =
            define("nop.err.stream.upstream-not-stream",
                    "Stream DSL {element} upstream is not a recognized stream type: {actualStreamType}",
                    ARG_ELEMENT, ARG_ACTUAL_STREAM_TYPE);

    ErrorCode ERR_STREAM_EDGE_HASH_KEY_EXPR_REQUIRED =
            define("nop.err.stream.edge-hash-key-expr-required",
                    "Stream DSL edge {edgeId} declares partition=\"HASH\" without keyExpr; "
                            + "declare keyExpr=\"...\" or use FORWARD", ARG_EDGE_ID);

    ErrorCode ERR_STREAM_EDGE_HASH_REDUNDANT =
            define("nop.err.stream.edge-hash-redundant",
                    "Stream DSL edge {edgeId} declares partition=\"HASH\" but its target transform "
                            + "{transformId} is already a keyBy transform; use FORWARD",
                    ARG_EDGE_ID, ARG_TRANSFORM_ID);

    ErrorCode ERR_STREAM_EDGE_KEY_EXPR_WITHOUT_HASH =
            define("nop.err.stream.edge-key-expr-without-hash",
                    "Stream DSL edge {edgeId} declares keyExpr but partition is not HASH; "
                            + "keyExpr is only consumed with partition=\"HASH\"", ARG_EDGE_ID);

    ErrorCode ERR_STREAM_EDGE_PARTITION_UNSUPPORTED =
            define("nop.err.stream.edge-partition-unsupported",
                    "Stream DSL edge {edgeId} declares partition={partition} which is not yet "
                            + "implemented in nop-stream-core; use FORWARD or a <keyBy> transform",
                    ARG_EDGE_ID, ARG_PARTITION);

    ErrorCode ERR_STREAM_EDGE_ATTR_UNSUPPORTED =
            define("nop.err.stream.edge-attr-unsupported",
                    "Stream DSL edge {edgeId} declares {attrName} which is not yet implemented "
                            + "in nop-stream-core; remove the attribute",
                    ARG_EDGE_ID, ARG_ATTR_NAME);

    ErrorCode ERR_STREAM_WINDOW_ATTR_UNSUPPORTED =
            define("nop.err.stream.window-attr-unsupported",
                    "Stream DSL {element} declares {attrName}={attrValue} which is not yet "
                            + "implemented in nop-stream-core windowing; remove the declaration "
                            + "or use the default value",
                    ARG_ELEMENT, ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_STREAM_BEAN_NOT_FOUND =
            define("nop.err.stream.bean-not-found",
                    "Stream DSL bean reference not found: bean='{beanName}'", ARG_BEAN_NAME);

    ErrorCode ERR_STREAM_BEAN_TYPE_MISMATCH =
            define("nop.err.stream.bean-type-mismatch",
                    "Stream DSL bean '{beanName}' is not a {expectedType}: actual={actualType}",
                    ARG_BEAN_NAME, ARG_EXPECTED_TYPE, ARG_ACTUAL_TYPE);

    // ------------------------------------------------------------------
    // Connector SPI registry error codes (item 19 / P-REQ-28)
    // ------------------------------------------------------------------

    String ARG_TYPE_NAME = "typeName";
    String ARG_DIRECTION = "direction";
    String ARG_REGISTERED_TYPES = "registeredTypes";
    String ARG_EXPECTED_DIRECTION = "expectedDirection";
    String ARG_ACTUAL_DIRECTION = "actualDirection";
    String ARG_FACTORY_CLASS = "factoryClass";
    String ARG_EXISTING_FACTORY_CLASS = "existingFactoryClass";
    String ARG_PARAM_NAME = "paramName";
    String ARG_PARAM_KIND = "paramKind";
    String ARG_DECLARED_VALUE = "declaredValue";
    String ARG_ACTUAL_VALUE = "actualValue";

    ErrorCode ERR_STREAM_CONNECTOR_TYPE_NOT_FOUND =
            define("nop.err.stream.connector-type-not-found",
                    "Stream connector type '{typeName}' ({direction}) is not registered. "
                            + "Registered {direction} types: {registeredTypes}",
                    ARG_TYPE_NAME, ARG_DIRECTION, ARG_REGISTERED_TYPES);

    ErrorCode ERR_STREAM_CONNECTOR_DIRECTION_MISMATCH =
            define("nop.err.stream.connector-direction-mismatch",
                    "Stream connector type '{typeName}' is registered as {actualDirection}, "
                            + "not {expectedDirection}",
                    ARG_TYPE_NAME, ARG_ACTUAL_DIRECTION, ARG_EXPECTED_DIRECTION);

    ErrorCode ERR_STREAM_CONNECTOR_DUPLICATE_TYPE =
            define("nop.err.stream.connector-duplicate-type",
                    "Duplicate stream connector registration: {direction} type name '{typeName}' "
                            + "(or alias) is registered by both '{existingFactoryClass}' and '{factoryClass}'",
                    ARG_DIRECTION, ARG_TYPE_NAME, ARG_EXISTING_FACTORY_CLASS, ARG_FACTORY_CLASS);

    ErrorCode ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID =
            define("nop.err.stream.connector-descriptor-invalid",
                    "Stream connector factory '{factoryClass}' declares an invalid capability descriptor: {detail}",
                    ARG_FACTORY_CLASS, ARG_DETAIL);

    ErrorCode ERR_STREAM_CONNECTOR_PARAM_REQUIRED =
            define("nop.err.stream.connector-param-required",
                    "Stream connector '{typeName}' requires config param '{paramName}' of kind {paramKind}",
                    ARG_TYPE_NAME, ARG_PARAM_NAME, ARG_PARAM_KIND);

    ErrorCode ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH =
            define("nop.err.stream.connector-capability-mismatch",
                    "Stream connector '{typeName}' descriptor declares {declaredValue} but the constructed "
                            + "endpoint reports {actualValue}",
                    ARG_TYPE_NAME, ARG_DECLARED_VALUE, ARG_ACTUAL_VALUE);

    // ------------------------------------------------------------------
    // Pre-submit validation error codes (item 20 / P-REQ-13/14)
    // ------------------------------------------------------------------

    String ARG_DECLARED_PARAMS = "declaredParams";
    String ARG_FIELD_NAME = "fieldName";
    String ARG_FIELD_VALUE = "fieldValue";
    String ARG_CREDENTIAL_ID = "credentialId";
    String ARG_CONNECTOR_ENDPOINT = "connectorEndpoint";
    String ARG_LAYER = "layer";

    ErrorCode ERR_STREAM_CONNECTOR_PARAM_UNKNOWN =
            define("nop.err.stream.connector-param-unknown",
                    "Stream connector '{typeName}' does not declare config param '{paramName}'. "
                            + "Declared params: {declaredParams}",
                    ARG_TYPE_NAME, ARG_PARAM_NAME, ARG_DECLARED_PARAMS);

    ErrorCode ERR_STREAM_CREDENTIAL_REF_INVALID =
            define("nop.err.stream.credential-ref-invalid",
                    "Invalid credential reference '{fieldValue}' on field '{fieldName}': expected syntax "
                            + "credential:{credentialId}#{field}",
                    ARG_FIELD_NAME, ARG_FIELD_VALUE, ARG_CREDENTIAL_ID);

    ErrorCode ERR_STREAM_CREDENTIAL_PROVIDER_MISSING =
            define("nop.err.stream.credential-provider-missing",
                    "Credential reference '{fieldValue}' on field '{fieldName}' cannot be resolved: no "
                            + "ICredentialProvider is available (fail-closed). Inject the provider before "
                            + "starting or validating the connector.",
                    ARG_FIELD_NAME, ARG_FIELD_VALUE);

    ErrorCode ERR_STREAM_CREDENTIAL_UNRESOLVED =
            define("nop.err.stream.credential-unresolved",
                    "Credential '{credentialId}' (referenced by field '{fieldName}') could not be resolved: "
                            + "it does not exist or has been soft-deleted (fail-closed). "
                            + "Original failure: {detail}",
                    ARG_CREDENTIAL_ID, ARG_FIELD_NAME, ARG_FIELD_VALUE, ARG_DETAIL);

    ErrorCode ERR_STREAM_CONNECTIVITY_CHECK_FAILED =
            define("nop.err.stream.connectivity-check-failed",
                    "Connectivity check failed for connector endpoint '{connectorEndpoint}': {detail}",
                    ARG_CONNECTOR_ENDPOINT, ARG_DETAIL);

    ErrorCode ERR_STREAM_CONNECTIVITY_NOT_SUPPORTED =
            define("nop.err.stream.connectivity-not-supported",
                    "Connector endpoint '{connectorEndpoint}' implements no probe contract: dry-run cannot "
                            + "determine its connectivity ({detail})",
                    ARG_CONNECTOR_ENDPOINT, ARG_DETAIL);
}
