/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import java.util.List;

/**
 * 提供 worker 负载视图（capacity + reserved），供 dispatcher 侧 best-fit 派发决策。
 */
public interface IWorkerLoadProvider {
    /**
     * 获取指定服务的所有 worker 负载快照。
     *
     * @param serviceName 服务名（对应 ServiceInstance.serviceName）
     * @return worker 负载列表，可能为空
     */
    List<WorkerLoad> getWorkerLoads(String serviceName);

    /**
     * AR-96 per-scan 缓存生命周期：dispatcher 在一次 {@code scanOnce} 批处理开始时调用
     * {@code beginScan}、结束时调用 {@code endScan}。实现可在 scan 作用域内缓存服务发现 + 聚合结果，
     * 使同一 scan 内对同一 serviceName 的多次 {@code getWorkerLoads} 调用不随 fire 数线性增长
     *（batchSize=100 时避免 100 次发现 + 100 次 GROUP BY 聚合）。默认空实现（向后兼容）。
     */
    default void beginScan() {
    }

    default void endScan() {
    }
}
