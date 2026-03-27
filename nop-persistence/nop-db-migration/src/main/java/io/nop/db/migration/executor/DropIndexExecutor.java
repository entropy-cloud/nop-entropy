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
import io.nop.db.migration.model.DropIndexChange;

public class DropIndexExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "dropIndex";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        DropIndexChange dropIndex = (DropIndexChange) change;
        String indexName = dropIndex.getName();
        String tableName = dropIndex.getTableName();
        if (StringHelper.isBlank(indexName)) {
            return;
        }
        String sql = buildDropIndexSql(dropIndex, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("drop-index-" + indexName)
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

    protected String buildDropIndexSql(DropIndexChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("DROP INDEX ");
        sb.append(dialect.escapeSQLName(change.getName()));
        
        if (StringHelper.isNotBlank(change.getTableName())) {
            sb.append(" ON ").append(dialect.escapeSQLName(change.getTableName()));
        }
        
        return sb.toString();
    }
}
