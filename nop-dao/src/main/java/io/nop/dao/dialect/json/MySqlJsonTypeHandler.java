package io.nop.dao.dialect.json;

import io.nop.dataset.binder.IDataParameters;

public class MySqlJsonTypeHandler extends DefaultJsonTypeHandler {
    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setString(index, null);
        } else {
            String str = value.toString();
            params.setString(index, str);
        }
    }
}
