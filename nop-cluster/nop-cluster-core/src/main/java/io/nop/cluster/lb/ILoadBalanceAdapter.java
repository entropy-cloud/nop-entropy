/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.lb;

public interface ILoadBalanceAdapter<T> {
    int getWeight(T candidate);

    int getActiveCount(T candidate);

    /**
     * 4.2-C8-001（plan 2559 裁定 G/R25-2）：调用开始时对候选实例的活跃计数 +1。
     * 缺省空实现——无活跃计数语义的 adapter 保持恒定行为。
     */
    default void begin(T candidate) {
    }

    /**
     * 调用终结（成功/异常/超时/取消）时对候选实例的活跃计数 -1。
     */
    default void end(T candidate) {
    }
}
