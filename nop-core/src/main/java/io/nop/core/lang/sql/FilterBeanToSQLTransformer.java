/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.sql;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.commons.text.marker.IMarkedString;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.model.query.FilterBeanVisitor;
import io.nop.core.model.query.FilterOp;

import java.time.LocalDate;
import java.util.List;

import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_CONTAINS;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_DATE_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_ENDS_WITH;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_EQ;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_GE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_GT;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_ICONTAINS;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_IN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_IS_EMPTY;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_IS_FALSE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_IS_NULL;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_IS_TRUE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_LE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_LIKE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_LT;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_EMPTY;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_FALSE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_IN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_NULL;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_TRUE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_STARTS_WITH;
import static io.nop.core.CoreErrors.ARG_NAME;
import static io.nop.core.CoreErrors.ARG_OP;
import static io.nop.core.CoreErrors.ARG_VALUE;
import static io.nop.core.CoreErrors.ERR_FILTER_SQL_OP_INVALID_VALUE_TYPE;
import static io.nop.core.CoreErrors.ERR_FILTER_UNKNOWN_OP;
import static io.nop.core.CoreErrors.ERR_SQL_FILTER_INVALID_FIELD_NAME;

public class FilterBeanToSQLTransformer extends FilterBeanVisitor<Void> {
    private final SQL.SqlBuilder sb;
    private final boolean checkVarName;
    private final String defaultOwner;

    public FilterBeanToSQLTransformer(SQL.SqlBuilder sb, boolean checkVarName, String defaultOwner) {
        this.sb = sb;
        this.checkVarName = checkVarName;
        this.defaultOwner = defaultOwner;
    }

    public FilterBeanToSQLTransformer(SQL.SqlBuilder sb) {
        this(sb, true, "o");
    }

    public FilterBeanToSQLTransformer() {
        this(SQL.begin(), true, "o");
    }

    public SQL getResult() {
        return sb.end();
    }

    protected void validateVarName(String name, ITreeBean filter, IVariableScope scope) {
        if (checkVarName && name != null && !StringHelper.isValidPropPath(name))
            throw new NopException(ERR_SQL_FILTER_INVALID_FIELD_NAME).param(ARG_NAME, name);
    }

    @Override
    protected Void visitCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String owner = getOwner(filter, defaultOwner);
        validateVarName(owner, filter, scope);
        String name = getName(filter);
        validateVarName(name, filter, scope);

        String valueName = getValueName(filter);
        if (valueName != null) {
            validateVarName(valueName, filter, scope);
        }

        String op = filterOp.name();

        // 对于空字符串和null，op=eq/ne的时候会转换为is null和is not null判断，其他情况下总是false

        Object value = getValue(filter);
        if (value == null) {
            if (FILTER_OP_EQ.equals(op)) {
                sb.owner(owner).eqEx(name, value);
            } else if (FILTER_OP_NE.equals(op)) {
                sb.owner(owner).notEqEx(name, value);
            } else {
                sb.alwaysFalse();
            }
            return null;
        }

