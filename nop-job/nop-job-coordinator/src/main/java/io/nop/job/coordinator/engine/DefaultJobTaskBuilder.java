package io.nop.job.coordinator.engine;

import io.nop.api.core.config.AppConfig;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DefaultJobTaskBuilder implements IJobTaskBuilder {
    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
        task.setWorkerInstanceId(AppConfig.hostId());
        task.getTaskPayloadComponent().set_jsonValue(Map.of(
                "jobFireId", fire.getJobFireId(),
                "jobParamsSnapshot", emptyIfNull(fire.getJobParamsSnapshotComponent().get_jsonMap())
        ));
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setCreatedBy("system");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("system");
        task.setUpdateTime(new Timestamp(now));

        return Collections.singletonList(task);
    }

    private Map<String, Object> emptyIfNull(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }
}
