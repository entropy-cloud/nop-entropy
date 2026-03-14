/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import java.io.Serializable;

/**
 * 状态后端接口，用于创建 IKeyedStateBackend。
 * 
 * <p>简化版本：相比 Flink 的 StateBackend，去除了 Checkpoint 相关方法，
 * 只保留创建 KeyedStateBackend 的能力。
 * 
 * <p>实现可以是：
 * <ul>
 *     <li>{@link MemoryStateBackend} - 内存实现，用于测试</li>
 *     <li>{@link RedisStateBackend} - Redis 实现，用于生产环境</li>
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
}
