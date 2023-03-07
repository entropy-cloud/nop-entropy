/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.query;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.IVariableScope;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.DisabledEvalScope;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MAX;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MAX_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MIN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MIN_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT;

/**
 * 将FilterBean格式化为类SQL语法，用于界面展示
 */
public class FilterBeanFormatter extends FilterBeanVisitor<Void> {
    private Function<String, String> nameTransformer;
    private StringBuilder buf;
    private int depth;
    private boolean skipIndent;

    public String format(ITreeBean filter) {
        this.buf = new StringBuilder();
        visit(filter, DisabledEvalScope.INSTANCE);
        return buf.toString();
    }

    void indent() {
        if (buf.length() <= 0)
            return;

        if (skipIndent) {
            skipIndent = false;
            return;
        }

        buf.append('\n');
        if (depth > 1) {
            for (int i = 1; i < depth; i++) {
                buf.append("    ");
            }
        }
    }

    public Void visitUnknown(String op, ITreeBean filter, IVariableScope scope) {
        indent();
        buf.append(op).append('(');
        Map<String, Object> attrs = filter.getAttrs();
        if (attrs != null && !attrs.isEmpty()) {
            String text = JSON.stringify(attrs);
            buf.append(text);
        }
        buf.append('}');
        return null;
    }

    @Override
    public Void visitAlwaysTrue(ITreeBean filter, IVariableScope scope) {
        indent();
        buf.append(" 1=1 ");
        return null;
    }

    @Override
    public Void visitAlwaysFalse(ITreeBean filter, IVariableScope sceop) {
        buf.append(" 1=0 ");
        return null;
    }

    @Override
    protected Void visitCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String label = nameTransformer.apply(getName(filter));
        indent();
        String opText = getOpText(filterOp);
        buf.append(label).append(' ').append(opText).append(' ')
                .append(getLabelOrValue(filter, FILTER_ATTR_VALUE_NAME, FILTER_ATTR_VALUE));
        return null;
    }

    protected Object getLabelOrValue(ITreeBean filter, String nameAttr, String valueAttr) {
        String text = ConvertHelper.toString(filter.getAttr(nameAttr));
        if (StringHelper.isEmpty(text)) {
            Object value = filter.getAttr(valueAttr);
            if (value == null || value instanceof Number)
                return value;
            String str = value.toString();
            return StringHelper.quote(str);
        }
        return nameTransformer.apply(text);
    }

    @Override
    protected Void visitAssertOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String label = nameTransformer.apply(getName(filter));
        indent();
        String opText = getOpText(filterOp);
        buf.append(label).append(' ').append(opText);
        return null;
    }

    @Override
    protected Void visitBetweenOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String label = nameTransformer.apply(getName(filter));
        indent();
        String opText = getOpText(filterOp);
        buf.append(label).append(' ').append(opText).append(' ')
                .append(getLabelOrValue(filter, FILTER_ATTR_MIN_NAME, FILTER_ATTR_MIN)).append(" and ")
                .append(getLabelOrValue(filter, FILTER_ATTR_MAX_NAME, FILTER_ATTR_MAX));
        return null;
    }

    @Override
    public Void visitGroup(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty()) {
            return null;
        }

        String op = filter.getTagName();
        indent();
        boolean not = FILTER_OP_NOT.equals(op);

        if (not) {
            buf.append(op).append("( ");
            skipIndent = true;
        }

        if (children.size() == 1) {
            visit(filter.getChildren().get(0), scope);

            if (not) {
                buf.append(" )");
            }
            return null;
        }

        if (depth > 0)
            buf.append("(");

        depth++;
        for (int i = 0, n = children.size(); i < n; i++) {
            ITreeBean child = children.get(i);
            visit(child, scope);
            if (i != n - 1) {
                indent();
                buf.append(not ? "and" : op).append(' ');
                skipIndent = true;
            }
        }
        depth--;
        skipIndent = false;

        if (depth > 0) {
            indent();
            buf.append(")");
        }

        return null;
    }
}