package io.nop.orm.eql.eval;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlTypeExpr;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.parse.EqlExprASTParser;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangOperator;

public class SqlExprTransformHelper {
    public static SqlExpr parseSqlExpr(SourceLocation loc, String text) {
        return new EqlExprASTParser().parseFromText(loc, text);
    }

    public static Expression parseSqlToExpression(SourceLocation loc, String text) {
        SqlExpr sqlExpr = parseSqlExpr(loc, text);
        return new SqlExprToExpressionTransformer().transform(sqlExpr);
    }

    public static TreeBean parseSqlToFilter(SourceLocation loc, String text) {
        SqlExpr sqlExpr = parseSqlExpr(loc, text);
        return new SqlExprToFilterBeanTransformer().transform(sqlExpr);
    }

    public static XLangOperator toXLangOperator(SqlOperator op) {
        switch (op) {
            case BIT_XOR:
                return XLangOperator.BIT_XOR;
            case MULTIPLY:
                return XLangOperator.MULTIPLY;
            case DIVIDE:
                return XLangOperator.DIVIDE;
            case MOD:
                return XLangOperator.MOD;
            case ADD:
                return XLangOperator.ADD;
            case MINUS:
                return XLangOperator.MINUS;
            case BIT_LEFT_SHIFT:
                return XLangOperator.BIT_LEFT_SHIFT;
            case BIT_RIGHT_SHIFT:
                return XLangOperator.BIT_RIGHT_SHIFT;
            case BIT_AND:
                return XLangOperator.BIT_AND;
            case BIT_OR:
                return XLangOperator.BIT_OR;
            case LT:
                return XLangOperator.LT;
            case LE:
                return XLangOperator.LE;
            case EQ:
            case IS:
                return XLangOperator.EQ;
            case NE:
                return XLangOperator.NE;
            case GT:
                return XLangOperator.GT;
            case GE:
                return XLangOperator.GE;
            case BIT_NOT:
                return XLangOperator.BIT_NOT;
            case AND:
                return XLangOperator.AND;
            case OR:
                return XLangOperator.OR;
            case NOT:
                return XLangOperator.NOT;
            default:
                throw new IllegalArgumentException("unsupported-operator:" + op);
                //LIKE("like", 110), RLIKE("rlike", 110), ILIKE("ilike", 110);
        }
    }

    public static StdSqlType getSqlType(SqlTypeExpr expr) {
        return StdSqlType.fromStdName(expr.getName());
    }
}
