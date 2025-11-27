/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.query;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JsonTool;

import java.util.List;

import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MAX;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MAX_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MIN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_MIN_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_OWNER;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE_OWNER;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_ALWAYS_FALSE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_ALWAYS_TRUE;
import static io.nop.api.core.convert.ConvertHelper.defaults;
import static io.nop.core.CoreErrors.ARG_OP;
import static io.nop.core.CoreErrors.ERR_FILTER_NO_NAME_ARG;
import static io.nop.core.CoreErrors.ERR_FILTER_OP_IS_NULL;
import static io.nop.core.CoreErrors.ERR_FILTER_OP_NOT_ALLOW_CONTENT;
import static io.nop.core.CoreErrors.ERR_FILTER_UNKNOWN_OP;

public class FilterBeanVisitor<T> {

    public T visitRoot(ITreeBean filter, IVariableScope scope) {
        if (filter == null)
            return visitNullFilter(scope);

        String op = filter.getTagName();
        if (op.equals(CoreConstants.DUMMY_TAG_NAME) || op.equals(CoreConstants.FILTER_TAG_NAME))
            return visitAnd(filter, scope);
        return visit(filter, scope);
    }

    /**
     * 自上而下深度遍历树形结构的判断条件
     *
     * @param filter 通过{@link io.nop.api.core.beans.FilterBeans} 构造的树形过滤条件
     * @param scope  过滤条件中name属性所对应的值通过IVariableScope来负责解析
     */
    public T visit(ITreeBean filter, IVariableScope scope) {
        if (filter == null)
            return visitNullFilter(scope);

        String op = filter.getTagName();
        if (op == null)
            throw new NopException(ERR_FILTER_OP_IS_NULL).source(filter);

        FilterOp filterOp = getFilterOp(op);
        if (filterOp == null) {
            return visitUnknown(op, filter, scope);
        }

        switch (filterOp.getType()) {
            case GROUP_OP:
                checkNotAllowContent(filter);
                if (filterOp == FilterOp.AND)
                    return visitAnd(filter, scope);
                if (filterOp == FilterOp.OR)
                    return visitOr(filter, scope);
                if (filterOp == FilterOp.NOT)
                    return visitNot(filter, scope);
                // 不应该执行到这里，
                return visitUnknown(op, filter, scope);
            case ASSERT_OP: {
                checkNotAllowContent(filter);
                return visitAssertOp(filterOp, filter, scope);
            }
            case COMPARE_OP: {
                checkNotAllowContent(filter);
                return visitCompareOp(filterOp, filter, scope);
            }
            case BETWEEN_OP: {
                checkNotAllowContent(filter);
                return visitBetweenOp(filterOp, filter, scope);
            }
            case FIXED_VALUE: {
                checkNotAllowContent(filter);
                return visitFixedValue(filterOp, filter, scope);
            }
            case OTHER:
                return visitOther(filterOp, filter, scope);
            default:
                return visitUnknown(op, filter, scope);
        }
    }

    protected void checkNotAllowContent(ITreeBean filter) {
        if (filter.getContentValue() != null)
            throw new NopException(ERR_FILTER_OP_NOT_ALLOW_CONTENT).source(filter);
    }

    protected FilterOp getFilterOp(String op) {
        return FilterOp.fromName(op);
    }

    public T visitNullFilter(IVariableScope scope) {
        return null;
    }

    public T visitUnknown(String op, ITreeBean filter, IVariableScope scope) {
        throw new NopException(ERR_FILTER_UNKNOWN_OP).param(ARG_OP, op).source(filter);
    }

    protected T visitOther(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        return visitUnknown(filter.getTagName(), filter, scope);
    }

    protected T visitFixedValue(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        if (filterOp == FilterOp.ALWAYS_TRUE)
            return visitAlwaysTrue(filter, scope);
        if (filterOp == FilterOp.ALWAYS_FALSE)
            return visitAlwaysFalse(filter, scope);
        return visitUnknown(filterOp.name(), filter, scope);
    }

    public T visitAlwaysTrue(ITreeBean filter, IVariableScope scope) {
        return visitUnknown(FILTER_OP_ALWAYS_TRUE, filter, scope);
    }

    public T visitAlwaysFalse(ITreeBean filter, IVariableScope scope) {
        return visitUnknown(FILTER_OP_ALWAYS_FALSE, filter, scope);
    }

