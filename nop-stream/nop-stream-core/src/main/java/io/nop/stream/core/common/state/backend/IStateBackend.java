/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import java.io.Serializable;

import io.nop.stream.core.common.state.shard.KeyGroup;

/**
 * 状态后端接口，用于创建 IKeyedStateBackend。
 * 
 * <p>简化版本：相比 Flink 的 StateBackend，去除了 Checkpoint 相关方法，
 * 只保留创建 KeyedStateBackend 的能力。
 * 
 * <p>实现可以是：
 * <ul>
 *     <li>{@link MemoryStateBackend} - 内存实现，用于测试与嵌入式执行</li>
 *     <li>{@code RocksDBStateBackend}（nop-stream-rocksdb 模块）- RocksDB 持久化实现，
 *         用于生产环境；该实现位于独立模块，-core 不直接依赖</li>
 * </ul>
 */
public interface IStateBackend extends Serializable {

    /**
     * 获取状态后端名称
     *
     * @return 状态后端名称
     */
    String getName();

    /**
     * 创建 KeyedStateBackend
     *
     * @param <K>    key 的类型
     * @param keyType key 的 Class 对象，用于 JSON 序列化
     * @return KeyedStateBackend 实例
     */
    <K> IKeyedStateBackend<K> createKeyedStateBackend(Class<K> keyType);

    IOperatorStateBackend createOperatorStateBackend();

    /**
     * Stage 35: job-global key-group upper bound used by this backend. The value
     * is constant for the job lifetime, which is what keeps the key&#8594;group
     * mapping stable across {@code parallelism}-only rescales. Implementations
     * that do not model key groups return {@link KeyGroup#DEFAULT_MAX_PARALLELISM}.
     */
    default int getMaxParallelism() {
        return KeyGroup.DEFAULT_MAX_PARALLELISM;
    }

    /**
     * plan 2511 C5-002 (engine checkpoint-mode coherence): pin the snapshot FORM
     * produced by the keyed state backends created from this factory. The
     * runtime engine's restore path ({@code RescaleStateAssembler}) merges
     * full-form {@code "states"} maps; a backend producing incremental markers
     * while the job's checkpoint coordinator runs non-incremental would make
     * every restore silently lose keyed state. The engine therefore pins the
     * backend form to the coordinator's incremental mode; direct/embedded
     * backend users keep the backend's own default (incremental, per C5-002
     * adjudication 5).
     *
     * <p>Default: no-op — backends whose snapshot form is fixed (e.g. the
     * Memory backend, which only produces full-form snapshots) ignore the pin.
     */
    default void setIncrementalCheckpointEnabled(boolean incrementalCheckpointEnabled) {
        // no-op default: backends without an incremental/full snapshot-form
        // duality have nothing to pin.
    }
}
