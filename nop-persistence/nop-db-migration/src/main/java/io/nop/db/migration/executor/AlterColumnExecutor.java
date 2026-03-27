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
import io.nop.db.migration.model.AlterColumnChange;

public class AlterColumnExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "alterColumn";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        AlterColumnChange alterColumn = (AlterColumnChange) change;
        String tableName = alterColumn.getTableName();
        String columnName = alterColumn.getColumnName();
        if (StringHelper.isBlank(tableName) || StringHelper.isBlank(columnName)) {
            return;
        }
        String sql = buildAlterColumnSql(alterColumn, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("alter-column-" + tableName + "-" + columnName)
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

    protected String buildAlterColumnSql(AlterColumnChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ")
          .append(dialect.escapeSQLName(change.getTableName()))
          .append(" ALTER COLUMN ")
          .append(dialect.escapeSQLName(change.getColumnName()));
        
        StdSqlType newType = change.getNewType();
        if (newType != null) {
            sb.append(" TYPE ").append(newType.name());
            Integer size = change.getNewSize();
            if (size != null && size > 0) {
                sb.append("(").append(size).append(")");
            }
        }
        
        if (change.getNewNullable() != null) {
            if (change.getNewNullable()) {
                sb.append(" DROP NOT NULL");
            } else {
                sb.append(" SET NOT NULL");
            }
        }
        
        if (StringHelper.isNotBlank(change.getNewDefaultValue())) {
            sb.append(" SET DEFAULT ").append(change.getNewDefaultValue());
        }
        
        return sb.toString();
    }
}
