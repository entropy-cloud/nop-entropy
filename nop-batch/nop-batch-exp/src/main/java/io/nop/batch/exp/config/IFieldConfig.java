package io.nop.batch.exp.config;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;

public interface IFieldConfig extends ISourceLocationGetter {
    String getName();

    String getFrom();

    default String getSourceFieldName() {
        String from = getFrom();
        if (from == null)
            from = getName();
        return from;
    }

    boolean isIgnore();

    StdDataType getStdDataType();

    StdSqlType getStdSqlType();

    IEvalAction getTransformExpr();

    default Object validate(Object value, IEvalScope scope) {
        return value;
    }
}
