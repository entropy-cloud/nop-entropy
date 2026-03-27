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
import io.nop.db.migration.model.DeleteDataChange;

public class DeleteDataExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "deleteData";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        DeleteDataChange deleteData = (DeleteDataChange) change;
        String tableName = deleteData.getTableName();
        if (StringHelper.isBlank(tableName)) {
            return;
        }
        String sql = buildDeleteSql(deleteData, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("delete-data-" + tableName)
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

    protected String buildDeleteSql(DeleteDataChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(dialect.escapeSQLName(change.getTableName()));
        
        if (StringHelper.isNotBlank(change.getWhere())) {
            sb.append(" WHERE ");
            sb.append(change.getWhere());
        }
        
        return sb.toString();
    }
}
