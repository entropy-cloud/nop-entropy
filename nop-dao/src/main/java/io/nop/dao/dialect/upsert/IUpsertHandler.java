package io.nop.dao.dialect.upsert;

import io.nop.core.lang.sql.SQL;

public interface IUpsertHandler {
    SQL.SqlBuilder buildUpsert(String tableName, String[] columnNames);
}