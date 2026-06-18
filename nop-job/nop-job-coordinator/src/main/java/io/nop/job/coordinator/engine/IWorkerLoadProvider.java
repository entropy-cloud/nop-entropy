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
}
