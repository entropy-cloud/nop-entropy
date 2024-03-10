/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.impl.MethodInvokers;

public class MyObjectInvokerInitializer implements ICoreInitializer {
    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_INVOKER;
    }

    @Override
    public void initialize() {
        // 注册invoker，替代Java反射调用
        MethodInvokers inv = new MethodInvokers();
        inv.call2(false, "testEval", IEvalScope.class, int.class, (thisObj, scope, value, context) -> {
            return ((MyObject) thisObj).testEval((IEvalScope) scope, (int) value);
        });

        ReflectionManager.instance().registerInvokers(MyObject.class, inv);
    }
}
