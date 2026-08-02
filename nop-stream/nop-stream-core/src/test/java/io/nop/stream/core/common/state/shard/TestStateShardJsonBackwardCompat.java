/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 34: verifies that adding {@code maxParallelism} to {@link StateShard}
 * does not break backward compatibility.
 *
 * <p>Production checkpoints never populate the {@code shards} list of
 * {@link TaskEpochSnapshot} (no live code calls {@code addShard}), and
 * {@code CheckpointSerDe} does not serialize the {@code shards} field at all.
 * So the real backward-compatibility contract is that (a) the legacy
 * {@code StateShard} constructors still produce valid instances with the
 * Stage-34 default applied, and (b) building an empty-shards snapshot still
 * works. The production JSON surface (operator/keyed states) is untouched by
 * this change because {@code shards} was never on the wire.
 */
class TestStateShardJsonBackwardCompat {

    @Test
    void legacyFourArgConstructorAppliesMaxParallelismDefault() {
        // Mirrors how old code (and any old serialized form reconstructed via
        // the legacy constructor) gets the Stage-34 default automatically.
        StateShard legacy = new StateShard(4, 0, 0, "DEFAULT");
        assertEquals(KeyGroup.DEFAULT_MAX_PARALLELISM, legacy.getMaxParallelism());
        assertEquals(4, legacy.getStateShardCount());
        assertEquals(0, legacy.getStateShardId());
    }

    @Test
    void noArgConstructorAppliesMaxParallelismDefault() {
        StateShard noArg = new StateShard();
        assertEquals(KeyGroup.DEFAULT_MAX_PARALLELISM, noArg.getMaxParallelism());
        assertEquals(1, noArg.getStateShardCount());
    }

    @Test
    void singleShardFactoryAppliesMaxParallelismDefault() {
        StateShard single = StateShard.singleShard(2);
        assertEquals(KeyGroup.DEFAULT_MAX_PARALLELISM, single.getMaxParallelism());
    }

    @Test
    void newConstructorSetsExplicitMaxParallelism() {
        StateShard shard = new StateShard(8, 3, 1, "DEFAULT", 256);
        assertEquals(256, shard.getMaxParallelism());
    }

    @Test
    void maxParallelismIncludedInEqualsAndHashCode() {
        StateShard a = new StateShard(4, 0, 0, "DEFAULT", 128);
        StateShard b = new StateShard(4, 0, 0, "DEFAULT", 256);
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void taskEpochSnapshotEmptyShardsRemainsValid() {
        // The production checkpoint reality: an empty shards list.
        TaskLocation loc = new TaskLocation("job1", "pipe1", "v1", 0);
        TaskEpochSnapshot snapshot = new TaskEpochSnapshot(loc, 3);
        assertNotNull(snapshot.getShards());
        assertTrue(snapshot.getShards().isEmpty());

        // Adding a shard still works and the shard carries the new field.
        snapshot.addShard(StateShard.singleShard(0));
        assertEquals(1, snapshot.getShards().size());
        assertEquals(KeyGroup.DEFAULT_MAX_PARALLELISM,
                snapshot.getShards().get(0).getMaxParallelism());
    }
}
