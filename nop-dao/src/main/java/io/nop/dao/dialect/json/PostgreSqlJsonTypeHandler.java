package io.nop.dao.dialect.json;

import io.nop.dataset.binder.IDataParameters;

import java.sql.Types;

public class PostgreSqlJsonTypeHandler extends DefaultJsonTypeHandler {


    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setObject(index, null, Types.OTHER);
        } else {
            String str = value.toString();
            params.setObject(index, str, Types.OTHER);
        }
    }
}
