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
        String sql = buildAddColumnSql(addColumn, dialect);
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

    protected String buildAddColumnSql(AddColumnChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        
        if (change.getColumns() != null) {
            boolean first = true;
            for (ColumnDefinition column : change.getColumns()) {
                if (!first) {
                    sb.append("; ");
                }
                first = false;
                
                sb.append("ALTER TABLE ")
                  .append(dialect.escapeSQLName(change.getTableName()))
                  .append(" ADD COLUMN ")
                  .append(dialect.escapeSQLName(column.getName()))
                  .append(" ");
                
                StdSqlType type = column.getType();
                String typeName = type != null ? type.name() : "VARCHAR";
                sb.append(typeName);
                
                Integer size = column.getSize();
                if (size != null && size > 0) {
                    sb.append("(").append(size).append(")");
                }
                
                if (!column.isNullable()) {
                    sb.append(" NOT NULL");
                }
                if (StringHelper.isNotBlank(column.getDefaultValue())) {
                    sb.append(" DEFAULT ").append(column.getDefaultValue());
                }
                if (StringHelper.isNotBlank(column.getRemark())) {
                    sb.append(" COMMENT '").append(column.getRemark()).append("'");
                }
            }
        }
        
        return sb.toString();
    }
}
