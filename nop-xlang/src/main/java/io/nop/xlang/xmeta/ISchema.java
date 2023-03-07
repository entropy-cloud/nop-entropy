/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.annotations.core.BeanClass;
import io.nop.xlang.xmeta.impl.SchemaImpl;

@BeanClass(SchemaImpl.class)
public interface ISchema extends ISimpleSchema, IListSchema, IMapSchema, IObjSchema, IUnionSchema {
    boolean isExplicitDefine();
}
