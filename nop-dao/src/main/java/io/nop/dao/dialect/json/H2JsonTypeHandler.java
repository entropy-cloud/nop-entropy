package io.nop.dao.dialect.json;

import io.nop.dataset.binder.IDataParameters;

import java.nio.charset.StandardCharsets;

public class H2JsonTypeHandler extends DefaultJsonTypeHandler {

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setBytes(index, null);
        } else {
            String str = value.toString();
            params.setBytes(index, str.getBytes(StandardCharsets.UTF_8));
        }
    }
}
