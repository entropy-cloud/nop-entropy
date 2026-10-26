package io.nop.metadata.service.method;

import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.api.dto.MethodInvocationResultDTO;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntityMethod;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaEntityFieldResolver;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.tableref.MetaEntityReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.orm.IOrmTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 方法执行器（设计 14 §3.4，plan 2026-10-26-2047-1 Phase 3）。
 *
 * <p>加载校验与 roles 检查在 BizModel 入口完成；本类负责 implKind 分派（首版仅 sql）
 * 与 entityKind 取数分派：PHYSICAL → 平台库连接（TableReference ENTITY 路径，平台事务
 * JDBC Connection）；SQL_VIEW/EXTERNAL → 实体绑定数据源 withConnection（联邦查询取数
 * 先例，方法执行仅单表，不做 JOIN）。方法 SQL 经 {@link MetaMethodSqlValidator}
 * query-time 重验后以 {@code ?} 占位符绑定执行。
 *
 * <p><b>错误面不泄漏 SQL 全文</b>（设计 §3.5）：执行失败 param 只携带
 * methodId/methodName 与错误类别，不回显 SQL 与参数值（沿事件快照脱敏与 sqlHash 先例）。
 *
 * <p>结果行数由方法作者经 SQL（LIMIT）自行控制，执行器不再截断（沿 custom_sql
 * 无行数上限的同一契约：SQL 是作者声明的只读查询）。
 */
public class MetaMethodExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MetaMethodExecutor.class);

    private final IMetaDataSourceConnectionProcessor connectionService;
    private final TableReferenceExecutor tableRefExecutor;

    public MetaMethodExecutor(IMetaDataSourceConnectionProcessor connectionService,
                              TableReferenceExecutor tableRefExecutor) {
        this.connectionService = connectionService;
        this.tableRefExecutor = tableRefExecutor;
    }

    /**
     * 执行方法 SQL（implKind=sql）。调用序前置条件：method 已加载且未 disabled、args
     * 已按声明校验补齐默认值、roles 已检查、SQL 已过 query-time 沙箱。
     *
     * @param argValues 按声明参数顺序排列的绑定值（数量必须等于声明参数数）
     */
    public MethodInvocationResultDTO invoke(NopMetaEntity entity, NopMetaEntityMethod method,
                                            List<String> declaredArgNames, List<Object> argValues,
                                            IDaoProvider daoProvider, IOrmTemplate orm) {
        String implSource = method.getImplSource();
        MetaMethodSqlValidator.validateQueryTime(implSource, method.getMethodName());
        if (MetaMethodSqlValidator.countPlaceholders(implSource) != argValues.size()) {
            // 模型不一致（实现体占位符数 ≠ 声明参数数）：按 save-time 沙箱语义拒绝
            throw new NopMetadataException(NopMetadataErrors.ERR_METHOD_IMPL_INVALID)
                    .param(NopMetadataArgs.ARG_METHOD_NAME, method.getMethodName())
                    .param(NopMetadataArgs.ARG_CATEGORY, "placeholder-count-mismatch");
        }

        TableReference ref = new MetaEntityReferenceResolver(new MetaDataSourceResolver(),
                new MetaEntityFieldResolver())
                .resolve(entity, daoProvider.daoFor(NopMetaDataSource.class),
                        daoProvider.daoFor(NopMetaEntity.class),
                        daoProvider.daoFor(NopMetaEntityField.class), orm);

        List<Map<String, Object>> rows = tableRefExecutor.execute(ref,
                (Connection conn, DatabaseMetaData metaData, String productName) ->
                        executeSelect(conn, implSource, argValues, method));

        MethodInvocationResultDTO result = new MethodInvocationResultDTO();
        result.setMethodId(method.getMethodId());
        result.setMethodName(method.getMethodName());
        if (!rows.isEmpty()) {
            result.setColumns(new ArrayList<>(rows.get(0).keySet()));
        }
        result.setItems(rows);
        result.setRowCount(rows.size());
        return result;
    }

    /** 单条只读 SELECT 执行：位置绑定 + 行 Map 化；失败只暴露 methodId/methodName/类别。 */
    private List<Map<String, Object>> executeSelect(Connection conn, String sql, List<Object> argValues,
                                                    NopMetaEntityMethod method) {
        // 日志面只记 sqlHash，不落 SQL 全文（方法 SQL 错误面与日志面同口径脱敏，AR-16 同源）
        LOG.info("MetaMethodExecutor SQL executed: methodId={}, sqlHash={}",
                method.getMethodId(), MetaQualityRuleExecutor.sqlHashOf(sql));
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object value : argValues) {
                st.setObject(idx++, value);
            }
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= columnCount; c++) {
                        String label = meta.getColumnLabel(c);
                        if (label == null || label.isEmpty()) {
                            label = meta.getColumnName(c);
                        }
                        row.put(label, rs.getObject(c));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            // 错误面不泄漏 SQL 全文与驱动细节（驱动消息可能回显语句片段，只保留类别）
            throw new NopMetadataException(NopMetadataErrors.ERR_METHOD_EXEC_FAILED, e)
                    .param(NopMetadataArgs.ARG_METHOD_ID, method.getMethodId())
                    .param(NopMetadataArgs.ARG_METHOD_NAME, method.getMethodName())
                    .param(NopMetadataArgs.ARG_CATEGORY, "sql-exec-failed");
        }
    }
}
