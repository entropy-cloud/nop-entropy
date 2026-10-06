/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataRow;

import static io.nop.orm.migration.MigrationErrors.ARG_DETAIL;
import static io.nop.orm.migration.MigrationErrors.ARG_DIALECT;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_P0_PIN_FAILED;

/**
 * PREPARE 阶段的源位点 P0 钉定（设计 §4.2）：经 JDBC 查询源库当前位点。
 *
 * <ul>
 * <li>mysql: {@code SHOW MASTER STATUS} → binlog File + Position（存在 Executed_Gtids 列时一并读取）；</li>
 * <li>postgres: {@code SELECT pg_current_wal_lsn()} → WAL LSN。</li>
 * </ul>
 *
 * <p>{@link PositionProvider} 是测试 seam：真实实现走 JDBC 查询，H2/内嵌测试注入模拟位点对象
 * （设计验收允许"H2 下模拟位点对象"）。方言不支持时显式失败（ERR_MIG_P0_PIN_FAILED）。</p>
 */
public class SourcePositionPinner {

    /**
     * 位点查询函数 seam。
     */
    public interface PositionProvider {
        SourcePosition pin(IJdbcTemplate jdbc, String querySpace, String connectorType);
    }

    private PositionProvider provider = new JdbcPositionProvider();

    public void setPositionProvider(PositionProvider provider) {
        this.provider = provider;
    }

    /**
     * 钉定 P0。重复执行取新值（设计 §4.1 PREPARE 幂等语义：校验幂等、P0 钉定重复执行取新值）。
     */
    public SourcePosition pin(IJdbcTemplate jdbc, String querySpace, String connectorType) {
        SourcePosition position = provider.pin(jdbc, querySpace, connectorType);
        if (position == null)
            throw new NopException(ERR_MIG_P0_PIN_FAILED).param(ARG_DIALECT, connectorType)
                    .param(ARG_DETAIL, "position provider returned null");
        return position;
    }

    /**
     * 缺省实现：mysql/pg 各自的原生位点查询。调用失败或方言不支持均显式失败。
     */
    public static final class JdbcPositionProvider implements PositionProvider {
        @Override
        public SourcePosition pin(IJdbcTemplate jdbc, String querySpace, String connectorType) {
            if (SourcePosition.TYPE_MYSQL.equalsIgnoreCase(connectorType))
                return pinMysql(jdbc, querySpace);
            if (SourcePosition.TYPE_POSTGRES.equalsIgnoreCase(connectorType))
                return pinPostgres(jdbc, querySpace);
            throw new NopException(ERR_MIG_P0_PIN_FAILED).param(ARG_DIALECT, connectorType)
                    .param(ARG_DETAIL, "CDC position pinning supports mysql/postgres only");
        }

        private SourcePosition pinMysql(IJdbcTemplate jdbc, String querySpace) {
            try {
                SQL sql = SQL.begin().querySpace(querySpace).name("orm-migration-pin-p0-mysql")
                        .sql("SHOW MASTER STATUS").end();
                return jdbc.findFirst(sql, (row, rowNum, colMapper) -> {
                    // nop dataset 列序为 0 基（JdbcDataSet: rs.getXxx(index + 1)）
                    String file = row.getString(0);
                    long pos = row.getLong(1);
                    // MySQL 8.0.22+ 增补了 Executed_Gtids 列；旧版本列不存在
                    String gtidSet = columnIfPresent(row, 4);
                    return SourcePosition.mysql(file, pos, gtidSet);
                });
            } catch (Exception e) {
                throw new NopException(ERR_MIG_P0_PIN_FAILED, e).param(ARG_DIALECT, SourcePosition.TYPE_MYSQL)
                        .param(ARG_DETAIL, "SHOW MASTER STATUS failed: " + e.getMessage());
            }
        }

        private SourcePosition pinPostgres(IJdbcTemplate jdbc, String querySpace) {
            try {
                SQL sql = SQL.begin().querySpace(querySpace).name("orm-migration-pin-p0-pg")
                        .sql("SELECT pg_current_wal_lsn()").end();
                return jdbc.findFirst(sql, (row, rowNum, colMapper) ->
                        SourcePosition.postgres(row.getString(0)));
            } catch (Exception e) {
                throw new NopException(ERR_MIG_P0_PIN_FAILED, e)
                        .param(ARG_DIALECT, SourcePosition.TYPE_POSTGRES)
                        .param(ARG_DETAIL, "pg_current_wal_lsn() failed: " + e.getMessage());
            }
        }

        private String columnIfPresent(IDataRow row, int index) {
            try {
                return row.getString(index);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
