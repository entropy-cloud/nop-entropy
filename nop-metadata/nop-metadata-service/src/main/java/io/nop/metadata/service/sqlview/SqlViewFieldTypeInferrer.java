package io.nop.metadata.service.sqlview;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * SQL 视图字段类型推断器（架构基线 §4.2.1 方案 B，plan 0900-1）：经 {@code LIMIT 0} + {@link ResultSetMetaData#getColumnTypeName}
 * 推断 tableType=sql 逻辑表的 sourceSql 输出列字段类型，补全 {@link SqlViewField#getType()}（方案 A 下恒为 null）。
 *
 * <p><b>独立组件，不破坏既有契约（plan 0900-1 R1 M3 修复）</b>：
 * <ul>
 *   <li>不修改 {@link SqlSelectFieldExtractor}（保持其"无状态、无 DB 连接"契约）。</li>
 *   <li>不修改 {@code MetaTableFieldResolver}（plan 0900-1 R2 N1 修复——10+ 调用方仅消费 name，不应触发 DB 连接）。
 *       类型推断在 BizModel 层（{@code NopMetaTableBizModel.resolveTableFields} / {@code createSqlTable}）
 *       作为 resolve 之后的独立补全步骤。</li>
 * </ul>
 *
 * <p><b>触发时机（plan D1）</b>：{@code querySpace} 为 null/空 → 不推断（调用方走方案 A 基线，type=null，非降级）。
 * {@code querySpace} 提供 → 显式推断请求；连接不可达 / 数据源 DISABLED / 方言不支持 / sourceSql 执行失败
 * → <b>显式抛 NopException</b>（不静默 fallback type=null、不吞异常）。
 *
 * <p><b>类型表示（plan D2）</b>：取 {@link ResultSetMetaData#getColumnTypeName(int)} 返回的<b>方言原生类型名</b>
 * （如 {@code INTEGER}/{@code VARCHAR}/{@code BIGINT}）。不归一化、不保留 length/precision。
 * NULL 类型列（如 {@code SELECT NULL AS c}，{@code getColumnTypeName} 返回 null）→ type=null
 * （列类型确属未知，非伪造）。与 external 表 buildSql JSON 的 {@code dataType} 字段语义对齐。
 *
 * <p><b>列对齐策略（plan D3）</b>：按 projections 列表序号（即 {@code SqlViewField} 在输入 List 中的 index）
 * 与 {@link ResultSetMetaData} 列序号（1-based）一一对应。<b>不按名匹配</b>（避免 driver 返回的 columnLabel
 * 与解析的 name 在别名/表达式列上歧义）。表达式列 {@code <expr_N>} 的 type 取对应序号的 ResultSetMetaData 列类型
 * （driver 自动生成的类型）。
 *
 * <p><b>标识符安全（架构基线 §4.2.1 行 865）</b>：sourceSql 是用户显式提供的视图定义（非自动注入面）。
 * 走 PreparedStatement 包装子查询（{@code SELECT * FROM (<sourceSql>) _t LIMIT 0}），sourceSql 作为
 * PreparedStatement 文本（非拼接值），不拼接标识符。
 *
 * <p><b>失败路径全部显式抛（plan R1 B5 修复，不吞、不静默 fallback）</b>：
 * <ul>
 *   <li>querySpace null/空/无匹配 → 沿用 {@link MetaDataSourceResolver#resolveActiveOrThrow} 的 ErrorCode</li>
 *   <li>DISABLED 数据源 → 沿用 {@link MetaDataSourceResolver#resolveActiveOrThrow} 的 ErrorCode</li>
 *   <li>建连失败 → 沿用 {@code withConnection} 的 {@code metadata.datasource-connect-failed}</li>
 *   <li>非 jdbc 类型 → 由 {@code withConnection} 抛 {@code NopException}</li>
 *   <li>方言不支持 → {@link #ERR_SQL_TYPE_INFERENCE_DIALECT_NOT_SUPPORTED}</li>
 *   <li>列数不匹配（sourceSql 输出列数 != ResultSetMetaData 列数）→ {@link #ERR_SQL_TYPE_INFERENCE_COLUMN_MISMATCH}</li>
 *   <li>sourceSql 执行失败（语法错误等）→ {@link #ERR_SQL_TYPE_INFERENCE_FAILED}</li>
 *   <li>取类型元数据失败（getColumnTypeName 抛 SQLException）→ {@link #ERR_SQL_TYPE_INFERENCE_FAILED}</li>
 * </ul>
 *
 * <p>本组件无状态（依赖外部传入 DAO/resolver/connectionService），可在多 BizModel 间共享实例。
 */
public class SqlViewFieldTypeInferrer {

    /** inline ErrorCode：方言不支持（首版仅 H2/MySQL/PostgreSQL）。 */
    static final ErrorCode ERR_SQL_TYPE_INFERENCE_DIALECT_NOT_SUPPORTED =
            ErrorCode.define("metadata.sql-type-inference-dialect-not-supported",
                    "Dialect not supported for sql view type inference (only H2/MySQL/PostgreSQL): "
                            + "{databaseProductName} querySpace={querySpace}",
                    "databaseProductName", "querySpace");
    /** inline ErrorCode：列数不匹配（extractor 输出列数 != ResultSetMetaData 列数）。 */
    static final ErrorCode ERR_SQL_TYPE_INFERENCE_COLUMN_MISMATCH =
            ErrorCode.define("metadata.sql-type-inference-column-mismatch",
                    "Sql view column count mismatch: extractor={extractedCount} resultSet={resultSetCount} "
                            + "(projection order must align with ResultSetMetaData by 1-based index)",
                    "extractedCount", "resultSetCount", "querySpace");
    /** inline ErrorCode：sourceSql 执行失败 或 取类型元数据失败。 */
    static final ErrorCode ERR_SQL_TYPE_INFERENCE_FAILED =
            ErrorCode.define("metadata.sql-type-inference-failed",
                    "Sql view type inference failed (LIMIT 0 execution or ResultSetMetaData read failed): "
                            + "{error} querySpace={querySpace}",
                    "error", "querySpace");

    /** 类型推断首版支持的方言集合（与 §4.4 LIMIT/OFFSET 便携语法方言集一致）。 */
    private static final Set<String> SUPPORTED_DIALECTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("H2", "MySQL", "PostgreSQL")));

    private final MetaDataSourceResolver dataSourceResolver;
    private final IMetaDataSourceConnectionProcessor connectionService;

    public SqlViewFieldTypeInferrer(MetaDataSourceResolver dataSourceResolver,
                                     IMetaDataSourceConnectionProcessor connectionService) {
        this.dataSourceResolver = dataSourceResolver;
        this.connectionService = connectionService;
    }

    /**
     * 推断 sql 视图字段类型（plan 0900-1 §D1/D2/D3）。
     *
     * <p>当 {@code querySpace} 为 null/空 → <b>不推断</b>，原样返回输入 fields（方案 A 基线，type=null，非降级——
     * 调用方未显式请求类型推断）。当 {@code querySpace} 提供 → 经 withConnection 跑 {@code SELECT * FROM (<sourceSql>) _t LIMIT 0}
     * + ResultSetMetaData 取列类型，按 D3 列序对齐补全到新 SqlViewField 列表返回。
     *
     * @param fields      方案 A 已解析的字段列表（name/alias 已就绪，type 恒 null）
     * @param sourceSql   视图定义 SQL（tableType=sql 的 NopMetaTable.sourceSql）
     * @param querySpace  查询空间；null/空 → 不推断（原样返回 fields）
     * @param dsDao       数据源 DAO（由调用方通过 {@code daoFor(NopMetaDataSource.class)} 获取）
     * @return type 已补全的 SqlViewField 列表（querySpace 空 → 原样 fields）；永不 null
     * @throws NopException querySpace 提供 时：数据源不可达 / DISABLED / 非 jdbc / 建连失败 /
     *                      方言不支持 / sourceSql 执行失败 / 列数不匹配（全部显式抛，不静默 fallback）
     */
    public List<SqlViewField> inferTypes(List<SqlViewField> fields, String sourceSql, String querySpace,
                                          IEntityDao<NopMetaDataSource> dsDao) {
        // D1：querySpace 为 null/空 → 不推断（方案 A 基线，type=null，非降级——调用方未请求类型推断）
        if (querySpace == null || querySpace.trim().isEmpty()) {
            return fields;
        }
        // querySpace 提供 → 显式推断请求；resolveActiveOrThrow 内部对 null/空/无匹配/DISABLED 显式失败
        NopMetaDataSource dataSource = dataSourceResolver.resolveActiveOrThrow(dsDao, querySpace);

        // 列数前置校验（用于失败时的清晰错误）
        final int expectedCount = fields.size();

        // R2 N3：withConnection 返回 void，用 holder-array 侧效收集结果（参考 NopMetaTableBizModel:615/638）
        @SuppressWarnings("unchecked")
        final List<SqlViewField>[] holder = (List<SqlViewField>[]) new List<?>[1];
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                new BiConsumer<Connection, DatabaseMetaData>() {
                    @Override
                    public void accept(Connection conn, DatabaseMetaData metaData) {
                        holder[0] = inferWithinConnection(conn, metaData, fields, sourceSql, querySpace, expectedCount);
                    }
                });
        return holder[0];
    }

    /**
     * 在已打开的 Connection 内执行 LIMIT 0 + ResultSetMetaData 取类型。
     *
     * <p>方言守卫（D1）+ 列对齐（D3）+ 失败显式抛（R1 B5）。
     */
    private List<SqlViewField> inferWithinConnection(Connection conn, DatabaseMetaData metaData,
                                                       List<SqlViewField> fields, String sourceSql,
                                                       String querySpace, int expectedCount) {
        String productName = safeProductName(metaData);
        if (productName == null || !SUPPORTED_DIALECTS.contains(productName)) {
            // 方言不支持 → 显式失败（不静默 fallback type=null）
            throw new NopException(ERR_SQL_TYPE_INFERENCE_DIALECT_NOT_SUPPORTED)
                    .param("databaseProductName", String.valueOf(productName))
                    .param("querySpace", querySpace);
        }

        // SELECT * FROM (<sourceSql>) _t LIMIT 0：sourceSql 作为 PreparedStatement 文本（非拼接值），不拼接标识符
        String wrappedSql = "SELECT * FROM (" + sourceSql + ") _t LIMIT 0";
        try (PreparedStatement st = conn.prepareStatement(wrappedSql)) {
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData rsMeta = rs.getMetaData();
                int columnCount = rsMeta.getColumnCount();
                if (columnCount != expectedCount) {
                    // 列数不匹配（D3 列序对齐失败）→ 显式失败（不静默截断/不静默补 null）
                    throw new NopException(ERR_SQL_TYPE_INFERENCE_COLUMN_MISMATCH)
                            .param("extractedCount", expectedCount)
                            .param("resultSetCount", columnCount)
                            .param("querySpace", querySpace);
                }
                // D3：按 projections 列表序号（0-based）与 ResultSetMetaData 列序号（1-based i+1）一一对应
                List<SqlViewField> inferred = new ArrayList<>(expectedCount);
                for (int i = 0; i < expectedCount; i++) {
                    SqlViewField f = fields.get(i);
                    String type = readColumnType(rsMeta, i + 1, querySpace);
                    inferred.add(new SqlViewField(f.getName(), f.getAlias(), type));
                }
                return inferred;
            }
        } catch (SQLException e) {
            // sourceSql 执行失败 / prepareStatement 失败 → 显式失败（不吞、不静默 fallback）
            throw new NopException(ERR_SQL_TYPE_INFERENCE_FAILED)
                    .param("error", messageOf(e))
                    .param("querySpace", querySpace)
                    .cause(e);
        }
    }

    /**
     * 读 ResultSetMetaData.getColumnTypeName(i)；SQLException 显式包装失败（不吞、不静默返回 null）。
     *
     * <p>D2：driver 原生返回 null（如 SELECT NULL AS c）→ type=null（列类型确属未知，非伪造）。
     */
    private String readColumnType(ResultSetMetaData rsMeta, int columnIdx, String querySpace) {
        try {
            return rsMeta.getColumnTypeName(columnIdx);
        } catch (SQLException e) {
            throw new NopException(ERR_SQL_TYPE_INFERENCE_FAILED)
                    .param("error", "getColumnTypeName failed for column " + columnIdx + ": " + messageOf(e))
                    .param("querySpace", querySpace)
                    .cause(e);
        }
    }

    private static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            return null;
        }
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }
}
