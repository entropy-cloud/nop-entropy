/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.executor;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.InsertColumnModel;
import io.nop.db.migration.model.InsertDataChange;

public class InsertDataExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "insertData";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        InsertDataChange insertData = (InsertDataChange) change;
        String tableName = insertData.getTableName();
        if (StringHelper.isBlank(tableName) || insertData.getColumns() == null || insertData.getColumns().isEmpty()) {
            return;
        }
        String sql = buildInsertSql(insertData, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("insert-data-" + tableName)
                    .append(sql)
                    .end()
            );
        }
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        InsertDataChange insertData = (InsertDataChange) change;
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ").append(dialect.escapeSQLName(insertData.getTableName()));
        return sb.toString();
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    protected String buildInsertSql(InsertDataChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(dialect.escapeSQLName(change.getTableName()));
        sb.append(" (");
        
        StringBuilder values = new StringBuilder();
        boolean first = true;
        if (change.getColumns() != null) {
            for (InsertColumnModel column : change.getColumns()) {
                if (!first) {
                    sb.append(", ");
                    values.append(", ");
                }
                first = false;
                sb.append(dialect.escapeSQLName(column.getName()));
                values.append(escapeValue(column.getValue(), dialect));
            }
        }
        
        sb.append(") VALUES (");
        sb.append(values);
        sb.append(")");
        
        return sb.toString();
    }

    protected String escapeValue(String value, IDialect dialect) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
