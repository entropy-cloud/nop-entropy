/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.nio.ByteBuffer;

import io.nop.stream.core.common.state.TtlContext;

import org.rocksdb.ColumnFamilyHandle;

/**
 * Marker implemented by every RocksDB keyed-state class so that
 * {@link RocksDBKeyedStateBackend} can bind and inspect a {@link TtlContext} without an
 * 8-way {@code instanceof} ladder. The sidecar key type is {@link ByteBuffer} wrapping
 * the <b>base</b> composite storage key (namespace + shard + raw key, without any map-key
 * suffix), so a whole {@code MapState} map shares one TTL timestamp.
 */
interface RocksDbTtlAware {

    /** Attach (or re-attach, after restore) a TTL sidecar to this state. */
    void bindTtl(TtlContext<ByteBuffer> ctx);

    /** The currently bound sidecar, or {@code null} when TTL is disabled. */
    TtlContext<ByteBuffer> ttlContext();

    /** The RocksDB column family this state lives in (used by the background sweep). */
    ColumnFamilyHandle cfHandle();
}
