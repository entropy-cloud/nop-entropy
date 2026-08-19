package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.job.api.log.TaskLogEntry;
import io.nop.job.biz.INopJobTaskLogBiz;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.entity.NopJobTaskLog;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobTaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.job.api.JobApiErrors.ARG_TASK_LOG_ID;
import static io.nop.job.api.JobApiErrors.ERR_JOB_LOG_INVALID_ENTRY;

/**
 * plan 2254: 任务执行日志（可选行为）。{@link #reportTaskLog} 是 worker → coordinator 的
 * 批量上报端点（REST /r/ 入口），日志行以 jobTaskId 归组，冗余展示列（fire/schedule/jobName/
 * groupId）从 task 快照填充避免 join。
 */
@BizModel("NopJobTaskLog")
public class NopJobTaskLogBizModel extends CrudBizModel<NopJobTaskLog> implements INopJobTaskLogBiz {
    static final Logger LOG = LoggerFactory.getLogger(NopJobTaskLogBizModel.class);

    private static final Map<String, Integer> LOG_LEVEL_CODES = Map.of(
            "TRACE", 10,
            "DEBUG", 20,
            "INFO", 30,
            "WARN", 40,
            "ERROR", 50
    );

    public NopJobTaskLogBizModel() {
        setEntityName(NopJobTaskLog.class.getName());
    }

    /**
     * 批量接收日志行并落库。校验 jobTaskId/logTime/logLevel；jobFireId/jobScheduleId/jobName/
     * groupId 从 task 快照冗余填充。返回接收条数（校验失败的条目抛错——显式失败可观测）。
     * 与任务状态机完全解耦：本方法不修改任何 task/fire/schedule 状态。
     */
    @BizMutation
    public int reportTaskLog(@Name("entries") List<TaskLogEntry> entries, IServiceContext context) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        IJobTaskStore taskStore = BeanContainer.getBeanByType(IJobTaskStore.class);
        Set<String> loadedTaskIds = new HashSet<>();
        for (TaskLogEntry entry : entries) {
            validate(entry);
            if (!loadedTaskIds.contains(entry.getJobTaskId())) {
                loadedTaskIds.add(entry.getJobTaskId());
            }
            saveEntry(entry, taskStore);
        }
        return entries.size();
    }

    private void validate(TaskLogEntry entry) {
        if (entry.getJobTaskId() == null || entry.getJobTaskId().isBlank()) {
            throw new NopException(ERR_JOB_LOG_INVALID_ENTRY).param(ARG_TASK_LOG_ID, null);
        }
        if (entry.getLogTime() <= 0) {
            throw new NopException(ERR_JOB_LOG_INVALID_ENTRY).param(ARG_TASK_LOG_ID, entry.getJobTaskId());
        }
        if (entry.getLogLevel() == null || !LOG_LEVEL_CODES.containsKey(entry.getLogLevel().toUpperCase())) {
            throw new NopException(ERR_JOB_LOG_INVALID_ENTRY).param(ARG_TASK_LOG_ID, entry.getJobTaskId());
        }
    }

    private void saveEntry(TaskLogEntry entry, IJobTaskStore taskStore) {
        NopJobTaskLog entity = dao().newEntity();
        entity.setJobTaskId(entry.getJobTaskId());
        entity.setLogTime(new Timestamp(entry.getLogTime()));
        entity.setLogLevel(LOG_LEVEL_CODES.get(entry.getLogLevel().toUpperCase()));
        entity.setLogMessage(entry.getLogMessage());
        Map<String, Object> payload = entry.getLogPayload();
        if (payload != null) {
            entity.setLogPayload(io.nop.core.lang.json.JsonTool.stringify(payload));
        }
        fillRedundantColumns(entity, taskStore);
        dao().saveEntityDirectly(entity);
    }

    private void fillRedundantColumns(NopJobTaskLog entity, IJobTaskStore taskStore) {
        try {
            NopJobTask task = taskStore.loadTask(entity.getJobTaskId());
            if (task == null) {
                return;
            }
            entity.setJobFireId(task.getJobFireId());
            if (task.getJobFireId() != null) {
                NopJobFire fire = BeanContainer.getBeanByType(IJobFireStore.class).loadFire(task.getJobFireId());
                if (fire != null) {
                    entity.setJobScheduleId(fire.getJobScheduleId());
                    entity.setJobName(fire.getJobName());
                    entity.setGroupId(fire.getGroupId());
                }
            }
        } catch (Exception e) {
            // task 已被清理/不存在：仅填充 taskId，日志行仍落库（展示列留空）
            LOG.warn("nop.job.log.fill-task-snapshot-failed:taskId={}", entity.getJobTaskId(), e);
        }
    }
}
