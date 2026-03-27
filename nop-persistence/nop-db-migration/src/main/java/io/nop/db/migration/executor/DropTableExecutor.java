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
import io.nop.db.migration.model.DropTableChange;

public class DropTableExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "dropTable";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        DropTableChange dropTable = (DropTableChange) change;
        String tableName = dropTable.getName();
        if (StringHelper.isBlank(tableName)) {
            return;
        }
        String sql = buildDropTableSql(dropTable, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("drop-table-" + tableName)
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

    protected String buildDropTableSql(DropTableChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("DROP TABLE ");
        sb.append(dialect.escapeSQLName(change.getName()));
        return sb.toString();
    }
}
