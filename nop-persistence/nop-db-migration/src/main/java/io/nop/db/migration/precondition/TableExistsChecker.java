package io.nop.db.migration.precondition;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.db.migration.model.TableExistsPrecondition;

public class TableExistsChecker implements IPreconditionChecker {
    
    public static final String PRECONDITION_TYPE = "tableExists";
    
    @Override
    public String getPreconditionType() {
        return PRECONDITION_TYPE;
    }
    
    @Override
    public boolean check(AbstractComponentModel precondition, MigrationContext context) {
        if (!(precondition instanceof TableExistsPrecondition)) {
            return false;
        }
        
        TableExistsPrecondition tep = (TableExistsPrecondition) precondition;
        String tableName = tep.getTableName();
        String schemaName = tep.getSchemaName();
        PreconditionExpect expect = tep.getExpect();
        if (expect == null) {
            expect = PreconditionExpect.EXISTS;
        }
        
        boolean exists = checkTableExists(context.getJdbcTemplate(), context.getQuerySpace(), tableName, schemaName);
        
        return expect == PreconditionExpect.EXISTS ? exists : !exists;
    }
    
    private boolean checkTableExists(IJdbcTemplate jdbcTemplate, String querySpace, String tableName, String schemaName) {
        return jdbcTemplate.existsTable(querySpace, schemaName, tableName);
    }
}
