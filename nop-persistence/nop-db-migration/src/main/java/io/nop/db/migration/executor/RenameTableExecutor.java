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
import io.nop.db.migration.model.RenameTableChange;

public class RenameTableExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "renameTable";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        RenameTableChange renameTable = (RenameTableChange) change;
        String oldTableName = renameTable.getOldTableName();
        String newTableName = renameTable.getNewTableName();
        if (StringHelper.isBlank(oldTableName) || StringHelper.isBlank(newTableName)) {
            return;
        }
        String sql = buildRenameTableSql(renameTable, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("rename-table-" + oldTableName)
                    .append(sql)
                    .end()
            );
        }
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        RenameTableChange renameTable = (RenameTableChange) change;
        return "ALTER TABLE " + dialect.escapeSQLName(renameTable.getNewTableName()) +
               " RENAME TO " + dialect.escapeSQLName(renameTable.getOldTableName());
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    protected String buildRenameTableSql(RenameTableChange change, IDialect dialect) {
        return "ALTER TABLE " + dialect.escapeSQLName(change.getOldTableName()) +
               " RENAME TO " + dialect.escapeSQLName(change.getNewTableName());
    }
}
