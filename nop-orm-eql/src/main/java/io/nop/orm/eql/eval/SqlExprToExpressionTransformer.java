package io.nop.orm.eql.eval;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.DateHelper;
import io.nop.core.model.query.FilterOp;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBetweenExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlBitValueLiteral;
import io.nop.orm.eql.ast.SqlBooleanLiteral;
import io.nop.orm.eql.ast.SqlCastExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlDateTimeLiteral;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlHexadecimalLiteral;
import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.SqlIsNullExpr;
import io.nop.orm.eql.ast.SqlLikeExpr;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlOrExpr;
import io.nop.orm.eql.ast.SqlParameterMarker;
import io.nop.orm.eql.ast.SqlQualifiedName;
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlUnaryExpr;
import io.nop.orm.eql.enums.SqlDateTimeType;
import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.BetweenOpExpression;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.CastExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.InExpression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.TypeNameNode;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangOperator;

import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.eql.OrmEqlErrors.ARG_EXPR;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNSUPPORTED_EVAL_EXPR;

/**
 * 将SQL表达式转换为XLang表达式
 */
public class SqlExprToExpressionTransformer {
    private int paramIndex;

    public Expression transform(SqlExpr expr) {
        if (expr == null)
            return null;

        switch (expr.getASTKind()) {
            case SqlAndExpr:
                return transformAnd((SqlAndExpr) expr);
            case SqlOrExpr:
                return transformOr((SqlOrExpr) expr);
            case SqlNotExpr:
                return transformNot((SqlNotExpr) expr);
            case SqlBinaryExpr:
                return transformBinary((SqlBinaryExpr) expr);
            case SqlLikeExpr:
                return transformLike((SqlLikeExpr) expr);
            case SqlUnaryExpr:
                return transformUnary((SqlUnaryExpr) expr);
            case SqlIsNullExpr:
                return transformIsNull((SqlIsNullExpr) expr);
            case SqlStringLiteral:
                return transformStringLiteral((SqlStringLiteral) expr);
            case SqlNumberLiteral:
                return transformNumber((SqlNumberLiteral) expr);
            case SqlBooleanLiteral:
                return transformBoolean((SqlBooleanLiteral) expr);
            case SqlNullLiteral:
                return Literal.nullValue(expr.getLocation());
            case SqlBitValueLiteral:
                return transformBitValue((SqlBitValueLiteral) expr);
            case SqlHexadecimalLiteral:
                return transformHexLiteral((SqlHexadecimalLiteral) expr);
            case SqlDateTimeLiteral:
                return transformDateTime((SqlDateTimeLiteral) expr);
            case SqlBetweenExpr:
                return transformBetween((SqlBetweenExpr) expr);
            case SqlCastExpr:
                return transformCast((SqlCastExpr) expr);
            case SqlInValuesExpr:
                return transformInValues((SqlInValuesExpr) expr);
            case SqlParameterMarker:
                return transformParamMarker((SqlParameterMarker) expr);
            case SqlColumnName:
                return transformColumnName((SqlColumnName) expr);
            case SqlRegularFunction:
                return transformRegularFunction((SqlRegularFunction) expr);
        }
        throw new NopException(ERR_EQL_UNSUPPORTED_EVAL_EXPR)
                .param(ARG_EXPR, expr);
    }

    private Expression transformAnd(SqlAndExpr expr) {
        return LogicalExpression.valueOf(expr.getLocation(), XLangOperator.AND,
                transform(expr.getLeft()), transform(expr.getRight()));
    }

    private Expression transformOr(SqlOrExpr expr) {
        return LogicalExpression.valueOf(expr.getLocation(), XLangOperator.OR,
                transform(expr.getLeft()), transform(expr.getRight()));
    }

    private Expression transformNot(SqlNotExpr expr) {
        return UnaryExpression.valueOf(expr.getLocation(), XLangOperator.NOT,
                transform(expr.getExpr()));
    }

    private Expression transformBinary(SqlBinaryExpr expr) {
        Expression left = transform(expr.getLeft());
        Expression right = transform(expr.getRight());

        switch (expr.getOperator()) {
            case LIKE:
                return CompareOpExpression.valueOf(expr.getLocation(), left, FilterOp.LIKE, right);
//            case ILIKE:
//            case RLIKE:
//                return CompareOpExpression.valueOf(expr.getLocation(), left, FilterOp.RLIKE, right);
            default:
                return BinaryExpression.valueOf(expr.getLocation(), left,
                        SqlExprTransformHelper.toXLangOperator(expr.getOperator()),
                        right);
        }
    }

    private Expression transformLike(SqlLikeExpr expr) {
        return CompareOpExpression.valueOf(expr.getLocation(), transform(expr.getExpr()), FilterOp.LIKE,
                transform(expr.getValue()));
    }

    private Expression transformUnary(SqlUnaryExpr expr) {
        XLangOperator op = SqlExprTransformHelper.toXLangOperator(expr.getOperator());
        return UnaryExpression.valueOf(expr.getLocation(), op, transform(expr.getExpr()));
    }

    private Expression transformIsNull(SqlIsNullExpr expr) {
        return BinaryExpression.valueOf(expr.getLocation(), transform(expr.getExpr()),
                XLangOperator.EQ, Literal.nullValue(expr.getLocation()));
    }

