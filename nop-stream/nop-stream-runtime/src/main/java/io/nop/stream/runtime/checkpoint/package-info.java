/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Distributed checkpoint coordination kernel: {@link io.nop.stream.runtime.checkpoint.CheckpointCoordinator}, {@link io.nop.stream.runtime.checkpoint.PendingCheckpoint}, checkpoint plan building, history and retention.
 *
 * <p>Driven by the JobCoordinator face; the local/savepoint face embeds the same coordinator via {@link io.nop.stream.runtime.execution.GraphModelCheckpointExecutor}. Shared restore/savepoint semantics live in {@code checkpoint.core}; persistence lives in {@code checkpoint.storage}.
 */
package io.nop.stream.runtime.checkpoint;
