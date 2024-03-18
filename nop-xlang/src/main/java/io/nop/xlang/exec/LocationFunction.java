/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExpressionExecutor;

/**
 * 返回函数调用处的源码位置
 */
public class LocationFunction extends AbstractExecutable {
    public LocationFunction(SourceLocation loc) {
        super(loc);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return getLocation();
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("location()");
    }
}
