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
import io.nop.db.migration.model.ColumnDefinition;
import io.nop.db.migration.model.CreateTableChange;

public class CreateTableExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "createTable";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        CreateTableChange createTable = (CreateTableChange) change;
        String tableName = createTable.getName();
        if (StringHelper.isBlank(tableName)) {
            return;
        }
        String sql = buildCreateTableSql(createTable, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("create-table-" + tableName)
                    .append(sql)
                    .end()
            );
        }
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        CreateTableChange createTable = (CreateTableChange) change;
        return "DROP TABLE IF EXISTS " + dialect.escapeSQLName(createTable.getName());
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    protected String buildCreateTableSql(CreateTableChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ");
        sb.append(dialect.escapeSQLName(change.getName()));
        sb.append(" (\n");
        
        boolean first = true;
        if (change.getColumns() != null) {
            for (ColumnDefinition column : change.getColumns()) {
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                sb.append("  ");
                sb.append(dialect.escapeSQLName(column.getName()));
                sb.append(" ");
                sb.append(buildColumnType(column, dialect));
                
                if (column.isPrimaryKey()) {
                    sb.append(" PRIMARY KEY");
                }
                if (!column.isNullable()) {
                    sb.append(" NOT NULL");
                }
                if (column.isAutoIncrement()) {
                    sb.append(" AUTO_INCREMENT");
                }
                if (StringHelper.isNotBlank(column.getDefaultValue())) {
                    sb.append(" DEFAULT ").append(column.getDefaultValue());
                }
            }
        }
        
        sb.append("\n)");
        
        if (StringHelper.isNotBlank(change.getRemark())) {
            sb.append(" COMMENT '").append(change.getRemark()).append("'");
        }
        
        return sb.toString();
    }

    protected String buildColumnType(ColumnDefinition column, IDialect dialect) {
        StdSqlType type = column.getType();
        String typeName = type != null ? type.name() : "VARCHAR";
        
        StringBuilder sb = new StringBuilder(typeName);
        
        Integer size = column.getSize();
        Integer decimalDigits = column.getDecimalDigits();
        
        if (size != null && size > 0) {
            sb.append("(").append(size);
            if (decimalDigits != null && decimalDigits > 0) {
                sb.append(",").append(decimalDigits);
            }
            sb.append(")");
        }
        
        return sb.toString();
    }
}
