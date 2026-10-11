/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED;

/**
 * WI11 (plan 2432): single construction entry for checkpoint storages. The
 * creation logic previously lived as {@code GraphModelCheckpointExecutor#
 * createStorage} while five other launch/embed sites bypassed it with direct
 * {@code new LocalFileCheckpointStorage} calls — the local default-directory
 * resolution and the jdbc fail-fast existed only on one of the six paths.
 * All face entry points now construct through this factory.
 */
public final class CheckpointStorages {

    private CheckpointStorages() {
    }

    /**
     * Creates the storage configured by {@code config}: {@code local} (default)
     * from the {@code path} storage property (falling back to the machine-level
     * default directory), {@code s3} from config, and an explicit fail-fast for
     * {@code jdbc} (requires an {@code IJdbcTemplate} that the config cannot
     * carry — construct {@code JdbcCheckpointStorage} directly at the JDBC
     * wiring site).
     */
    public static ICheckpointStorage create(CheckpointConfig config) {
        String storageType = config.getStorageType();
        if ("jdbc".equalsIgnoreCase(storageType)) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED)
                    .param(ARG_DETAIL,
                            "JdbcCheckpointStorage requires IJdbcTemplate configuration. " +
                            "Use storageType='local' or provide JDBC configuration.");
        }
        if ("s3".equalsIgnoreCase(storageType)) {
            // WI13 (plan 2339): S3-compatible object storage
            return S3CheckpointStorage.fromConfig(config);
        }
        if (storageType == null || !"local".equalsIgnoreCase(storageType)) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_FAILED)
                    .param(ARG_DETAIL, "Unknown storage type: " + storageType);
        }
        String basePath = config.getStorageProperty("path");
        if (basePath == null || basePath.isEmpty()) {
            basePath = defaultBaseDir();
        }
        return new LocalFileCheckpointStorage(basePath);
    }

    /**
     * Explicit-path local storage construction — the single choke-point for
     * {@code new LocalFileCheckpointStorage}: savepoint stores, launch/embed/rpc
     * descriptor-path storages are local-by-design (savepoint format is local
     * file), but their construction funnels here so a future storage-type
     * evolution has one place to change.
     */
    public static LocalFileCheckpointStorage local(String basePath) {
        return new LocalFileCheckpointStorage(basePath);
    }

    /**
     * Default (no {@code path} storage property configured) checkpoint base
     * directory. Resolution order: the {@code nop-stream.checkpoint.storage.dir}
     * system property (deployment/ops override, also used by tests to avoid
     * machine-level pollution), then {@code ${java.io.tmpdir}/nop-stream-checkpoints}.
     */
    public static String defaultBaseDir() {
        String override = System.getProperty("nop-stream.checkpoint.storage.dir");
        if (override != null && !override.isBlank()) {
            return override;
        }
        return System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoints";
    }
}
