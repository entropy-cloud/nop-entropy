/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.worker.capacity;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core.NopJobCoreConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.nop.job.core.JobCoreErrors.ARG_METADATA_KEY;
import static io.nop.job.core.JobCoreErrors.ARG_METADATA_VALUE;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_WORKER_CAPACITY_MALFORMED;

/**
 * 默认 capacity provider：启动时从 {@code ServiceInstance.metadata}
 * （key: {@code nop.job.capacity.cpu/memory}，由 worker 启动器在注册服务时注入）
 * 或本地配置 {@code nop.job.capacity.cpu/memory} 读取，缓存为 {@link ResourceVector}。
 * <p>
 * 优先级：metadata &gt; 本地配置 &gt; MAX_VALUE（默认值，向后兼容）。
 * <p>
 * 解析失败（非数字）抛 {@link NopException}，不静默退化为 MAX_VALUE——
 * 防止 worker 误以为有无限容量而过度拉取（design §3.3.5）。
 * <p>
 * 生产部署通常在 worker 启动器（注册 {@code ServiceInstance} 的位置）将 metadata
 * 通过 {@link #setMetadataSource(Map)} 注入到本 bean。未启用服务发现的部署直接用
 * {@code nop.job.capacity.cpu/memory} 配置。
 */
public class MetadataWorkerCapacityProvider implements IWorkerCapacityProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataWorkerCapacityProvider.class);

    /** 显式默认值 0 表示"配置未指定"，触发 MAX_VALUE 回退 */
    private static final int UNSET = 0;

    private Map<String, String> metadataSource;
    private int configCpu = UNSET;
    private int configMemory = UNSET;

    private volatile ResourceVector cached;

    /**
     * 注入本 worker 进程的 {@code ServiceInstance.metadata}。子类或部署脚本在启动时调用。
     * 设为 null 走配置兜底。
     */
    public void setMetadataSource(Map<String, String> metadataSource) {
        this.metadataSource = metadataSource;
    }

    /**
     * 本地配置兜底（用于未启用服务发现的部署）。
     * 显式设为 0 表示"未配置"（与字段初始值一致）。
     */
    @InjectValue("@cfg:nop.job.capacity.cpu|0")
    public void setConfigCpu(int configCpu) {
        this.configCpu = configCpu;
    }

    @InjectValue("@cfg:nop.job.capacity.memory|0")
    public void setConfigMemory(int configMemory) {
        this.configMemory = configMemory;
    }

    @Override
    public ResourceVector getMyCapacity() {
        ResourceVector snapshot = cached;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (cached == null) {
                cached = resolveCapacity();
            }
            return cached;
        }
    }

    /**
     * 暴露给单元测试：强制清缓存重新解析。
     */
    void refreshForTest() {
        cached = null;
    }

    private ResourceVector resolveCapacity() {
        Integer cpu = null;
        Integer memory = null;

        // 优先：从 ServiceInstance.metadata 读取（worker 启动时通过 setMetadataSource 注入）
        if (metadataSource != null) {
            cpu = parseFromMetadata(metadataSource, NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU);
            memory = parseFromMetadata(metadataSource, NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY);
        }

        // 次选：本地配置兜底（仅当 metadata 未提供该维度）
        if (cpu == null && configCpu != UNSET) {
            cpu = configCpu;
        }
        if (memory == null && configMemory != UNSET) {
            memory = configMemory;
        }

        // 默认：未声明该维度退化为 MAX_VALUE（向后兼容）
        int cpuValue = cpu != null ? cpu : NopJobCoreConstants.DEFAULT_CAPACITY_IF_UNDECLARED;
        int memoryValue = memory != null ? memory : NopJobCoreConstants.DEFAULT_CAPACITY_IF_UNDECLARED;

        if (cpu == null && memory == null) {
            LOG.warn("nop.job.worker.capacity-undeclared: worker capacity not declared in metadata or config, "
                    + "falling back to MAX_VALUE (count-based behavior). "
                    + "Set metadata keys [{}, {}] or config [nop.job.capacity.cpu, nop.job.capacity.memory] "
                    + "to enable resource-based worker limit.",
                    NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU,
                    NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY);
        }

        return new ResourceVector(cpuValue, memoryValue);
    }

    /**
     * 解析单个 metadata 字段。返回 null 表示 metadata 未提供该字段；非数字抛 NopException。
     */
    private Integer parseFromMetadata(Map<String, String> metadata, String key) {
        String raw = metadata.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new NopException(ERR_JOB_WORKER_CAPACITY_MALFORMED, e)
                    .param(ARG_METADATA_KEY, key)
                    .param(ARG_METADATA_VALUE, raw);
        }
    }
}
