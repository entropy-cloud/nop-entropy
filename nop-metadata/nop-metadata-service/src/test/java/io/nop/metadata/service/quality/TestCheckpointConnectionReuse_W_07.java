
package io.nop.metadata.service.quality;

import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.tableref.MetaEntityReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W-07 回归钉（plan 2528 红⑤，CRA 5.5 C7-003+C4-R2-002）：checkpoint 编排建连复用。
 *
 * <p>缺陷形态（pre-fix）：编排逐规则建连——同数据源 N 条规则每 tick 产生 N 次「建 DataSource
 * + TCP+TLS+认证+拆连」。修复后按 ref.getDataSource() 的 dataSourceId 等值分组，每组单次
 * withConnection（callback 内逐规则 judge，对齐批量路径模板）。
 *
 * <p>钉形：记录型 {@link IMetaDataSourceConnectionProcessor} 计数 withConnection 调用——
 * 同数据源 3 条规则的 checkpoint 执行断言调用数 == 分组数（1）。
 */
public class TestCheckpointConnectionReuse_W_07 {

    private static final String DS_ID = "ds-w07-shared";

    /** 记录型建连服务：计数 withConnection 调用，callback 直接收记录型 conn/metaData（不真实建连）。 */
    static class CountingConnectionService implements IMetaDataSourceConnectionProcessor {
        final List<String> connectionConfigs = new ArrayList<>();
        int withConnectionCalls;

        @Override
        public void withConnection(String datasourceType, String connectionConfig,
                                   BiConsumer<Connection, DatabaseMetaData> action) {
            withConnectionCalls++;
            connectionConfigs.add(connectionConfig);
            action.accept(recordingProxy(Connection.class), recordingProxy(DatabaseMetaData.class));
        }

        @Override
        public Map<String, Object> testConnect(String datasourceType, String connectionConfig) {
            throw new UnsupportedOperationException("not used by this pin");
        }

