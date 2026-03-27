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
import io.nop.db.migration.model.UpdateColumnModel;
import io.nop.db.migration.model.UpdateDataChange;

public class UpdateDataExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "updateData";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        UpdateDataChange updateData = (UpdateDataChange) change;
        String tableName = updateData.getTableName();
        if (StringHelper.isBlank(tableName) || updateData.getColumns() == null || updateData.getColumns().isEmpty()) {
            return;
        }
        String sql = buildUpdateSql(updateData, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("update-data-" + tableName)
                    .append(sql)
                    .end()
            );
        }
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        return null;
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    protected String buildUpdateSql(UpdateDataChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(dialect.escapeSQLName(change.getTableName()));
        sb.append(" SET ");
        
        boolean first = true;
        if (change.getColumns() != null) {
            for (UpdateColumnModel column : change.getColumns()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(dialect.escapeSQLName(column.getName()));
                sb.append(" = ");
                sb.append(escapeValue(column.getValue(), dialect));
            }
        }
        
        if (StringHelper.isNotBlank(change.getWhere())) {
            sb.append(" WHERE ");
            sb.append(change.getWhere());
        }
        
        return sb.toString();
    }

    protected String escapeValue(String value, IDialect dialect) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
