/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

import java.util.Map;

public class NewMapExecutable extends AbstractExecutable {
    private final MapItemExecutable[] itemExprs;

    public NewMapExecutable(SourceLocation loc, MapItemExecutable[] itemExprs) {
        super(loc);
        this.itemExprs = itemExprs;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append('{');
        for (int i = 0, n = itemExprs.length; i < n; i++) {
            itemExprs[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append('}');
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(itemExprs.length / 2);
        for (MapItemExecutable itemExpr : itemExprs) {
            if (itemExpr.isSpread()) {
                XLangSemantics.spreadMapPut(ret, itemExpr.getValue(executor, rt));
                // 如果null为空或者undefined，直接忽略
            } else {
                Object key = itemExpr.getKey(executor, rt);
                Object value = itemExpr.getValue(executor, rt);
                ret.put(StringHelper.toString(key, null), value);
            }
        }
        return ret;
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            for (MapItemExecutable item : this.itemExprs) {
                item.getKeyExpr().visit(visitor);
                item.getValueExpr().visit(visitor);
            }
            visitor.onEndVisitExpr(this);
        }
    }
    public io.nop.xlang.exec.MapItemExecutable[] getItems() {
        return itemExprs;
    }

}
