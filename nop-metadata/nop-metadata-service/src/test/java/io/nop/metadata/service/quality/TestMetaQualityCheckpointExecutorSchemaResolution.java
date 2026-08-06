
package io.nop.metadata.service.quality;

import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AR-07（plan 2026-08-06-0553-3 Phase 2）：检查点路径 schema 解析与单规则路径语义一致——
 * schemaPattern 为 null/空时回退目标表 NopMetaTable.metaSchema（此前原样透传恒 null，
 * 同一条规则两个入口评估不同的物理表）。
 *
 * <p>接线验证（Exit Criteria）：MetaQualityCheckpointExecutor → tableRefExecutor.execute →
 * ruleExecutor.judge(schema) 调用链真实连通——经 execute() 公开入口断言 judge 收到的 schema 参数
 * （mock tableRefExecutor 直接调用 action，捕获 judge 实参），修复前 judge 收到 null → red。
 */
public class TestMetaQualityCheckpointExecutorSchemaResolution {

    private static final String RULE_ID = "rule-schema-1";
    private static final String TABLE_ID = "table-schema-1";

    @SuppressWarnings("unchecked")
    private MetaQualityCheckpointExecutor buildExecutor(MetaQualityRuleExecutor ruleExecutor) {
        IDaoProvider daoProvider = mock(IDaoProvider.class);

        IEntityDao<NopMetaQualityRule> ruleDao = (IEntityDao<NopMetaQualityRule>) mock(IEntityDao.class);
        NopMetaQualityRule rule = new NopMetaQualityRule();
        rule.setQualityRuleId(RULE_ID);
        rule.setRuleName("schema-rule");
        rule.setRuleType("volume");
        rule.setEntityType(_NopMetadataCoreConstants.QUALITY_ENTITY_TYPE_TABLE);
        rule.setEntityId(TABLE_ID);
        when(ruleDao.getEntityById(RULE_ID)).thenReturn(rule);
        when(daoProvider.daoFor(NopMetaQualityRule.class)).thenReturn(ruleDao);

        IEntityDao<NopMetaTable> tableDao = (IEntityDao<NopMetaTable>) mock(IEntityDao.class);
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId(TABLE_ID);
        table.setTableName("EXT_SCHEMA_TABLE");
        table.setMetaSchema("META_SCHEMA_X");
        table.setQuerySpace("qs-schema-test");
        when(tableDao.getEntityById(TABLE_ID)).thenReturn(table);
        when(daoProvider.daoFor(NopMetaTable.class)).thenReturn(tableDao);

        MetaTableReferenceResolver tableRefResolver = mock(MetaTableReferenceResolver.class);
        TableReferenceExecutor tableRefExecutor = mock(TableReferenceExecutor.class);
        // 真实调用 action（模拟 TableReferenceExecutor 的分派），使 judge 实参可捕获
        when(tableRefExecutor.execute(any(), any())).thenAnswer(invocation -> {
            TableReferenceExecutor.ConnectionAction<?> action = invocation.getArgument(1);
            return action.apply(mock(Connection.class), mock(DatabaseMetaData.class), "H2");
        });

        QualityResultWriter resultWriter = mock(QualityResultWriter.class);
        IOrmTemplate orm = mock(IOrmTemplate.class);
        return new MetaQualityCheckpointExecutor(ruleExecutor, tableRefResolver, tableRefExecutor,
                resultWriter, daoProvider, orm);
    }

    private NopMetaQualityCheckpoint buildCheckpoint() {
        NopMetaQualityCheckpoint cp = new NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-schema-1");
        cp.setStatus(_NopMetadataCoreConstants.CHECKPOINT_STATUS_ACTIVE);
        cp.setValidations("[{\"ruleIds\":[\"" + RULE_ID + "\"]}]");
        return cp;
    }

    private static QualityRuleJudgment passJudgment() {
        QualityRuleJudgment j = new QualityRuleJudgment();
        j.setStatus(_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_PASS);
        j.setMessage("ok");
        return j;
    }

    /** AR-07 判别性主用例：schemaPattern=null → judge 收到 table.metaSchema 回退值（修复前收到 null → red）。 */
    @Test
    public void testSchemaPatternNullFallsBackToTableMetaSchema() {
        MetaQualityRuleExecutor ruleExecutor = mock(MetaQualityRuleExecutor.class);
        when(ruleExecutor.judge(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(passJudgment());
        MetaQualityCheckpointExecutor executor = buildExecutor(ruleExecutor);

        Map<String, Object> summary = executor.execute(buildCheckpoint(), "run-1", null);
        assertNotNull(summary, "summary must not be null");
        assertEquals(1, summary.get("executedCount"), "one rule must be executed");
        assertEquals(0, summary.get("errorCount"), "no execution errors expected");

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(ruleExecutor).judge(any(), any(), schemaCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertEquals("META_SCHEMA_X", schemaCaptor.getValue(),
                "schemaPattern=null must fall back to table.metaSchema (same semantics as single-rule path)");
    }

    /** 显式 schemaPattern 优先于 metaSchema（与单规则路径 resolveDefaultSchema 语义一致）。 */
    @Test
    public void testExplicitSchemaPatternWinsOverMetaSchema() {
        MetaQualityRuleExecutor ruleExecutor = mock(MetaQualityRuleExecutor.class);
        when(ruleExecutor.judge(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(passJudgment());
        MetaQualityCheckpointExecutor executor = buildExecutor(ruleExecutor);

        Map<String, Object> summary = executor.execute(buildCheckpoint(), "run-2", "PUBLIC");
        assertNotNull(summary, "summary must not be null");

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(ruleExecutor).judge(any(), any(), schemaCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertEquals("PUBLIC", schemaCaptor.getValue(),
                "explicit schemaPattern must take precedence over table.metaSchema");
    }

    /** 空串 schemaPattern 也走 metaSchema 回退（resolveDefaultSchema 的 trim 语义）。 */
    @Test
    public void testBlankSchemaPatternFallsBackToTableMetaSchema() {
        MetaQualityRuleExecutor ruleExecutor = mock(MetaQualityRuleExecutor.class);
        when(ruleExecutor.judge(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(passJudgment());
        MetaQualityCheckpointExecutor executor = buildExecutor(ruleExecutor);

        executor.execute(buildCheckpoint(), "run-3", "   ");

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(ruleExecutor).judge(any(), any(), schemaCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertEquals("META_SCHEMA_X", schemaCaptor.getValue(),
                "blank schemaPattern must fall back to table.metaSchema");
    }
}
