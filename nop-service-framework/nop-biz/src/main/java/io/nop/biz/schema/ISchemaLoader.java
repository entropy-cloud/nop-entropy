package io.nop.biz.schema;

import io.nop.xlang.xmeta.ISchema;

public interface ISchemaLoader {
    ISchema loadSchema(String bizObjName);
}