        if (op.equals(FILTER_OP_EQ)) {
            // 如果是空字符串，实际生成的sql是name is null。也就是说，平台缺省情况下认为数据保存到数据库中空字符串会自动被转换为null，
            // 这样约定是为了避免数据库移植的时候出现不一致性。
            sb.owner(owner).eqEx(name, value);
        } else if (op.equals(FILTER_OP_NE)) {
            sb.owner(owner).notEqEx(name, value);
        } else if (op.equals(FILTER_OP_GT)) {
            sb.owner(owner).gt(name, value);
        } else if (op.equals(FILTER_OP_GE)) {
            sb.owner(owner).ge(name, value);
        } else if (op.equals(FILTER_OP_LT)) {
            sb.owner(owner).lt(name, value);
        } else if (op.equals(FILTER_OP_LE)) {
            sb.owner(owner).le(name, value);
        } else if (op.equals(FILTER_OP_IN)) {
            sb.owner(owner).in(name, value);
        } else if (op.equals(FILTER_OP_NOT_IN)) {
            sb.owner(owner).not().in(name, value);
        } else if (op.equals(FILTER_OP_STARTS_WITH)) {
            sb.owner(owner).sql(name).sql(" like ").param(value + "%");
        } else if (op.equals(FILTER_OP_ENDS_WITH)) {
            sb.owner(owner).sql(name).sql(" like ").param("%" + value);
        } else if (op.equals(FILTER_OP_CONTAINS)) {
            sb.owner(owner).sql(name).sql(" like ").param("%" + value + "%");
        } else if (op.equals(FILTER_OP_LIKE)) {
            sb.owner(owner).sql(name).sql(" like ").param(value);
        } else if (op.equals(FILTER_OP_ICONTAINS)) {
            sb.owner(owner).sql(name).sql(" ilike ").param("%" + value + "%");
        } else {
            visitUnknown(op, filter, scope);
        }
        return null;
    }

    public Void visitAlwaysTrue(ITreeBean filter, IVariableScope scope) {
        sb.alwaysTrue();
        return null;
    }

    public Void visitAlwaysFalse(ITreeBean filter, IVariableScope scope) {
        sb.alwaysFalse();
        return null;
    }

    @Override
    protected Void visitAssertOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String owner = getOwner(filter, defaultOwner);
        validateVarName(owner, filter, scope);
        String name = getName(filter);
        validateVarName(name, filter, scope);

        String op = filterOp.name();
        if (op.equals(FILTER_OP_IS_NULL)) {
            sb.owner(owner).isNull(name);
        } else if (op.equals(FILTER_OP_NOT_NULL)) {
            sb.owner(owner).notNull(name);
        } else if (op.equals(FILTER_OP_IS_EMPTY)) {
            sb.owner(owner).isEmpty(name);
        } else if (op.equals(FILTER_OP_NOT_EMPTY)) {
            sb.owner(name).notEmpty(name);
        } else if (op.equals(FILTER_OP_IS_TRUE)) {
            sb.owner(name).isTrue(name);
        } else if (op.equals(FILTER_OP_IS_FALSE)) {
            sb.owner(name).isFalse(name);
        } else if (op.equals(FILTER_OP_NOT_TRUE)) {
            sb.owner(name).notTrue(name);
        } else if (op.equals(FILTER_OP_NOT_FALSE)) {
            sb.owner(name).notFalse(name);
        } else {
            visitUnknown(op, filter, scope);
        }
        return null;
    }

    @Override
    protected Void visitBetweenOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String owner = getOwner(filter, defaultOwner);
        validateVarName(owner, filter, scope);
        String name = getName(filter);
        validateVarName(name, filter, scope);

        boolean excludeMin = isExcludeMin(filter);
        boolean excludeMax = isExcludeMax(filter);

        Object min = getMin(filter);
        Object max = getMax(filter);

        String op = filterOp.name();
        if (op.equals(FILTER_OP_BETWEEN)) {
            sb.owner(owner).between(name, min, max, excludeMin, excludeMax);
        } else if (op.equals(FILTER_OP_DATE_BETWEEN)) {
            min = ConvertHelper.toLocalDate(min, NopException::new);
            max = ConvertHelper.toLocalDate(max, NopException::new);
            if (max != null && !excludeMax) {
                LocalDate d = (LocalDate) max;
                max = d.plusDays(1);
                excludeMax = true;
            }
            sb.owner(owner).between(name, min, max, excludeMin, excludeMax);
        } else {
            visitUnknown(op, filter, scope);
        }
        return null;
    }

    @Override
    public Void visitAnd(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty()) {
            sb.alwaysTrue();
            return null;
        }

        for (int i = 0, n = filter.getChildCount(); i < n; i++) {
            ITreeBean child = filter.getChildren().get(i);
            boolean hasChild = child.getChildCount() > 0;
            if (hasChild) {
                sb.append('(');
                visit(child, scope);
                sb.append(')');
            } else {
                visit(child, scope);
            }
            if (i != n - 1)
                sb.and();
        }
        return null;
    }

    @Override
    public Void visitOr(ITreeBean filter, IVariableScope scope) {
        List<? extends ITreeBean> children = filter.getChildren();
        if (children == null || children.isEmpty()) {
            sb.alwaysFalse();
            return null;
        }

        sb.append('(');
        for (int i = 0, n = filter.getChildCount(); i < n; i++) {
            ITreeBean child = filter.getChildren().get(i);
            boolean hasChild = child.getChildCount() > 0;
            if (hasChild) {
                sb.append('(');
                visit(child, scope);
                sb.append(')');
            } else {
                visit(child, scope);
            }
            if (i != n - 1)
                sb.or();
        }
        sb.append(')');
        return null;
    }

    @Override
    public Void visitNot(ITreeBean filter, IVariableScope scope) {
        if (filter.getChildCount() <= 0) {
            sb.alwaysTrue();
            return null;
        }

        sb.not().append("(");
        for (int i = 0, n = filter.getChildCount(); i < n; i++) {
            ITreeBean child = filter.getChildren().get(i);
            visit(child, scope);
            if (i != n - 1)
                sb.and();
        }
        sb.append(")");
        return null;
    }

    public Void visitUnknown(String op, ITreeBean filter, IVariableScope scope) {
        if (op.equals(CoreConstants.FILTER_OP_SQL)) {
            Object value = getValue(filter);
            if (value == null) {
                sb.alwaysTrue();
                return null;
            }

            if (!(value instanceof IMarkedString)) {
                throw new NopException(ERR_FILTER_SQL_OP_INVALID_VALUE_TYPE).source(filter).param(ARG_VALUE, value);
            }

            IMarkedString sql = (IMarkedString) value;
            if (sql.isEmpty()) {
                sb.alwaysTrue();
                return null;
            }

            sb.append('(').append(sql).append(')');
            return null;
        }
        throw new NopException(ERR_FILTER_UNKNOWN_OP).param(ARG_OP, op).source(filter);
    }
}