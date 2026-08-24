/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical-entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.executor;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.AlterColumnChange;

import java.util.ArrayList;
import java.util.List;

import static io.nop.db.migration.DbMigrationErrors.ARG_COLUMN_NAME;
import static io.nop.db.migration.DbMigrationErrors.ARG_TABLE_NAME;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_EXECUTION_FAILED;

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
        // One statement per action: PostgreSQL/H2 do not accept chained
        // "TYPE x SET NOT NULL" in a single ALTER COLUMN clause, and MySQL
        // needs a single MODIFY COLUMN statement with the full definition.
        for (String sql : buildAlterColumnSqls(alterColumn, dialect)) {
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
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        return null;
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    protected List<String> buildAlterColumnSqls(AlterColumnChange change, IDialect dialect) {
        String tableName = dialect.escapeSQLName(change.getTableName());
        String columnName = dialect.escapeSQLName(change.getColumnName());

        String typeSql = null;
        StdSqlType newType = change.getNewType();
        if (newType != null) {
            Integer size = change.getNewSize();
            int precision = size != null && size > 0 ? size : -1;
            Integer digits = change.getNewDecimalDigits();
            int scale = digits != null && digits > 0 ? digits : -1;
            typeSql = dialect.stdToNativeSqlType(newType, precision, scale).toString();
        }
        Boolean newNullable = change.getNewNullable();
        String newDefault = change.getNewDefaultValue();
        if (newDefault != null) {
            DdlSyntax.checkDefaultValue(newDefault, change.getTableName(), change.getColumnName());
        }

        List<String> statements = new ArrayList<>();
        switch (DdlSyntax.family(dialect)) {
            case DdlSyntax.FAMILY_MYSQL:
            case DdlSyntax.FAMILY_ORACLE:
            case DdlSyntax.FAMILY_MSSQL:
                // MODIFY (Oracle) / MODIFY COLUMN (MySQL) / ALTER COLUMN (MSSQL)
                // replace the whole column definition, so the new type is
                // required to also change nullability or the default value
                if (typeSql == null && (newNullable != null || StringHelper.isNotBlank(newDefault))) {
                    throw new NopException(ERR_DB_MIGRATION_EXECUTION_FAILED)
                        .param(ARG_TABLE_NAME, change.getTableName())
                        .param(ARG_COLUMN_NAME,
                            change.getColumnName() + ": newType is required on this dialect to alter"
                                + " nullability or default value");
                }
                statements.add("ALTER TABLE " + tableName
                    + modifyColumnClause(dialect, columnName, typeSql, newNullable, newDefault));
                return statements;
            default:
                // PostgreSQL/H2 standard form: one ALTER COLUMN action per statement
                if (typeSql != null) {
                    statements.add("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                        + (DdlSyntax.family(dialect) == DdlSyntax.FAMILY_H2 ? " SET DATA TYPE " : " TYPE ")
                        + typeSql);
                }
                if (newNullable != null) {
                    statements.add("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                        + (newNullable ? " DROP NOT NULL" : " SET NOT NULL"));
                }
                if (StringHelper.isNotBlank(newDefault)) {
                    statements.add("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                        + " SET DEFAULT " + newDefault);
                }
                return statements;
        }
    }

    private String modifyColumnClause(IDialect dialect, String columnName, String typeSql,
                                      Boolean newNullable, String newDefault) {
        StringBuilder sb = new StringBuilder();
        String family = DdlSyntax.family(dialect);
        if (family == DdlSyntax.FAMILY_ORACLE) {
            sb.append(" MODIFY (").append(columnName);
        } else if (family == DdlSyntax.FAMILY_MSSQL) {
            sb.append(" ALTER COLUMN ").append(columnName);
        } else {
            sb.append(" MODIFY COLUMN ").append(columnName);
        }
        if (typeSql != null) {
            sb.append(" ").append(typeSql);
        }
        if (newNullable != null) {
            sb.append(newNullable ? " NULL" : " NOT NULL");
        }
        if (StringHelper.isNotBlank(newDefault)) {
            sb.append(" DEFAULT ").append(newDefault);
        }
        if (family == DdlSyntax.FAMILY_ORACLE) {
            sb.append(")");
        }
        return sb.toString();
    }
}
