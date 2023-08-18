/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.model;

import io.nop.api.core.util.INeedInit;
import io.nop.biz.api.IBizActionArgModel;
import io.nop.biz.model._gen._BizActionArgModel;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.SchemaImpl;

public class BizActionArgModel extends _BizActionArgModel implements IBizActionArgModel, INeedInit {
    public BizActionArgModel() {

    }

    @Override
    public void init() {
        // 同步type属性和schema上的type属性
        IGenericType type = getType();
        if (type != null) {
            ISchema schema = getSchema();
            if (schema == null) {
                schema = new SchemaImpl();
                setSchema(schema);
            }
            ((SchemaImpl) schema).setType(type);
        } else {
            ISchema schema = getSchema();
            if (schema != null) {
                setType(schema.getType());
            }
        }
    }
}
