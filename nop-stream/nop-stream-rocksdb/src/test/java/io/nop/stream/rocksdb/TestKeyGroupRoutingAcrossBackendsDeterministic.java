/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI21 (§八 2): keyed-state routing is deterministic across state backends — the
 * same key lands in the same key group under {@code MemoryKeyedStateBackend}
 * (routeKey → {@code KeyGroupAssignment.assignToKeyGroup}) and
 * {@code RocksDBKeyedStateBackend} ({@code computeKeyGroupId}), because both
 * delegate to the same pure function of (key, maxParallelism). A checkpoint
 * taken on one backend must restore with identical key-group ownership on the
 * other; this parity is what makes the reshard/restore filter sound.
 */
public class TestKeyGroupRoutingAcrossBackendsDeterministic {

    private static final List<String> KEYS = Arrays.asList("k1", "k2", "user-42", "", "中文键");

    @Test
    public void memoryAndRocksDBRouteToTheSameKeyGroups() throws Exception {
        Path dir = Files.createTempDirectory("wi21-rocksdb-parity");
        RocksDBKeyedStateBackend<String> rocks = new RocksDBKeyedStateBackend<>(
                dir.toString(), String.class, 8, null);
        try {
            for (String key : KEYS) {
                int expected = KeyGroupAssignment.assignToKeyGroup(key, 8);
                int rocksGroup = rocks.computeKeyGroupId(key);
                assertEquals(expected, rocksGroup,
                        "key '" + key + "' must route identically across backends");
            }
        } finally {
            rocks.close();
        }
    }

    @Test
    public void maxParallelismScalingStaysConsistentAcrossBackends() throws Exception {
        // each maxParallelism needs its own backend — computeKeyGroupId reads the
        // constructor-fixed job-global value (like a real job's fixed setting)
        for (int mp : new int[]{1, 4, 32}) {
            Path dir = Files.createTempDirectory("wi21-rocksdb-parity-mp" + mp);
            RocksDBKeyedStateBackend<String> rocks = new RocksDBKeyedStateBackend<>(
                    dir.toString(), String.class, mp, null);
            try {
                for (String key : KEYS) {
                    assertEquals(KeyGroupAssignment.assignToKeyGroup(key, mp),
                            rocks.computeKeyGroupId(key),
                            "key '" + key + "' @maxParallelism=" + mp + " must route identically");
                }
            } finally {
                rocks.close();
            }
        }
    }
}
