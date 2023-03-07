/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
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
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v = executor.execute(attrs, scope);
        if (v == null)
            return null;

        if (!(v instanceof Map))
            throw newError(ERR_EXEC_XML_EXT_ATTRS_NOT_MAP);

        IEvalOutput out = scope.getOut();
        Map<String, Object> map = (Map<String, Object>) v;
        if (excludeNames.isEmpty()) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                outputAttr(entry, out);
            }
        } else {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!excludeNames.contains(entry.getKey()))
                    outputAttr(entry, out);
            }
        }
        return null;
    }

    private void outputAttr(Map.Entry<String, Object> entry, IEvalOutput out) {
        Object value = entry.getValue();
        if (value != null) {
            SourceLocation loc = getLocation();
            out.text(null, " ");
            out.text(null, entry.getKey());
            out.text(null, "=\"");
            out.text(loc, StringHelper.escapeXmlAttr(value.toString()));
            out.text(null, "\"");
        }
    }
}
