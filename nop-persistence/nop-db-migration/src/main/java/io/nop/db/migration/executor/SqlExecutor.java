/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.executor;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.DbSpecificSql;
import io.nop.db.migration.model.SqlChange;

import static io.nop.db.migration.DbMigrationErrors.ARG_CHANGE_TYPE;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_NO_SQL_FOR_DIALECT;

public class SqlExecutor implements IChangeExecutor {
    
    public static final String CHANGE_TYPE = "sql";
    
    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        SqlChange sqlChange = (SqlChange) change;
        
        String sql = getSqlForDialect(sqlChange, dialect);
        
        if (StringHelper.isBlank(sql)) {
            return;
        }
        
        if (sqlChange.isSplitStatements()) {
            context.getJdbcTemplate().executeMultiSql(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("migration-sql")
                    .append(sql)
                    .end()
            );
        } else {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("migration-sql")
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
    
    protected String getSqlForDialect(SqlChange change, IDialect dialect) {
        if (change.getDbSpecific() != null && !change.getDbSpecific().isEmpty()) {
            String dialectName = dialect.getName().toLowerCase();
            for (DbSpecificSql specific : change.getDbSpecific()) {
                if (specific.getDbType() != null && 
                    specific.getDbType().toLowerCase().equals(dialectName)) {
                    return specific.getBody();
                }
            }
        }
        
        String body = change.getBody();
        if (StringHelper.isBlank(body)) {
            throw new NopException(ERR_DB_MIGRATION_NO_SQL_FOR_DIALECT)
                .param(ARG_CHANGE_TYPE, "sql");
        }
        return body;
    }
}
