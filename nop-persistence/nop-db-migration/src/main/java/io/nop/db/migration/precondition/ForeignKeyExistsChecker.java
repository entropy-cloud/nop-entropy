package io.nop.db.migration.precondition;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.db.migration.model.ForeignKeyExistsPrecondition;
import io.nop.dataset.IDataRow;

public class ForeignKeyExistsChecker implements IPreconditionChecker {
    
    public static final String PRECONDITION_TYPE = "foreignKeyExists";
    
    @Override
    public String getPreconditionType() {
        return PRECONDITION_TYPE;
    }
    
    @Override
    public boolean check(AbstractComponentModel precondition, MigrationContext context) {
        if (!(precondition instanceof ForeignKeyExistsPrecondition)) {
            return false;
        }
        
        ForeignKeyExistsPrecondition fkep = (ForeignKeyExistsPrecondition) precondition;
        String tableName = fkep.getTableName();
        String constraintName = fkep.getConstraintName();
        String schemaName = fkep.getSchemaName();
        PreconditionExpect expect = fkep.getExpect();
        if (expect == null) {
            expect = PreconditionExpect.EXISTS;
        }
        
        boolean exists = checkForeignKeyExists(context.getJdbcTemplate(), context.getQuerySpace(), tableName, constraintName, schemaName);
        
        return expect == PreconditionExpect.EXISTS ? exists : !exists;
    }
    
    private boolean checkForeignKeyExists(IJdbcTemplate jdbcTemplate, String querySpace, String tableName, String constraintName, String schemaName) {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'FOREIGN KEY' AND UPPER(CONSTRAINT_NAME) = UPPER(?)";
        if (tableName != null && !tableName.isEmpty()) {
            sql += " AND UPPER(TABLE_NAME) = UPPER(?)";
        }
        if (schemaName != null && !schemaName.isEmpty()) {
            sql += " AND UPPER(TABLE_SCHEMA) = UPPER(?)";
        }
        
        final boolean[] exists = {false};
        
        if (tableName != null && !tableName.isEmpty() && schemaName != null && !schemaName.isEmpty()) {
            jdbcTemplate.executeQuery(
                SQL.begin().querySpace(querySpace).sql(sql, constraintName, tableName, schemaName).end(),
                dataSet -> {
                    for (IDataRow row : dataSet) {
                        exists[0] = row.getLong(0) > 0;
                        break;
                    }
                    return null;
                }
            );
        } else if (tableName != null && !tableName.isEmpty()) {
            jdbcTemplate.executeQuery(
                SQL.begin().querySpace(querySpace).sql(sql, constraintName, tableName).end(),
                dataSet -> {
                    for (IDataRow row : dataSet) {
                        exists[0] = row.getLong(0) > 0;
                        break;
                    }
                    return null;
                }
            );
        } else {
            jdbcTemplate.executeQuery(
                SQL.begin().querySpace(querySpace).sql(sql, constraintName).end(),
                dataSet -> {
                    for (IDataRow row : dataSet) {
                        exists[0] = row.getLong(0) > 0;
                        break;
                    }
                    return null;
                }
            );
        }
        
        return exists[0];
    }
}
