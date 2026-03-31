package io.nop.db.migration.precondition;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.db.migration.model.ForeignKeyExistsPrecondition;

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
        return jdbcTemplate.existsForeignKey(querySpace, schemaName, tableName, constraintName);
    }
}
