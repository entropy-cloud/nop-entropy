/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.executor;

import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.AddColumnChange;
import io.nop.db.migration.model.ColumnDefinition;

public class AddColumnExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "addColumn";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        AddColumnChange addColumn = (AddColumnChange) change;
        String tableName = addColumn.getTableName();
        if (StringHelper.isBlank(tableName) || addColumn.getColumns() == null || addColumn.getColumns().isEmpty()) {
            return;
        }
        // Each column is added with its own statement. executeUpdate does not
        // split multiple statements, so concatenating them with ';' would
        // produce a syntax error on H2/PostgreSQL/Oracle/MySQL.
        for (ColumnDefinition column : addColumn.getColumns()) {
            String sql = buildAddColumnSql(addColumn, column, dialect);
            if (StringHelper.isNotBlank(sql)) {
                context.getJdbcTemplate().executeUpdate(
                    SQL.begin()
                        .querySpace(context.getQuerySpace())
                        .name("add-column-" + tableName)
                        .append(sql)
                        .end()
                );
            }
        }
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        AddColumnChange addColumn = (AddColumnChange) change;
        StringBuilder sb = new StringBuilder();
        if (addColumn.getColumns() != null) {
            for (ColumnDefinition column : addColumn.getColumns()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append("ALTER TABLE ")
                  .append(dialect.escapeSQLName(addColumn.getTableName()))
                  .append(" DROP COLUMN ")
                  .append(dialect.escapeSQLName(column.getName()));
            }
        }
        return sb.toString();
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    protected String buildAddColumnSql(AddColumnChange change, ColumnDefinition column, IDialect dialect) {
        StringBuilder sb = new StringBuilder();

        sb.append("ALTER TABLE ")
          .append(dialect.escapeSQLName(change.getTableName()))
          .append(" ADD COLUMN ")
          .append(dialect.escapeSQLName(column.getName()))
          .append(" ")
          .append(buildColumnType(column, dialect));

        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }
        if (StringHelper.isNotBlank(column.getDefaultValue())) {
            sb.append(" DEFAULT ").append(column.getDefaultValue());
        }
        if (StringHelper.isNotBlank(column.getRemark())) {
            sb.append(" COMMENT '").append(escapeComment(column.getRemark())).append("'");
        }

        return sb.toString();
    }

    static String buildColumnType(ColumnDefinition column, IDialect dialect) {
        StdSqlType type = column.getType();
        if (type == null) {
            type = StdSqlType.VARCHAR;
        }
        int size = column.getSize() != null && column.getSize() > 0 ? column.getSize() : -1;
        int decimalDigits = column.getDecimalDigits() != null && column.getDecimalDigits() > 0 ? column.getDecimalDigits() : -1;
        return dialect.stdToNativeSqlType(type, size, decimalDigits).toString();
    }

    static String escapeComment(String comment) {
        return comment.replace("'", "''");
    }
}
