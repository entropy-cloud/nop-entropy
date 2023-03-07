/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.core.data;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.unittest.VarCollector;

public class AutoTestVarCollector extends VarCollector {

    @Override
    public void collectVar(String name, Object obj, String propName) {
        collectVar(name, BeanTool.instance().getProperty(obj, propName));
    }

    @Override
    public void collectVar(String name, Object value) {
        AutoTestVars.addVar(name, value);
    }
}