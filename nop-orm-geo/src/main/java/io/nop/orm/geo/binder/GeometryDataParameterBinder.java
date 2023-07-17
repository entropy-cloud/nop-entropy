package io.nop.orm.geo.binder;

import io.nop.commons.type.StdSqlType;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.binder.IDataParameters;

public class GeometryDataParameterBinder implements IDataParameterBinder {
    @Override
    public StdSqlType getStdSqlType() {
        return StdSqlType.GEOMETRY;
    }

    @Override
    public Object getValue(IDataParameters params, int index) {
        return null;
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {

    }
}
