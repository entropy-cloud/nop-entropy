package io.nop.batch.sys;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.manager.IBatchTaskManager;
import io.nop.sys.dao.message.SysDaoMessageService;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SysEventBatchTrigger {
    static final Logger LOG = LoggerFactory.getLogger(SysEventBatchTrigger.class);

    public static final String TASK_PATH = "/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml";

    private IBatchTaskManager batchTaskManager;

    private SysDaoMessageService messageService;

    private IntRangeSet assignedPartitions;

    @Inject
    public void setBatchTaskManager(IBatchTaskManager batchTaskManager) {
        this.batchTaskManager = batchTaskManager;
    }

    public void setMessageService(SysDaoMessageService messageService) {
        this.messageService = messageService;
    }

    public void setAssignedPartitions(IntRangeSet assignedPartitions) {
        this.assignedPartitions = assignedPartitions;
    }

    public void processNonBroadcastEvent() {
        SysDaoMessageService svc = messageService;
        if (svc == null) {
            svc = (SysDaoMessageService) BeanContainer.tryGetBean("nopSysDaoMessageService");
        }
        if (svc == null) {
            throw new IllegalStateException("nopSysDaoMessageService bean not found and messageService not set directly");
        }

        IBatchTask task = batchTaskManager.loadBatchTaskFromPath(TASK_PATH, BeanContainer.instance());

        IBatchTaskContext context = batchTaskManager.newBatchTaskContext();

        // partitionRange 使 OrmQueryBatchLoaderProvider 自动追加 partitionIndex BETWEEN 过滤
        if (assignedPartitions != null && !assignedPartitions.isEmpty()) {
            IntRangeBean range = IntRangeBean.build(
                    assignedPartitions.getFirstBegin(),
                    assignedPartitions.getLastEnd());
            context.setPartitionRange(range);
        } else {
            context.setPartitionRange(IntRangeBean.shortRange());
        }

        // 注入 messageService 到 eval scope，供 DSL processor/consumer 的 <source> XPL 使用
        context.getEvalScope().setLocalValue("messageService", svc);

        task.execute(context);
    }
}
