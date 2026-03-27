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
import io.nop.db.migration.model.CreateIndexChange;

public class CreateIndexExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "createIndex";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        CreateIndexChange createIndex = (CreateIndexChange) change;
        String indexName = createIndex.getName();
        String tableName = createIndex.getTableName();
        if (StringHelper.isBlank(indexName) || StringHelper.isBlank(tableName)) {
            return;
        }
        String sql = buildCreateIndexSql(createIndex, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("create-index-" + indexName)
                    .append(sql)
                    .end()
            );
        }
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        CreateIndexChange createIndex = (CreateIndexChange) change;
        return "DROP INDEX IF EXISTS " + dialect.escapeSQLName(createIndex.getName());
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    protected String buildCreateIndexSql(CreateIndexChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        
        if (change.isUnique()) {
            sb.append("CREATE UNIQUE INDEX ");
        } else {
            sb.append("CREATE INDEX ");
        }
        
        sb.append(dialect.escapeSQLName(change.getName()));
        sb.append(" ON ");
        sb.append(dialect.escapeSQLName(change.getTableName()));
        sb.append(" (");
        
        if (change.getColumnNames() != null) {
            boolean first = true;
            for (String columnName : change.getColumnNames()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(dialect.escapeSQLName(columnName));
            }
        }
        
        sb.append(")");
        
        return sb.toString();
    }
}
