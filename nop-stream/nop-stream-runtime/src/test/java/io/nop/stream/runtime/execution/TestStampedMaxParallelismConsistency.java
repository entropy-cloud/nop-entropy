/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 KR-2 regression pins: the stamped (P, M) ownership metadata is consumed at
 * restore — a checkpoint stamped with a different key-group bound than the live
 * routing bound must fail fast with migration guidance; legacy (unstamped)
 * snapshots skip the check.
 */
class TestStampedMaxParallelismConsistency {

    private static final TaskLocation LOC_0 = new TaskLocation("job-kr2", "pipeline-0", "v", 0);

    private static GraphModelCheckpointExecutor.TaskStateLookup lookupOf(TaskStateSnapshot snap) {
        return snap == null ? null : loc -> snap;
    }

    @Test
    void stampedMismatchFailsFastWithMigrationGuidance() {
        TaskEpochSnapshot stamped = new TaskEpochSnapshot(LOC_0, 7L);
        stamped.setKeyGroupOwnership(4, 4, new io.nop.stream.core.common.state.shard.KeyGroupRange(0, 3));

        StreamException e = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.assertStampedMaxParallelismConsistency(
                        Collections.singleton(LOC_0), lookupOf(stamped), 8));
        assertTrue(String.valueOf(e.getParam(NopStreamErrors.ARG_DETAIL))
                        .contains("MaxParallelismReshardMigration"),
                "mismatch must point at the migration tool: " + e.getParam(NopStreamErrors.ARG_DETAIL));
    }

    @Test
    void stampedMatchPasses() throws Exception {
        TaskEpochSnapshot stamped = new TaskEpochSnapshot(LOC_0, 7L);
        stamped.setKeyGroupOwnership(8, 8, new io.nop.stream.core.common.state.shard.KeyGroupRange(0, 7));
        GraphModelCheckpointExecutor.assertStampedMaxParallelismConsistency(
                Collections.singleton(LOC_0), lookupOf(stamped), 8);
    }

    @Test
    void unstampedLegacySnapshotSkipsCheck() throws Exception {
        // legacy shape: plain snapshot without ownership materialization
        TaskStateSnapshot legacy = new TaskStateSnapshot(LOC_0, 7L);
        GraphModelCheckpointExecutor.assertStampedMaxParallelismConsistency(
                Collections.singleton(LOC_0), lookupOf(legacy), 8);
    }
}
