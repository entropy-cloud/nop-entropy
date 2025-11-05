/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.binder;

import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

/**
 * 通过此接口可以提供针对单个数据库字段的特殊存取逻辑。例如字段级别的加解密等
 */
public interface IOrmColumnBinderEnhancer {
    IDataParameterBinder enhanceBinder(IEntityModel entityModel, IColumnModel col, IDataParameterBinder defaultBinder);
}