/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.api.core.message.IMessageService;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R5 CC-07 regression pin: {@code governanceSweep} contains unexpected
 * exceptions per run (returns 0, logs) instead of propagating — an uncaught
 * throw from a scheduleAtFixedRate task permanently stops all subsequent runs.
 */
class TestGovernanceSweepContainment {

    @Test
    void sweepReturnsIntWithoutThrowingOnFreshManager() throws Exception {
        Path tempDir = Files.createTempDirectory("cc07-sweep");
        InMemoryClusterRegistry clusterRegistry = new InMemoryClusterRegistry();
        IMessageService messageService = new io.nop.message.core.local.LocalMessageService();
        OpsJobManager manager = new OpsJobManager(messageService, clusterRegistry,
                new LocalFileCheckpointStorage(tempDir.toString()),
                Map.<String, IStreamTaskRpcService>of());
        try {
            int removed = assertDoesNotThrow(manager::governanceSweep);
            assertEquals(0, removed, "fresh manager has nothing to sweep");
            // a second run must still be reachable (containment keeps the task alive)
            assertDoesNotThrow(manager::governanceSweep);
        } finally {
            manager.close();
        }
    }
}
