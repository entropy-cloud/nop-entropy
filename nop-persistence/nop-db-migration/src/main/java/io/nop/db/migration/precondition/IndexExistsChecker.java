package io.nop.db.migration.precondition;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.db.migration.model.IndexExistsPrecondition;

public class IndexExistsChecker implements IPreconditionChecker {
    
    public static final String PRECONDITION_TYPE = "indexExists";
    
    @Override
    public String getPreconditionType() {
        return PRECONDITION_TYPE;
    }
    
    @Override
    public boolean check(AbstractComponentModel precondition, MigrationContext context) {
        if (!(precondition instanceof IndexExistsPrecondition)) {
            return false;
        }
        
        IndexExistsPrecondition iep = (IndexExistsPrecondition) precondition;
        String tableName = iep.getTableName();
        String indexName = iep.getIndexName();
        String schemaName = iep.getSchemaName();
        PreconditionExpect expect = iep.getExpect();
        if (expect == null) {
            expect = PreconditionExpect.EXISTS;
        }
        
        boolean exists = checkIndexExists(context.getJdbcTemplate(), context.getQuerySpace(), tableName, indexName, schemaName);
        
        return expect == PreconditionExpect.EXISTS ? exists : !exists;
    }
    
    private boolean checkIndexExists(IJdbcTemplate jdbcTemplate, String querySpace, String tableName, String indexName, String schemaName) {
        return jdbcTemplate.existsIndex(querySpace, schemaName, tableName, indexName);
    }
}
