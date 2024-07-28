/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
            if (propMeta.isVirtual())
                continue;

            String name = propMeta.getName();
            if (propMeta.getMapToProp() != null)
                name = propMeta.getMapToProp();

            if (ignoreFields != null && ignoreFields.contains(propMeta.getName()))
                continue;

            ObjConditionExpr autoExpr = propMeta.getAutoExpr();
            if (autoExpr == null) {
                // 只有save的时候才考虑设置缺省值。
                if (propMeta.getDefaultValue() != null && BizConstants.METHOD_SAVE.equals(action)) {
                    BeanTool.setProperty(entity, name, propMeta.getDefaultValue());
                }
                continue;
            }

            if (autoExpr.getWhen() != null && !autoExpr.getWhen().contains(action))
                continue;

            Object value = null;
            if (autoExpr.getSource() != null) {
                scope.setLocalValue(null, OrmConstants.VAR_PROP_META, propMeta);
                value = autoExpr.getSource().invoke(scope);
            }

            if (value != Undefined.undefined)
                BeanTool.setProperty(entity, name, value);
        }
    }
}
