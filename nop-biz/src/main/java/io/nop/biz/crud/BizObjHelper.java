/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.crud;

import io.nop.biz.BizConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.xlang.xmeta.IObjPropMeta;

public class BizObjHelper {
    public static Object getProp(Object entity, IObjPropMeta propMeta, IServiceContext context) {
        if (propMeta.getGetter() != null) {
            IEvalAction getter = propMeta.getGetter();
            IEvalScope scope = context.getEvalScope().newChildScope();
            scope.setLocalValue(null, BizConstants.VAR_ENTITY, entity);
            return getter.invoke(scope);
        }

        String mapTo = propMeta.getMapTo();
        if (mapTo != null)
            return BeanTool.instance().getProperty(entity, mapTo);
        return BeanTool.instance().getProperty(entity, propMeta.getName());
    }
}
