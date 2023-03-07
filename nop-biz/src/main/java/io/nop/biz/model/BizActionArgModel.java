/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.model;

import io.nop.biz.api.IBizActionArgModel;
import io.nop.biz.model._gen._BizActionArgModel;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xmeta.ISchema;

public class BizActionArgModel extends _BizActionArgModel implements IBizActionArgModel {
    public BizActionArgModel() {

    }

    public IGenericType getType() {
        ISchema schema = getSchema();
        return schema == null ? null : schema.getType();
    }
}
