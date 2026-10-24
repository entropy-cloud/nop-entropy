package io.nop.metadata.service.quality;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.metadata.api.dto.CheckpointExecutionResultDTO;
import io.nop.metadata.service.entity.NopMetaQualityCheckpointBizModel;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W-11 regression (plan 2496 Phase 4): an Error escaping
 * {@code executeScheduledCheckpoint} must NOT turn the cron job permanently FAILED.
 * Pre-fix the entry caught only {@code Exception} — an Error (OOM/LinkageError from the
 * checkpoint chain) propagated to the invoker, became {@code JobFireResult.ERROR} and
 * LocalJobScheduler put the job in FAILED with no revival path (the MA7.5-01 self-healing
 * contract only covered the Exception half). Post-fix the Throwable-wide guard treats the
 * Error like any checkpoint-level failure: FATAL-level observation + normal error-result
 * DTO return, so the job stays alive and the next cron tick fires.
 */
public class TestMetaQualityCheckpointSchedulerErrorKeepAlive {

    private NopMetaQualityCheckpointBizModel errorThrowingBizModel() {
        NopMetaQualityCheckpointBizModel biz = mock(NopMetaQualityCheckpointBizModel.class);
        when(biz.executeCheckpoint(any(), any(), any()))
                .thenThrow(new LinkageError("injected checkpoint Error"));
        return biz;
    }

    @Test
    public void testErrorInjectionKeepsJobAliveAndObservable() {
        MetaQualityCheckpointScheduler service = new MetaQualityCheckpointScheduler();
        service.setCheckpointBizModel(errorThrowingBizModel());

        Logger logger = (Logger) LoggerFactory.getLogger(MetaQualityCheckpointScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Map<String, Object> params = Collections.singletonMap("checkpointId", "cp-w11");

            // job 存活断言（第一轮 cron tick）：the entry returns a normal result DTO
            // instead of propagating the Error to the invoker — no JobFireResult.ERROR,
            // so LocalJobScheduler never puts the job into the unrevivable FAILED state.
            CheckpointExecutionResultDTO first = assertDoesNotThrow(
                    () -> service.executeScheduledCheckpoint(params),
                    "an Error must not escape the scheduler entry (W-11 job-keep-alive half)");
            assertNotNull(first, "the Error round must still return the error result DTO");
            assertFalse(first.getErrors().isEmpty(),
                    "the Error round must carry the typed scheduler error entry");

            // 下一轮 cron 仍触发断言：the entry is re-invocable — a second tick also
            // returns normally (pre-fix the job would already be dead FAILED here).
            CheckpointExecutionResultDTO second = assertDoesNotThrow(
                    () -> service.executeScheduledCheckpoint(params),
                    "the next cron tick must still fire (job kept alive)");
            assertNotNull(second);

            // FATAL 级观测断言：the Error branch must be visible at ERROR level with the
            // dedicated fatal key (upgrade over the silent pre-fix escape).
            boolean fatalLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.ERROR
                            && e.getFormattedMessage().contains("scheduled-exec-fatal")
                            && e.getFormattedMessage().contains("cp-w11")
                            && e.getFormattedMessage().contains("LinkageError"));
            assertTrue(fatalLogged,
                    "the Error branch must be observed at FATAL (ERROR) level with the "
                            + "error class and checkpointId, got: "
                            + appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                            .reduce((a, b) -> a + "\n" + b).orElse(""));

            // 并发拒绝 WARN 分支不受捕获宽度放宽影响（回归保护）。
            NopMetaQualityCheckpointBizModel rejBiz = mock(NopMetaQualityCheckpointBizModel.class);
            when(rejBiz.executeCheckpoint(any(), any(), any()))
                    .thenThrow(new io.nop.metadata.service.NopMetadataException(
                            io.nop.metadata.service.NopMetadataErrors.ERR_CHECKPOINT_ALREADY_RUNNING));
            service.setCheckpointBizModel(rejBiz);
            assertDoesNotThrow(() -> service.executeScheduledCheckpoint(params));
            boolean warnLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("scheduled-exec-skipped"));
            assertTrue(warnLogged, "the concurrent-run rejection must stay on the WARN branch");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * 同根因随单闭合的另一半：{@code MetaQualityCheckpointExecutor.execute} 的 per-rule
     * 失败隔离覆盖 Throwable——单条规则的 Error 只隔离该规则，其余规则继续评定。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testPerRuleErrorIsolationKeepsRemainingRulesEvaluated() {
        io.nop.dao.api.IDaoProvider daoProvider = mock(io.nop.dao.api.IDaoProvider.class);

        io.nop.dao.api.IEntityDao<io.nop.metadata.dao.entity.NopMetaQualityRule> ruleDao =
                (io.nop.dao.api.IEntityDao<io.nop.metadata.dao.entity.NopMetaQualityRule>)
                        mock(io.nop.dao.api.IEntityDao.class);
        io.nop.metadata.dao.entity.NopMetaQualityRule rule1 = new io.nop.metadata.dao.entity.NopMetaQualityRule();
        rule1.setQualityRuleId("rule-w11-a");
        rule1.setRuleName("boom-rule");
        rule1.setRuleType("volume");
        rule1.setEntityType(io.nop.metadata.core._NopMetadataCoreConstants.QUALITY_ENTITY_TYPE_ENTITY);
        rule1.setEntityId("table-w11");
        io.nop.metadata.dao.entity.NopMetaQualityRule rule2 = new io.nop.metadata.dao.entity.NopMetaQualityRule();
        rule2.setQualityRuleId("rule-w11-b");
        rule2.setRuleName("ok-rule");
        rule2.setRuleType("volume");
        rule2.setEntityType(io.nop.metadata.core._NopMetadataCoreConstants.QUALITY_ENTITY_TYPE_ENTITY);
        rule2.setEntityId("table-w11");
        when(ruleDao.getEntityById("rule-w11-a")).thenReturn(rule1);
        when(ruleDao.getEntityById("rule-w11-b")).thenReturn(rule2);
        when(daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaQualityRule.class)).thenReturn(ruleDao);

