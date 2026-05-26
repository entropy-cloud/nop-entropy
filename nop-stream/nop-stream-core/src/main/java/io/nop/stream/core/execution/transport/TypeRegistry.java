/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.transport;

import java.util.concurrent.ConcurrentHashMap;
import io.nop.stream.core.exceptions.StreamException;

/**
 * 边（edge）到输出类型类名的注册表。
 *
 * <p>在流拓扑中，每条边对应一个算子的输出类型。TypeRegistry 用于在序列化/反序列化时
 * 查找边对应的输出类型类名，以便正确还原 StreamRecord 中的载荷。
 */
public class TypeRegistry {

    private final ConcurrentHashMap<String, String> edgeToOutputType = new ConcurrentHashMap<>();

    /**
     * 注册边 ID 与输出类型类名的映射。
     *
     * @param edgeId             边的唯一标识
     * @param outputTypeClassName 输出类型的全限定类名
     */
    public void register(String edgeId, String outputTypeClassName) {
        if (edgeId == null || edgeId.isEmpty()) {
            throw new StreamException("edgeId must not be null or empty");
        }
        if (outputTypeClassName == null || outputTypeClassName.isEmpty()) {
            throw new StreamException("outputTypeClassName must not be null or empty");
        }
        edgeToOutputType.put(edgeId, outputTypeClassName);
    }

    /**
     * 获取边对应的输出类型类名。
     *
     * @param edgeId 边的唯一标识
     * @return 输出类型的全限定类名，如果未注册则返回 null
     */
    public String getOutputTypeClassName(String edgeId) {
        return edgeToOutputType.get(edgeId);
    }

    /**
     * 检查边是否已注册。
     *
     * @param edgeId 边的唯一标识
     * @return 如果已注册返回 true
     */
    public boolean isRegistered(String edgeId) {
        return edgeToOutputType.containsKey(edgeId);
    }

    /**
     * 移除边的注册。
     *
     * @param edgeId 边的唯一标识
     */
    public void unregister(String edgeId) {
        edgeToOutputType.remove(edgeId);
    }

    /**
     * 返回已注册的边数量。
     *
     * @return 注册数量
     */
    public int size() {
        return edgeToOutputType.size();
    }

    /**
     * 清空所有注册。
     */
    public void clear() {
        edgeToOutputType.clear();
    }
}
