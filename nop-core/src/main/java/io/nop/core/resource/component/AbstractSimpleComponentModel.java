/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;

/**
 * 不支持扩展属性
 */
public abstract class AbstractSimpleComponentModel extends AbstractFreezable
        implements IComponentModel, IJsonSerializable {
    @Override
    public void serializeToJson(IJsonHandler out) {
        out.beginObject(getLocation());
        outputJson(out);
        out.endObject();
    }

    protected void outputJson(IJsonHandler handler) {

    }
}
