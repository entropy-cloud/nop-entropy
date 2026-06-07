package io.nop.job.coordinator.engine;

import io.nop.api.core.config.AppConfig;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DefaultJobTaskBuilder implements IJobTaskBuilder {
    static final Logger LOG = LoggerFactory.getLogger(DefaultJobTaskBuilder.class);

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);

        // NOTE: workerInstanceId is set to the coordinator's hostId here.
        // The SUSPICIOUS detection in JobTimeoutCheckerImpl compares this value
        // against live instances in the naming service. This mechanism works
        // correctly only when coordinator and worker are co-deployed (same JVM).
        // In non-co-deployed scenarios, the timeout checker's worker liveness
        // check is not applicable for tasks created by DefaultJobTaskBuilder.
        task.setWorkerInstanceId(AppConfig.hostId());

        // targetHost is reserved for future use: populate when a naming service
        // is available to resolve workerInstanceId to a network address.
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
