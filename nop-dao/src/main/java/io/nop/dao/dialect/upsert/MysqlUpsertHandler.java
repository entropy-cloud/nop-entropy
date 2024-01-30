package io.nop.dao.dialect.upsert;

import io.nop.core.lang.sql.SQL;

public class MysqlUpsertHandler implements IUpsertHandler {
    @Override
    public SQL.SqlBuilder buildUpsert(String tableName, String[] columnNames) {
        return null;
    }
}
