/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.UpsertStatements;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ARG_DIALECT;
import static io.nop.orm.migration.MigrationErrors.ARG_TARGET_TABLE;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_UPSERT_DIALECT_NOT_SUPPORTED;

/**
 * FULL 阶段的 keyed upsert 消费面（设计 §4.5：一切目标端写入 keyed UPSERT，不用裸 INSERT——
 * "任意点重跑"结构性幂等：全量 chunk 重放覆盖而非重复）。
 *
 * <p>行按新策略路由到的目标物理表分组，同表行合并为一次 JDBC batch upsert；写原语与增量
 * jdbc-2pc sink 共用同一方言事实源 {@link UpsertStatements}（m2 边界：方言无 native upsert
 * 显式失败，不静默降级）。数据面完全在物理表层，不经 ORM session（EncAssistedBackfiller 先例），
 * 因此搬迁管道自身不受写停止关约束（写门只禁业务 DML 提交入口）。</p>
 */
public class ShardedUpsertConsumerProvider implements IBatchConsumerProvider<Map<String, Object>> {

    private final IJdbcTemplate jdbcTemplate;
    private final TargetShardMapping mapping;
    /** 主键物理列名（有序，决定 conflict 列与 where 语义）。 */
    private final List<String> keyColumns;
    /** 搬迁的物理列全集（缺省从首行推导并缓存，批内 schema 漂移显式失败）。 */
    private List<String> columns;
    /** 消费面所属源物理表名（null 分片键 fail-fast 的定位信息）。 */
    private String sourceTableName;

    public ShardedUpsertConsumerProvider(IJdbcTemplate jdbcTemplate, TargetShardMapping mapping,
                                         List<String> keyColumns) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapping = mapping;
        this.keyColumns = keyColumns;
    }

    public void setSourceTableName(String sourceTableName) {
        this.sourceTableName = sourceTableName;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    @Override
    public IBatchConsumer<Map<String, Object>> setup(IBatchTaskContext context) {
        return this::consume;
    }

    void consume(Collection<Map<String, Object>> items, IBatchChunkContext context) {
        if (items.isEmpty())
            return;

        Map<PhysicalTable, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : items) {
            PhysicalTable target = mapping.routeRow(row, displayPk(row),
                    sourceTableName == null ? "-" : sourceTableName);
            groups.computeIfAbsent(target, k -> new ArrayList<>()).add(row);
        }

        for (Map.Entry<PhysicalTable, List<Map<String, Object>>> entry : groups.entrySet()) {
            upsertGroup(entry.getKey(), entry.getValue());
        }
    }

    private void upsertGroup(PhysicalTable target, List<Map<String, Object>> rows) {
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(target.getQuerySpace());
        List<String> cols = resolveColumns(rows.get(0));
        String upsertSql = UpsertStatements.buildNativeUpsertSqlText(dialect, target.getTableName(),
                cols, keyColumns, updateColumns(cols));
        if (upsertSql == null) {
            throw new NopException(ERR_MIG_UPSERT_DIALECT_NOT_SUPPORTED)
                    .param(ARG_DIALECT, dialect.getName())
                    .param(ARG_TARGET_TABLE, target.getTableName());
        }

        SQL anchor = SQL.begin().querySpace(target.getQuerySpace())
                .name("orm-migration-full-upsert:" + target.getTableName()).end();
        jdbcTemplate.runWithConnection(anchor, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                for (Map<String, Object> row : rows) {
                    int index = 1;
                    for (String col : cols) {
                        ps.setObject(index++, normalizeValue(row.get(col)));
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                throw dialect.getSQLExceptionTranslator().translate(
                        "orm-migration-full-upsert:" + target.getTableName(), e);
            }
            return null;
        });
    }

    private List<String> resolveColumns(Map<String, Object> firstRow) {
        if (columns != null && !columns.isEmpty())
            return columns;
        List<String> resolved = new ArrayList<>();
        for (String name : firstRow.keySet()) {
            resolved.add(name);
        }
        if (resolved.isEmpty())
            throw new IllegalStateException("migration upsert row has no columns");
        return resolved;
    }

    private List<String> updateColumns(List<String> cols) {
        List<String> update = new ArrayList<>(cols);
        update.removeAll(keyColumns);
        return update;
    }

    private Object normalizeValue(Object value) {
        // BigDecimal 全量精度直达驱动；其余类型原样（H2/mysql/pg JDBC 均可 setObject 绑定）
        return value;
    }

    private String displayPk(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyColumns.size(); i++) {
            if (i > 0)
                sb.append(',');
            Object v = row.get(keyColumns.get(i));
            if (v == null) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(keyColumns.get(i))) {
                        v = entry.getValue();
                        break;
                    }
                }
            }
            sb.append(v);
        }
        return sb.toString();
    }
}
