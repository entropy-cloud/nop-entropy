/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.common.state.shard.KeyGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R5 KR-3c regression pin: the RocksDB convenience constructor must default to
 * the platform key-group bound — M=1 made any parallelism&gt;1 keyBy illegal and
 * failed on the first record, far from the misconfiguration.
 */
class TestRocksDbDefaultMaxParallelism {

    @Test
    void convenienceConstructorDefaultsToPlatformKeyGroupBound() {
        RocksDBStateBackend backend = new RocksDBStateBackend("target/tmp-rocksdb-default-m-pin");
        assertEquals(KeyGroup.DEFAULT_MAX_PARALLELISM, backend.getMaxParallelism());
    }
}
