package io.nop.xlang.expr.filter;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.model.query.FilterBeanFormatter;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.simple.SimpleExprParser;

public class FilterExprHelper {
    public static TreeBean parseFilterExpr(SourceLocation loc, String filterExpr) {
        Expression expr = SimpleExprParser.newFilterExprParser().parseExpr(loc, filterExpr);
        return new ExpressionToFilterBeanTransformer().transform(expr);
    }

    public static String buildFilterExpr(ITreeBean filter) {
        return new FilterBeanFormatter(name -> name).useFunctionCall(true).format(filter);
    }
}
