package io.nop.batch.dsl.runner;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.manager.IBatchTaskManager;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public class BatchTaskRunner implements IBatchTaskRunner {

    private IBatchTaskManager batchTaskManager;

    @Inject
    public void setBatchTaskManager(IBatchTaskManager batchTaskManager) {
        this.batchTaskManager = batchTaskManager;
    }

    @Override
    public CompletionStage<Void> executeAsync(@Name("taskPath") String taskPath,
                                               @Name("params") Map<String, Object> params) {
        IBatchTask task = batchTaskManager.loadBatchTaskFromPath(taskPath, BeanContainer.instance());
        IBatchTaskContext context = batchTaskManager.newBatchTaskContext();
        if (params != null) {
            context.setParams(params);
        }
        return task.executeAsync(context);
    }
}
