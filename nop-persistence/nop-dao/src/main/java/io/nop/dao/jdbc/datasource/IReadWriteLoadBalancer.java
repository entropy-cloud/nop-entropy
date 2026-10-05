/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import java.util.List;

/**
 * 读写分离（plan 2319）从库负载均衡器：对过滤掉冷却中从库后的候选列表给出尝试顺序。
 * 获取连接失败时按此顺序换下一台（设计文档 §3.5）。
 */
public interface IReadWriteLoadBalancer {

    /**
     * 返回候选从库的尝试顺序。不得修改入参列表，返回列表包含全部候选（顺序不同）
     */
    List<ReplicaChannel> order(List<ReplicaChannel> candidates);
}
