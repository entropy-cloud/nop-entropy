package io.nop.job.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobTaskLog;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopJobTaskLog extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testTaskLogCrudAndQueryByTaskId() {
        var dao = daoProvider.daoFor(NopJobTaskLog.class);

        NopJobTaskLog log1 = dao.newEntity();
        log1.setJobTaskId("task-1");
        log1.setJobFireId("fire-1");
        log1.setJobScheduleId("schedule-1");
        log1.setJobName("job-a");
        log1.setGroupId("group-a");
        log1.setLogTime(new Timestamp(System.currentTimeMillis()));
        log1.setLogLevel(30); // INFO
        log1.setLogMessage("started");
        dao.saveEntity(log1);

        NopJobTaskLog log2 = dao.newEntity();
        log2.setJobTaskId("task-1");
        log2.setJobFireId("fire-1");
        log2.setLogTime(new Timestamp(System.currentTimeMillis() + 1000));
        log2.setLogLevel(50); // ERROR
        log2.setLogMessage("failed with error");
        dao.saveEntity(log2);

        NopJobTaskLog log3 = dao.newEntity();
        log3.setJobTaskId("task-2");
        log3.setLogTime(new Timestamp(System.currentTimeMillis()));
        log3.setLogLevel(30);
        log3.setLogMessage("other task");
        dao.saveEntity(log3);

        // 按 taskId 归组查询（ix_nop_job_task_log_task_time 主查询路径）
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("jobTaskId", "task-1"));
        var logs = dao.findPageByQuery(query);
        assertEquals(2, logs.size());
        for (NopJobTaskLog log : logs) {
            assertEquals("task-1", log.getJobTaskId());
            assertNotNull(log.getJobTaskLogId());
        }

        // 冗余展示列回读
        NopJobTaskLog loaded = dao.getEntityById(logs.get(0).getJobTaskLogId());
        assertEquals("fire-1", loaded.getJobFireId());
        assertEquals("job-a", loaded.getJobName());
        assertEquals("group-a", loaded.getGroupId());

        // 删除
        dao.deleteEntityDirectly(dao.requireEntityById(log1.getJobTaskLogId()));
        QueryBean query2 = new QueryBean();
        query2.addFilter(FilterBeans.eq("jobTaskId", "task-1"));
        assertEquals(1, dao.findPageByQuery(query2).size());

        // _NopJobCoreConstants 可访问（模块常量引用完整性）
        assertNotNull(_NopJobCoreConstants.TASK_STATUS_RUNNING);
    }
}
