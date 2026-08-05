
package io.nop.metadata.service.quality;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.dao.entity.NopMetaTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MetaQualityScorer 单元测试（P2-04：readExtConfigDimension catch 补日志）。
 *
 * <p>既有 MetaQualityScorer 无独立单测（仅 TestNopMetaQualityScoreBizModel 集成覆盖），本类从零搭建
 * mock DAO 脚手架（沿 TestMetadataPropagationUnit 模式），测试位置执行时裁定为 quality 包下独立文件。
 */
public class TestMetaQualityScorer {

    /**
     * P2-04 回归：rule.extConfig 为损坏 JSON 时 readExtConfigDimension 回退静态 ruleType 映射
     * （CUSTOM_SQL → DIM_CONSISTENCY，非 null、非伪造维度），且必须留 WARN 根因日志（含 ruleId）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testCorruptExtConfigFallsBackToRuleTypeMappingAndLogsWarn() {
        IDaoProvider daoProvider = mock(IDaoProvider.class);
        IEntityDao<NopMetaTable> tableDao = (IEntityDao<NopMetaTable>) mock(IEntityDao.class);
        IEntityDao<NopMetaQualityRule> ruleDao = (IEntityDao<NopMetaQualityRule>) mock(IEntityDao.class);
        IEntityDao<NopMetaQualityResult> resultDao = (IEntityDao<NopMetaQualityResult>) mock(IEntityDao.class);
        IEntityDao<NopMetaQualityScore> scoreDao = (IEntityDao<NopMetaQualityScore>) mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaTable.class)).thenReturn(tableDao);
        when(daoProvider.daoFor(NopMetaQualityRule.class)).thenReturn(ruleDao);
        when(daoProvider.daoFor(NopMetaQualityResult.class)).thenReturn(resultDao);
        when(daoProvider.daoFor(NopMetaQualityScore.class)).thenReturn(scoreDao);

        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("t-1");
        when(tableDao.getEntityById("t-1")).thenReturn(table);

        // CUSTOM_SQL：静态 ruleType 映射 → DIM_CONSISTENCY（readExtConfigDimension 返回 null 时的回退面）
        NopMetaQualityRule rule = new NopMetaQualityRule();
        rule.setQualityRuleId("r-custom-sql");
        rule.setRuleType("custom_sql");
        rule.setExtConfig("{{{corrupt");
        when(ruleDao.findAllByQuery(any(QueryBean.class))).thenReturn(List.of(rule));

        NopMetaQualityResult result = new NopMetaQualityResult();
        result.setQualityRuleId("r-custom-sql");
        result.setStatus("PASS");
        when(resultDao.findFirstByQuery(any(QueryBean.class))).thenReturn(result);

        when(scoreDao.findFirstByQuery(any(QueryBean.class))).thenReturn(null);

        MetaQualityScorer scorer = new MetaQualityScorer(daoProvider);

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MetaQualityScorer.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MetaQualityScorer.QualityScoreResult score = scorer.score("t-1");

            // 行为保持：损坏 extConfig → 静态 ruleType 映射（CUSTOM_SQL → consistency），维度分正常
            Map<String, Object> dims = score.getDimensionScores();
            assertEquals(100.0d, ((Number) dims.get(MetaQualityScorer.DIM_CONSISTENCY)).doubleValue(),
                    "corrupt extConfig must fall back to static ruleType mapping (CUSTOM_SQL → consistency, P2-04)");
            assertEquals(null, dims.get(MetaQualityScorer.DIM_COMPLETENESS),
                    "other dimensions must stay no-rules null (fallback must not fabricate scores)");

            boolean warnLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.WARN
                            && e.getFormattedMessage().contains("extConfig is not valid JSON")
                            && e.getFormattedMessage().contains("r-custom-sql"));
            assertTrue(warnLogged,
                    "corrupt rule extConfig must be logged with WARN including ruleId (P2-04), got: "
                            + appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.toList()));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
