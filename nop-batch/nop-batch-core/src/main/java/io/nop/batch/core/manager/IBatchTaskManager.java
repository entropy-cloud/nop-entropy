package io.nop.batch.core.manager;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskBuilder;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;

public interface IBatchTaskManager {

    default IBatchTaskContext newBatchTaskContext() {
        return newBatchTaskContext(null);
    }

    IBatchTaskContext newBatchTaskContext(IServiceContext svcCtx, IEvalScope scope);

    default IBatchTaskContext newBatchTaskContext(IServiceContext svcCtx) {
        return newBatchTaskContext(svcCtx, svcCtx == null ? null : svcCtx.getEvalScope());
    }

    default IBatchTask newBatchTask(String batchTaskName, Long batchTaskVersion, IBatchTaskContext taskContext) {
        return newBatchTaskBuilder(batchTaskName, batchTaskVersion, taskContext.getEvalScope().getBeanProvider())
                .buildTask(taskContext);
    }

    IBatchTaskBuilder newBatchTaskBuilder(String batchTaskName, Long batchTaskVersion, IBeanProvider beanProvider);

    IBatchTaskBuilder newBatchTaskBuilderFromModel(String batchTaskName, XNode node, IBeanProvider beanProvider);
}
