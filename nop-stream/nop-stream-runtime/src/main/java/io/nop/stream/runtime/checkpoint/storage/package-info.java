/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Checkpoint persistence: the {@code ICheckpointStorage} implementations (local file / JDBC / S3), SerDe and the {@link io.nop.stream.runtime.checkpoint.storage.CheckpointStorages} factory — the single choke-point for storage construction (WI11 plan 2432; direct {@code new LocalFileCheckpointStorage(...)} outside this package is a violation).
 */
package io.nop.stream.runtime.checkpoint.storage;
