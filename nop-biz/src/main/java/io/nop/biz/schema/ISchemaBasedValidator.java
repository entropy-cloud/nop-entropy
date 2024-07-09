package io.nop.biz.schema;

import io.nop.xlang.xmeta.ISchema;

public interface ISchemaBasedValidator {
    ISchema getSchemaForBean(Class<?> beanClass);

    ISchema getSchema(String bizObjName);

    void validate(ISchema schema, Object value, ValidationContext validationContext);
}
