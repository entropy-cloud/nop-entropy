/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Driver-face execution: the local/savepoint executor {@link io.nop.stream.runtime.execution.GraphModelCheckpointExecutor} (embeds {@code checkpoint.CheckpointCoordinator}), the embedded / RPC distributed executors, supervision loop, rescale state assembly, TM-side checkpoint wiring and deployment plan generation.
 *
 * <p>WI11 adjudication (plan 2432): NOT split further — the remaining fan-out stems from the two mega-classes (GMCE / JobCoordinator); see RD-05 for the class-level split.
 */
package io.nop.stream.runtime.execution;