        @SuppressWarnings("unchecked")
        private static <T> T recordingProxy(Class<T> iface) {
            return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                    (p, method, args) -> {
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return Boolean.FALSE;
                        if (rt == int.class) return 0;
                        return null;
                    });
        }
    }

    @SuppressWarnings("unchecked")
    private CountingConnectionService buildExecutorWithRules(int ruleCount) {
        IDaoProvider daoProvider = mock(IDaoProvider.class);

        IEntityDao<NopMetaQualityRule> ruleDao = (IEntityDao<NopMetaQualityRule>) mock(IEntityDao.class);
        for (int i = 0; i < ruleCount; i++) {
            NopMetaQualityRule rule = new NopMetaQualityRule();
            rule.setQualityRuleId("rule-w07-" + i);
            rule.setRuleName("reuse-rule-" + i);
            rule.setRuleType("volume");
            rule.setEntityType(_NopMetadataCoreConstants.QUALITY_ENTITY_TYPE_ENTITY);
            rule.setEntityId("table-w07");
            when(ruleDao.getEntityById("rule-w07-" + i)).thenReturn(rule);
        }
        when(daoProvider.daoFor(NopMetaQualityRule.class)).thenReturn(ruleDao);

        IEntityDao<NopMetaEntity> tableDao = (IEntityDao<NopMetaEntity>) mock(IEntityDao.class);
        NopMetaEntity table = new NopMetaEntity();
        table.setMetaEntityId("table-w07");
        table.setTableName("W07_SHARED_TABLE");
        when(tableDao.getEntityById("table-w07")).thenReturn(table);
        when(daoProvider.daoFor(NopMetaEntity.class)).thenReturn(tableDao);

        CountingConnectionService connectionService = new CountingConnectionService();

        // 同数据源 EXTERNAL 形态 ref（分组键 = dataSourceId 等值）
        NopMetaDataSource ds = new NopMetaDataSource();
        ds.setDataSourceId(DS_ID);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"jdbc:mysql://example.invalid:3306/w07\"}");
        MetaEntityReferenceResolver tableRefResolver = mock(MetaEntityReferenceResolver.class);
        when(tableRefResolver.resolve(any(), any(), any(), any(), any())).thenReturn(
                new TableReference(TableReference.Kind.EXTERNAL, "table-w07", "W07_SHARED_TABLE",
                        null, ds, null, null, null));

        TableReferenceExecutor tableRefExecutor = new TableReferenceExecutor(connectionService,
                mock(IOrmTemplate.class));

        MetaQualityRuleExecutor ruleExecutor = mock(MetaQualityRuleExecutor.class);
        when(ruleExecutor.judge(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    QualityRuleJudgment j = new QualityRuleJudgment();
                    j.setStatus(_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_PASS);
                    return j;
                });

        NopMetaQualityCheckpoint cp = new NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-w07");
        cp.setStatus(_NopMetadataCoreConstants.CHECKPOINT_STATUS_ACTIVE);
        List<String> ruleIds = new ArrayList<>();
        for (int i = 0; i < ruleCount; i++) {
            ruleIds.add("\"rule-w07-" + i + "\"");
        }
        cp.setValidations("[{\"ruleIds\":[" + String.join(",", ruleIds) + "]}]");

        // W-07（裁定⑤）：记录型建连服务直达编排分组——同数据源规则组单次 withConnection
        MetaQualityCheckpointExecutor executor = new MetaQualityCheckpointExecutor(
                ruleExecutor, tableRefResolver, tableRefExecutor,
                mock(QualityResultWriter.class), daoProvider, mock(IOrmTemplate.class), connectionService);

        Map<String, Object> summary = executor.execute(cp, "run-w07", null);
        assertEquals(ruleCount, summary.get("executedCount"),
                "all rules must still be judged (reuse must not lose rules)");
        assertEquals(ruleCount, summary.get("passCount"),
                "all rules must keep their judgments under the grouped execution");
        return connectionService;
    }

    /** 同数据源 3 条规则 → withConnection 调用数收敛为分组数 1（pre-fix 为 3 → red）。 */
    @Test
    public void testSameDataSourceRulesShareSingleConnection() {
        CountingConnectionService service = buildExecutorWithRules(3);
        assertEquals(1, service.withConnectionCalls,
                "same-datasource rules must share a single withConnection (grouped reuse)");
    }

    /** 不同数据源各自建连：分组键为 dataSourceId 等值——不同 ds 不得合并。 */
    @Test
    public void testDifferentDataSourceIdGetsOwnConnection() {
        // 两组规则、两个 dataSourceId → 2 次 withConnection
        CountingConnectionService service = buildExecutorWithTwoDataSources(2, 2);
        assertEquals(2, service.withConnectionCalls,
                "distinct dataSourceIds must not share a connection (group key is id equality)");
    }

    @SuppressWarnings("unchecked")
    private CountingConnectionService buildExecutorWithTwoDataSources(int rulesA, int rulesB) {
        IDaoProvider daoProvider = mock(IDaoProvider.class);
        IEntityDao<NopMetaQualityRule> ruleDao = (IEntityDao<NopMetaQualityRule>) mock(IEntityDao.class);
        int total = rulesA + rulesB;
        for (int i = 0; i < total; i++) {
            NopMetaQualityRule rule = new NopMetaQualityRule();
            rule.setQualityRuleId("rule-w07-" + i);
            rule.setRuleName("reuse-rule-" + i);
            rule.setRuleType("volume");
            rule.setEntityType(_NopMetadataCoreConstants.QUALITY_ENTITY_TYPE_ENTITY);
            rule.setEntityId(i < rulesA ? "table-a" : "table-b");
            when(ruleDao.getEntityById("rule-w07-" + i)).thenReturn(rule);
        }
        when(daoProvider.daoFor(NopMetaQualityRule.class)).thenReturn(ruleDao);

        IEntityDao<NopMetaEntity> tableDao = (IEntityDao<NopMetaEntity>) mock(IEntityDao.class);
        NopMetaEntity tableA = new NopMetaEntity();
        tableA.setMetaEntityId("table-a");
        tableA.setTableName("W07_TABLE_A");
        NopMetaEntity tableB = new NopMetaEntity();
        tableB.setMetaEntityId("table-b");
        tableB.setTableName("W07_TABLE_B");
        when(tableDao.getEntityById("table-a")).thenReturn(tableA);
        when(tableDao.getEntityById("table-b")).thenReturn(tableB);
        when(daoProvider.daoFor(NopMetaEntity.class)).thenReturn(tableDao);

        CountingConnectionService connectionService = new CountingConnectionService();

        NopMetaDataSource dsA = new NopMetaDataSource();
        dsA.setDataSourceId("ds-a");
        dsA.setDatasourceType("jdbc");
        dsA.setConnectionConfig("{\"jdbcUrl\":\"jdbc:mysql://example.invalid:3306/a\"}");
        NopMetaDataSource dsB = new NopMetaDataSource();
        dsB.setDataSourceId("ds-b");
        dsB.setDatasourceType("jdbc");
        dsB.setConnectionConfig("{\"jdbcUrl\":\"jdbc:mysql://example.invalid:3306/b\"}");

        MetaEntityReferenceResolver tableRefResolver = mock(MetaEntityReferenceResolver.class);
        when(tableRefResolver.resolve(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            NopMetaEntity table = inv.getArgument(0);
            boolean isA = "table-a".equals(table.getMetaEntityId());
            return new TableReference(TableReference.Kind.EXTERNAL, table.getMetaEntityId(),
                    table.getTableName(), null, isA ? dsA : dsB, null, null, null);
        });

        TableReferenceExecutor tableRefExecutor = new TableReferenceExecutor(connectionService,
                mock(IOrmTemplate.class));

        MetaQualityRuleExecutor ruleExecutor = mock(MetaQualityRuleExecutor.class);
        when(ruleExecutor.judge(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    QualityRuleJudgment j = new QualityRuleJudgment();
                    j.setStatus(_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_PASS);
                    return j;
                });

        NopMetaQualityCheckpoint cp = new NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-w07-2ds");
        cp.setStatus(_NopMetadataCoreConstants.CHECKPOINT_STATUS_ACTIVE);
        List<String> ruleIds = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            ruleIds.add("\"rule-w07-" + i + "\"");
        }
        cp.setValidations("[{\"ruleIds\":[" + String.join(",", ruleIds) + "]}]");

        MetaQualityCheckpointExecutor executor = new MetaQualityCheckpointExecutor(
                ruleExecutor, tableRefResolver, tableRefExecutor,
                mock(QualityResultWriter.class), daoProvider, mock(IOrmTemplate.class), connectionService);

        Map<String, Object> summary = executor.execute(cp, "run-w07-2ds", null);
        assertEquals(total, summary.get("executedCount"),
                "rules across two datasources must all be judged");
        return connectionService;
    }
}