        io.nop.dao.api.IEntityDao<io.nop.metadata.dao.entity.NopMetaEntity> tableDao =
                (io.nop.dao.api.IEntityDao<io.nop.metadata.dao.entity.NopMetaEntity>)
                        mock(io.nop.dao.api.IEntityDao.class);
        io.nop.metadata.dao.entity.NopMetaEntity table = new io.nop.metadata.dao.entity.NopMetaEntity();
        table.setMetaEntityId("table-w11");
        table.setTableName("W11_TABLE");
        when(tableDao.getEntityById("table-w11")).thenReturn(table);
        when(daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaEntity.class)).thenReturn(tableDao);

        io.nop.metadata.service.tableref.MetaEntityReferenceResolver tableRefResolver =
                mock(io.nop.metadata.service.tableref.MetaEntityReferenceResolver.class);
        io.nop.metadata.service.tableref.TableReferenceExecutor tableRefExecutor =
                mock(io.nop.metadata.service.tableref.TableReferenceExecutor.class);
        when(tableRefExecutor.execute(any(), any())).thenAnswer(invocation -> {
            io.nop.metadata.service.tableref.TableReferenceExecutor.ConnectionAction<?> action =
                    invocation.getArgument(1);
            return action.apply(mock(java.sql.Connection.class),
                    mock(java.sql.DatabaseMetaData.class), "H2");
        });

        io.nop.metadata.service.quality.QualityResultWriter resultWriter =
                mock(io.nop.metadata.service.quality.QualityResultWriter.class);
        io.nop.orm.IOrmTemplate orm = mock(io.nop.orm.IOrmTemplate.class);

        MetaQualityRuleExecutor ruleExecutor = mock(MetaQualityRuleExecutor.class);
        when(ruleExecutor.judge(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new LinkageError("injected rule Error"))
                .thenReturn(passJudgment());

        MetaQualityCheckpointExecutor executor = new MetaQualityCheckpointExecutor(
                ruleExecutor, tableRefResolver, tableRefExecutor, resultWriter, daoProvider, orm);

        io.nop.metadata.dao.entity.NopMetaQualityCheckpoint cp =
                new io.nop.metadata.dao.entity.NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-w11-iso");
        cp.setStatus(io.nop.metadata.core._NopMetadataCoreConstants.CHECKPOINT_STATUS_ACTIVE);
        cp.setValidations("[{\"ruleIds\":[\"rule-w11-a\",\"rule-w11-b\"]}]");

        Map<String, Object> summary = assertDoesNotThrow(() -> executor.execute(cp, "run-w11", null),
                "a rule-level Error must be isolated, not escape the checkpoint run");
        assertEquals(2, summary.get("executedCount"),
                "both rules must be evaluated (the Error only isolates its own rule)");
        assertEquals(1, summary.get("errorCount"), "the failing rule must count as error");
        assertEquals(1, summary.get("passCount"), "the remaining rule must still pass");
    }

    private static QualityRuleJudgment passJudgment() {
        QualityRuleJudgment j = new QualityRuleJudgment();
        j.setStatus(io.nop.metadata.core._NopMetadataCoreConstants.QUALITY_RESULT_STATUS_PASS);
        j.setMessage("ok");
        return j;
    }
}
