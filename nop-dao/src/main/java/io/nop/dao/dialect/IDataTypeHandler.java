package io.nop.dao.dialect;

import io.nop.dataset.binder.IDataParameterBinder;

public interface IDataTypeHandler extends IDataParameterBinder {
    String toLiteral(Object value, IDialect dialect);

    Object fromLiteral(String text, IDialect dialect);

    boolean isJavaType(Object value);
}
