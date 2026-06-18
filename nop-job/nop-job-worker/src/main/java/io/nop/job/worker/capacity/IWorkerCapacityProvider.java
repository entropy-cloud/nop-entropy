/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.worker.capacity;

import io.nop.job.api.resource.ResourceVector;

/**
 * Worker 启动时声明的资源容量（CPU 毫核 + 内存 MB）。
 * <p>
 * 默认实现 {@code MetadataWorkerCapacityProvider} 从 {@code ServiceInstance.metadata}
 * （key: {@code nop.job.capacity.cpu/memory}）或本地配置读取，启动时缓存。
 * <p>
 * 未声明 capacity 的 worker 返回 {@link ResourceVector#MAX_VALUE}（退化为 count-based 行为），
 * 用于向后兼容。启用资源限制时应在 metadata 或配置中显式声明。
 * <p>
 * 与 dispatcher 侧 {@code IWorkerLoadProvider}（Plan 215，按 serviceName 取所有 worker 的负载）
 * 正交：本接口只回答"我自己（当前 worker 进程）的容量"，worker 侧 scanOnce 用。
 */
public interface IWorkerCapacityProvider {

    /**
     * 返回当前 worker 进程的容量声明。结果在启动后缓存，运行时不变。
     * <p>
     * 未声明时返回 {@link ResourceVector#MAX_VALUE}。
     */
    ResourceVector getMyCapacity();
}
