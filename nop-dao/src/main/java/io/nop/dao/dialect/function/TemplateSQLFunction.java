/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.function;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.marker.Markers;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.core.lang.sql.StringSqlExpr;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.model.SqlTemplateModel;

import java.util.ArrayList;
import java.util.List;

import static io.nop.dao.DaoErrors.ARG_ARG_COUNT;
import static io.nop.dao.DaoErrors.ARG_FUNC_NAME;
import static io.nop.dao.DaoErrors.ERR_DAO_FUNC_INVALID_ARG_COUNT;

public class TemplateSQLFunction implements ISQLFunction {
    private final SqlTemplateModel funcModel;
    private final List<StdSqlType> argTypes;
    private final String source;
    private final List<Markers.NameMarker> markers;

    public TemplateSQLFunction(SqlTemplateModel funcModel) {
        this.funcModel = funcModel;
        this.argTypes = CollectionHelper.toNotNull(funcModel.getArgTypes());

        this.markers = new ArrayList<>();
        this.source = funcModel.getSource().trim();
        int pos = 0;
        do {
            int pos1 = source.indexOf("{", pos);
            if (pos1 < 0)
                break;
            int pos2 = source.indexOf('}', pos1);
            if (pos2 < 0)
                break;

            String name = source.substring(pos1 + 1, pos2);
            if (StringHelper.isAllDigit(name)) {
                markers.add(new Markers.NameMarker(pos1, pos2 + 1, name));
            }
            pos = pos2;
        } while (true);
    }

    @Override
    public String getName() {
        return funcModel.getName();
    }

    @Override
    public int getMinArgCount() {
        return argTypes.size();
    }

    @Override
    public int getMaxArgCount() {
        return argTypes.size();
    }

    @Override
    public boolean hasParentheses() {
        return true;
    }

    @Override
    public boolean isOnlyForWindowExpr() {
        return funcModel.isOnlyForWindowExpr();
    }

    @Override
    public List<StdSqlType> getArgTypes() {
        return argTypes;
    }

    @Override
    public StdSqlType getReturnType(List<? extends ISqlExpr> argExprs, IDialect dialect) {
        return funcModel.getReturnType();
    }

    @Override
    public StdSqlType getArgType(List<? extends ISqlExpr> argExprs, int argIndex, IDialect dialect) {
        if (argIndex >= argTypes.size())
            return StdSqlType.ANY;
        return argTypes.get(argIndex);
    }

    @Override
    public SqlExprList buildFunctionExpr(SourceLocation loc, List<? extends ISqlExpr> argExprs, IDialect dialect) {
        if (argExprs.size() != argTypes.size())
            throw new NopException(ERR_DAO_FUNC_INVALID_ARG_COUNT).loc(loc).param(ARG_FUNC_NAME, getName())
                    .param(ARG_ARG_COUNT, argTypes.size());

        if (markers.size() == 0) {
            return new SqlExprList(getName(), getReturnType(argExprs, dialect), StringSqlExpr.makeExpr(source));
        }

        SqlExprList ret = new SqlExprList(getName(), getReturnType(argExprs, dialect));
        int pos = 0;
        for (Markers.NameMarker marker : markers) {
            int begin = marker.getBegin();
            if (begin > pos) {
                ret.add(StringSqlExpr.makeExpr(source.substring(pos, begin)));
            }
            int index = ConvertHelper.stringToInt(marker.getName(), NopException::new);
            if (index >= argExprs.size()) {
                ret.add(StringSqlExpr.makeExpr(" null "));
            } else {
                ret.add(argExprs.get(index));
            }
            pos = marker.getEnd();
        }

        if (pos < source.length()) {
            ret.add(StringSqlExpr.makeExpr(source.substring(pos)));
        }
        return ret;
    }
}
