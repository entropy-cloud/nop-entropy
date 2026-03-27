package io.nop.db.migration.precondition;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.db.migration.model.IndexExistsPrecondition;
import io.nop.dataset.IDataRow;

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
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = ? AND INDEX_NAME = ?";
        if (schemaName != null && !schemaName.isEmpty()) {
            sql += " AND TABLE_SCHEMA = ?";
        }
        
        final boolean[] exists = {false};
        
        if (schemaName != null && !schemaName.isEmpty()) {
            jdbcTemplate.executeQuery(
                SQL.begin().querySpace(querySpace).sql(sql, tableName.toUpperCase(), indexName.toUpperCase(), schemaName.toUpperCase()).end(),
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
                SQL.begin().querySpace(querySpace).sql(sql, tableName.toUpperCase(), indexName.toUpperCase()).end(),
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
