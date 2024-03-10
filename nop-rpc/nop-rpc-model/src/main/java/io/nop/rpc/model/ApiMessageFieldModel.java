/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.core.type.IGenericType;
import io.nop.rpc.model._gen._ApiMessageFieldModel;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import io.nop.xlang.xmeta.impl.SchemaNodeImpl;

public class ApiMessageFieldModel extends _ApiMessageFieldModel implements IWithOptions {
    public ApiMessageFieldModel() {

    }

    public IGenericType getType() {
        ISchema schema = getSchema();
        return schema == null ? null : schema.getType();
    }

    public void setType(IGenericType type) {
        ISchema schema = getSchema();
        if (schema == null) {
            schema = new SchemaImpl();
            setSchema(schema);
        }
        ((SchemaNodeImpl) schema).setType(type);
    }
}
