/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast._gen._CustomExpression;

public class CustomExpression extends _CustomExpression {
    private IExecutableExpression executable;

    public void setExecutable(IExecutableExpression executable) {
        this.executable = executable;
    }

    public IExecutableExpression getExecutable() {
        return executable;
    }

    public IExecutableExpression buildExecutable(IXLangCompileScope scope) {
        return executable;
    }

    @Override
    public CustomExpression deepClone() {
        CustomExpression ret = super.deepClone();
        ret.executable = executable;
        return ret;
    }

    public static CustomExpression build(SourceLocation loc, String source, IExecutableExpression executable) {
        CustomExpression ret = new CustomExpression();
        ret.setLocation(loc);
        ret.setSource(source);
        ret.setExecutable(executable);
        return ret;
    }
}
