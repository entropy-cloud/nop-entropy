package io.nop.db.migration.precondition;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.db.migration.model.ColumnExistsPrecondition;

public class ColumnExistsChecker implements IPreconditionChecker {
    
    public static final String PRECONDITION_TYPE = "columnExists";
    
    @Override
    public String getPreconditionType() {
        return PRECONDITION_TYPE;
    }
    
    @Override
    public boolean check(AbstractComponentModel precondition, MigrationContext context) {
        if (!(precondition instanceof ColumnExistsPrecondition)) {
            return false;
        }
        
        ColumnExistsPrecondition cep = (ColumnExistsPrecondition) precondition;
        String tableName = cep.getTableName();
        String columnName = cep.getColumnName();
        String schemaName = cep.getSchemaName();
        PreconditionExpect expect = cep.getExpect();
        if (expect == null) {
            expect = PreconditionExpect.EXISTS;
        }
        
        boolean exists = checkColumnExists(context.getJdbcTemplate(), context.getQuerySpace(), tableName, columnName, schemaName);
        
        return expect == PreconditionExpect.EXISTS ? exists : !exists;
    }
    
    private boolean checkColumnExists(IJdbcTemplate jdbcTemplate, String querySpace, String tableName, String columnName, String schemaName) {
        return jdbcTemplate.existsColumn(querySpace, schemaName, tableName, columnName);
    }
}