    private Expression transformStringLiteral(SqlStringLiteral expr) {
        return Literal.stringValue(expr.getLocation(), expr.getValue());
    }

    private Expression transformNumber(SqlNumberLiteral expr) {
        return Literal.numberValue(expr.getLocation(), expr.getNumberValue());
    }

    private Expression transformBoolean(SqlBooleanLiteral expr) {
        return Literal.booleanValue(expr.getLocation(), expr.getValue());
    }

    private Expression transformBitValue(SqlBitValueLiteral expr) {
        String str = expr.getValue();
        if (str.startsWith("0b")) {
            str = str.substring(2);
        } else if (str.startsWith("B")) {
            str = str.substring(1);
        } else {
            throw new IllegalArgumentException("invalid bit value:" + expr.getValue());
        }
        int value = Integer.parseInt(str, 2);
        return Literal.numberValue(expr.getLocation(), value);
    }

    private Expression transformHexLiteral(SqlHexadecimalLiteral expr) {
        String str = expr.getValue();
        if (str.startsWith("0x")) {
            str = str.substring(2);
        } else if (str.startsWith("X")) {
            str = str.substring(1);
        } else {
            throw new IllegalArgumentException("invalid hex value；" + expr.getValue());
        }
        int value = Integer.decode(str);
        return Literal.numberValue(expr.getLocation(), value);
    }

    private Expression transformDateTime(SqlDateTimeLiteral expr) {
        if (expr.getType() == SqlDateTimeType.DATE) {
            return Literal.valueOf(expr.getLocation(), DateHelper.parseDate(expr.getValue()));
        } else if (expr.getType() == SqlDateTimeType.TIME) {
            return Literal.valueOf(expr.getLocation(), DateHelper.parseTime(expr.getValue()));
        } else {
            return Literal.valueOf(expr.getLocation(), ConvertHelper.stringToTimestamp(expr.getValue(), NopException::new));
        }
    }

    private Expression transformBetween(SqlBetweenExpr expr) {
        Expression value = transform(expr.getTest());
        Expression min = transform(expr.getBegin());
        Expression max = transform(expr.getEnd());

        Expression ret = BetweenOpExpression.valueOf(expr.getLocation(), value, FilterOp.BETWEEN.name(),
                min, max, false, false);

        if (expr.getNot()) {
            ret = UnaryExpression.not(expr.getLocation(), ret);
        }
        return ret;
    }

    private Expression transformCast(SqlCastExpr expr) {
        StdSqlType sqlType = SqlExprTransformHelper.getSqlType(expr.getDataType());
        if (sqlType == null)
            sqlType = StdSqlType.VARCHAR;
        Expression value = transform(expr.getExpr());
        TypeNameNode typeNode = TypeNameNode.fromType(expr.getLocation(), sqlType.getStdDataType().getJavaClass());
        return CastExpression.valueOf(expr.getLocation(), value, typeNode);
    }

    private Expression transformInValues(SqlInValuesExpr expr) {
        Expression value = transform(expr.getExpr());
        Expression list = toListExpr(expr.getLocation(), expr.getValues());
        return InExpression.valueOf(expr.getLocation(), value, list);
    }

    private Expression toListExpr(SourceLocation loc, List<SqlExpr> exprs) {
        List<XLangASTNode> list = new ArrayList<>(exprs.size());
        for (SqlExpr expr : exprs) {
            list.add(transform(expr));
        }
        return ArrayExpression.valueOf(loc, list);
    }

    private Expression transformParamMarker(SqlParameterMarker expr) {
        int index = this.paramIndex;
        this.paramIndex++;
        Identifier obj = Identifier.implicitVar(expr.getLocation(), OrmEqlConstants.VAR_PARAMS);
        Literal prop = Literal.numberValue(expr.getLocation(), index);
        return MemberExpression.valueOf(expr.getLocation(), obj, prop, true);
    }

    private Expression transformColumnName(SqlColumnName colName) {
        SqlQualifiedName owner = colName.getOwner();
        if (owner == null) {
            owner = SqlQualifiedName.valueOf(colName.getLocation(), OrmEqlConstants.VAR_O, null);
        }
        Expression obj = transformQualifiedName(null, owner);
        Identifier prop = Identifier.valueOf(colName.getLocation(), colName.getName());
        return MemberExpression.valueOf(colName.getLocation(), obj, prop, false);
    }

    private Expression transformQualifiedName(Expression parent, SqlQualifiedName name) {
        Expression expr = makeExpr(parent, name.getLocation(), name.getName());
        if (name.getNext() == null)
            return expr;

        return transformQualifiedName(expr, name.getNext());
    }

    private Expression makeExpr(Expression parent, SourceLocation loc, String name) {
        Identifier prop = Identifier.valueOf(loc, name);
        if (parent == null)
            return prop;
        return MemberExpression.valueOf(loc, parent, prop, false);
    }

    private Expression transformRegularFunction(SqlRegularFunction func) {
        Identifier callee = Identifier.valueOf(func.getLocation(), func.getName());
        List<Expression> args = new ArrayList<>();
        if (func.getArgs() != null) {
            for (SqlExpr exp : func.getArgs()) {
                args.add(transform(exp));
            }
        }
        return CallExpression.valueOf(func.getLocation(), callee, args);
    }
}