    protected T visitCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        return visitUnknown(filterOp.name(), filter, scope);
    }

    protected T visitAssertOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        return visitUnknown(filterOp.name(), filter, scope);
    }

    protected T visitBetweenOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        return visitUnknown(filterOp.name(), filter, scope);
    }

    public T visitAnd(ITreeBean filter, IVariableScope scope) {
        return visitGroup(filter, scope);
    }

    public T visitGroup(ITreeBean filter, IVariableScope scope) {
        return visitChildren(filter, scope);
    }

    protected T visitChildren(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children != null) {
            T result = null;
            for (ITreeBean child : children) {
                result = visit(child, scope);
            }
            return result;
        }
        return null;
    }

    public T visitOr(ITreeBean filter, IVariableScope scope) {
        return visitGroup(filter, scope);
    }

    public T visitNot(ITreeBean filter, IVariableScope scope) {
        return visitGroup(filter, scope);
    }

    protected String getOpText(FilterOp filterOp) {
        return defaults(filterOp.getMathSymbol(), filterOp.name());
    }

    protected String getName(ITreeBean filter) {
        String name = getNameAttr(filter, FILTER_ATTR_NAME);
        if (StringHelper.isEmpty(name))
            throw new NopException(ERR_FILTER_NO_NAME_ARG).param(ARG_OP, filter.getTagName()).source(filter);
        return name;
    }

    protected void setName(ITreeBean filter, String name) {
        filter.setAttr(FILTER_ATTR_NAME, name);
    }

    protected Object getValue(ITreeBean filter) {
        return normalizeValue(filter.getLocation(), FILTER_ATTR_VALUE, filter.getAttr(FILTER_ATTR_VALUE));
    }

    protected void setValue(ITreeBean filter, Object value) {
        filter.setAttr(FILTER_ATTR_VALUE, value);
    }

    protected Object getMin(ITreeBean filter) {
        Object min = filter.getAttr(FILTER_ATTR_MIN);
        return normalizeValue(filter.getLocation(), FILTER_ATTR_MIN, min);
    }

    protected Object getMax(ITreeBean filter) {
        Object max = filter.getAttr(FILTER_ATTR_MAX);
        return normalizeValue(filter.getLocation(), FILTER_ATTR_MAX, max);
    }

    protected Object normalizeValue(SourceLocation loc, String name, Object value) {
        if (value instanceof String) {
            String str = value.toString();
            if (str.startsWith(CoreConstants.ATTR_EXPR_PREFIX)) {
                str = str.substring(CoreConstants.ATTR_EXPR_PREFIX.length()).trim();
                return JsonTool.parseNonStrict(str);
            }
        }
        return value;
    }

    protected String getMinName(ITreeBean filter) {
        return getNameAttr(filter, FILTER_ATTR_MIN_NAME);
    }

    protected String getMaxName(ITreeBean filter) {
        return getNameAttr(filter, FILTER_ATTR_MAX_NAME);
    }

    protected String getNameAttr(ITreeBean filter, String attrName) {
        String name = ConvertHelper.toString(filter.getAttr(attrName));
        return StringHelper.emptyAsNull(name);
    }

    protected boolean isExcludeMin(ITreeBean filter) {
        return ConvertHelper.toPrimitiveBoolean(filter.getAttr(FILTER_ATTR_EXCLUDE_MIN), NopException::new);
    }

    protected boolean isExcludeMax(ITreeBean filter) {
        return ConvertHelper.toPrimitiveBoolean(filter.getAttr(FILTER_ATTR_EXCLUDE_MIN), NopException::new);
    }

    protected String getValueName(ITreeBean filter) {
        return getNameAttr(filter, FILTER_ATTR_VALUE_NAME);
    }

    protected String getOwner(ITreeBean filter, String defaultOwner) {
        String name = ConvertHelper.toString(filter.getAttr(FILTER_ATTR_OWNER));
        if (StringHelper.isEmpty(name))
            return defaultOwner;
        return name;
    }

    protected String getValueOwner(ITreeBean filter, String defaultOwner) {
        String name = ConvertHelper.toString(filter.getAttr(FILTER_ATTR_VALUE_OWNER));
        if (StringHelper.isEmpty(name))
            return defaultOwner;
        return name;
    }
}