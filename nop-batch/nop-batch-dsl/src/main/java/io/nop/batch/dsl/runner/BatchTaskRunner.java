package io.nop.batch.dsl.runner;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.manager.IBatchTaskManager;
import io.nop.cluster.naming.PartitionResolver;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public class BatchTaskRunner implements IBatchTaskRunner {

    private IBatchTaskManager batchTaskManager;

    /**
     * 可选的分区解析器。由调用方（job 侧）通过 beans.xml 注入；
     * 批处理任务本身不负责解析分区，仅在执行前调用已注入的 resolver 把分区写入 context。
     * 未注入时（如纯 batch 部署无 job 模块）为 null，不追加分区过滤。
     */
    private PartitionResolver partitionResolver;

    @Inject
    public void setBatchTaskManager(IBatchTaskManager batchTaskManager) {
        this.batchTaskManager = batchTaskManager;
    }

    public void setPartitionResolver(PartitionResolver partitionResolver) {
        this.partitionResolver = partitionResolver;
    }

    @Override
    public CompletionStage<Void> executeAsync(@Name("taskPath") String taskPath,
                                               @Name("params") Map<String, Object> params) {
        IBatchTaskContext context = batchTaskManager.newBatchTaskContext();
        if (params != null) {
            context.setParams(params);
        }
        if (partitionResolver != null) {
            IntRangeSet partitions = partitionResolver.resolvePartitions();
            if (partitions != null) {
                context.setPartitionRange(partitions);
            }
        }
        IBatchTask task = batchTaskManager.loadBatchTaskFromPath(taskPath, BeanContainer.instance());
        return task.executeAsync(context);
    }
}
