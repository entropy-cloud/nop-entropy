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
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ERR_EXEC_XML_EXT_ATTRS_NOT_MAP;

public class OutputXmlExtAttrsExecutable extends AbstractExecutable {
    private final Set<String> excludeNames;
    private final IExecutableExpression attrs;

    public OutputXmlExtAttrsExecutable(SourceLocation loc, Set<String> excludeNames, IExecutableExpression attrs) {
        super(loc);
        this.excludeNames = excludeNames == null ? Collections.emptySet() : excludeNames;
        this.attrs = Guard.notNull(attrs, "attrs");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("@attrs:");
        attrs.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v = executor.execute(attrs, rt);
        XLangSemantics.outputXmlExtAttrs(getLocation(), display(), rt.getOut(), excludeNames, v);
        return null;
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            attrs.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }

    public Set<String> getExcludeNames() {
        return excludeNames;
    }

    public IExecutableExpression getAttrsExpr() {
        return attrs;
    }
}
