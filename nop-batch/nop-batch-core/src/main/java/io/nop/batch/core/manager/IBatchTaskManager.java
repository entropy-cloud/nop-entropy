package io.nop.batch.core.manager;

import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.context.IServiceContext;

public interface IBatchTaskManager {

    default IBatchTaskContext newBatchTaskContext() {
        return newBatchTaskContext(null);
    }

    IBatchTaskContext newBatchTaskContext(IServiceContext svcCtx);

    IBatchTask newBatchTask(String batchTaskName, Long batchTaskVersion, IBatchTaskContext taskContext);
}
