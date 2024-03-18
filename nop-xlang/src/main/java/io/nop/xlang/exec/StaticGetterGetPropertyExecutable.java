/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IPropertyGetter;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_PROP_FAIL;

public class StaticGetterGetPropertyExecutable extends AbstractExecutable {
    private final String className;
    private final String propName;
    private final IPropertyGetter getter;

    public StaticGetterGetPropertyExecutable(SourceLocation loc, String className, String propName,
                                             IPropertyGetter getter) {
        super(loc);
        this.className = Guard.notEmpty(className, "className");
        this.propName = Guard.notEmpty(propName, "propName");
        this.getter = Guard.notNull(getter, "getter");
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(className);
        sb.append('.');
        sb.append(propName);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        try {
            return getter.getProperty(null, propName, rt.getScope());
        } catch (Exception e) {
            throw newError(ERR_EXEC_READ_PROP_FAIL, e).forWrap().param(ARG_CLASS_NAME, className).param(ARG_PROP_NAME,
                    propName);
        }
    }
}
