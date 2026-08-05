
package io.nop.metadata.service.quality;

import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.job.api.IJobScheduler;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MetaQualityCheckpointScheduler.readRegisteredCron 失败路径单元测试（P2-04）。
 *
 * <p>readRegisteredCron 为 private 方法，可观测路径 = registerCheckpoint → doRegister catch →
 * readRegisteredCron（mock addJob 抛异常后，诊断读取 getJobDetail 也抛异常）。既有
 * TestMetaQualityCheckpointScheduler 经 IoC 注入真实 LocalJobScheduler 无 mock 先例——本类为
 * 手工构造 + setter 注入（setDaoProvider/setScheduler）的 mock 测试，测试位置执行时裁定为
 * quality 包下独立文件。
 */
public class TestMetaQualityCheckpointSchedulerCronReadFailure {

    /**
     * P2-04 回归：scheduler.getJobDetail 抛异常（调度器故障，非"损坏配置"）→ readRegisteredCron
     * 返回 null 且不向外抛（诊断语义保持），但必须留 WARN 根因日志（含 checkpointId）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testSchedulerGetJobDetailFailureLoggedNotSilent() {
        IDaoProvider daoProvider = mock(IDaoProvider.class);
        IEntityDao<NopMetaQualityCheckpoint> cpDao = (IEntityDao<NopMetaQualityCheckpoint>) mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaQualityCheckpoint.class)).thenReturn(cpDao);

        NopMetaQualityCheckpoint cp = new NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-cron-read");
        cp.setStatus("ACTIVE");
        cp.setExtConfig("{\"schedule\":\"0 0 * * * ?\"}");
        when(cpDao.getEntityById("cp-cron-read")).thenReturn(cp);

        IJobScheduler scheduler = mock(IJobScheduler.class);
        // addJob 失败（MA7.5-03 路径）→ doRegister catch 调 readRegisteredCron 做诊断
        doThrow(new RuntimeException("scheduler down")).when(scheduler).addJob(any(), anyBoolean());
        // 诊断读取自身也失败（调度器故障）→ readRegisteredCron catch → WARN + null
        doThrow(new RuntimeException("scheduler down")).when(scheduler).getJobDetail(anyString());

        MetaQualityCheckpointScheduler service = new MetaQualityCheckpointScheduler();
        service.setDaoProvider(daoProvider);
        service.setScheduler(scheduler);

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MetaQualityCheckpointScheduler.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // 行为保持：getJobDetail 失败不向外抛（registerCheckpoint 吞异常 + doRegister 返回 false）
            assertDoesNotThrow(() -> service.registerCheckpoint("cp-cron-read"),
                    "scheduler query failure must not propagate (diagnostic path, P2-04)");

            boolean warnLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.WARN
                            && e.getFormattedMessage().contains("read-registered-cron-failed")
                            && e.getFormattedMessage().contains("cp-cron-read"));
            assertTrue(warnLogged,
                    "getJobDetail failure must be logged with WARN including checkpointId (P2-04), got: "
                            + appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.toList()));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
