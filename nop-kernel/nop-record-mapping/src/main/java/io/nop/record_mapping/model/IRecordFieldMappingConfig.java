package io.nop.record_mapping.model;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.model.mapper.IValueMapper;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xmeta.ISchema;

public interface IRecordFieldMappingConfig extends ISourceLocationGetter {
    ISchema getSchema();

    IGenericType getType();

    IEvalFunction getValueExpr();

    boolean isMandatory();

    Object getNormalizedDefaultValue();

    IValueMapper<String, Object> getValueMapper();

    String getVarName();

    boolean isDisableToPropPath();
}
