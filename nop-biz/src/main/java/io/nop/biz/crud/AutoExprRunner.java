/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.crud;

import io.nop.biz.BizConstants;
import io.nop.commons.lang.Undefined;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.orm.OrmConstants;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.impl.ObjConditionExpr;

import java.util.Collection;

public class AutoExprRunner {
    public static void runAutoExpr(String action,
                                   Object entity, Object data,
                                   IObjSchema objMeta, IEvalScope scope,
                                   Collection<String> ignoreFields) {
        scope.setLocalValue(null, BizConstants.VAR_DATA, data);
        scope.setLocalValue(null, BizConstants.VAR_ENTITY, entity);
        scope.setLocalValue(null, BizConstants.VAR_OBJ_META, objMeta);

        for (IObjPropMeta propMeta : objMeta.getProps()) {
            ObjConditionExpr autoExpr = propMeta.getAutoExpr();
            if (autoExpr == null)
                continue;

            if (ignoreFields != null && ignoreFields.contains(propMeta.getName()))
                continue;

            if (autoExpr.getWhen() != null && !autoExpr.getWhen().contains(action))
                continue;

            Object value = null;
            if (autoExpr.getSource() != null) {
                scope.setLocalValue(null, OrmConstants.VAR_PROP_META, propMeta);
                value = autoExpr.getSource().invoke(scope);
            }

            if (value != Undefined.undefined)
                BeanTool.setProperty(entity, propMeta.getName(), value);
        }
    }
}
