/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.parse;

import io.nop.antlr4.common.ParseTreeHelper;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.eql.enums.SqlCompareRange;
import io.nop.orm.eql.enums.SqlDateTimeType;
import io.nop.orm.eql.enums.SqlIntervalUnit;
import io.nop.orm.eql.enums.SqlJoinType;
import io.nop.orm.eql.enums.SqlUnionType;
import io.nop.orm.eql.parse.antlr.EqlParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import static io.nop.antlr4.common.ParseTreeHelper.isToken;
import static io.nop.antlr4.common.ParseTreeHelper.loc;
import static io.nop.antlr4.common.ParseTreeHelper.text;
import static io.nop.antlr4.common.ParseTreeHelper.token;
import static io.nop.orm.eql.OrmEqlErrors.ARG_ALLOWED_NAMES;
import static io.nop.orm.eql.OrmEqlErrors.ARG_SQL_TYPE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_VALUE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_DATETIME_TYPE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_INTERVAL_UNIT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_SQL_TYPE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_PRECISION_NOT_POSITIVE_INT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_SCALE_NOT_NON_NEGATIVE_INT;
import static io.nop.orm.eql.parse.EqlParseHelper.operator;

@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class EqlASTBuildVisitor extends _EqlASTBuildVisitor {

    /**
     * rules: sqlAggregateFunction
     */
    public boolean SqlAggregateFunction_distinct(ParseTree node) {
        return isToken(token(node), EqlParser.DISTINCT);
    }

    /**
     * rules: sqlAggregateFunction
     */
    public boolean SqlAggregateFunction_selectAll(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.ASTERISK_);
    }

    /**
     * rules: SqlBetweenExpr
     */
    public boolean SqlBetweenExpr_not(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.NOT);
    }

    /**
     * rules: sqlBooleanLiteral
     */
    public boolean SqlBooleanLiteral_value(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.TRUE);
    }

    @Override
    public boolean SqlSubqueryTableSource_lateral(Token token) {
        return isToken(token, EqlParser.LATERAL);
    }

    @Override
    public SqlCompareRange SqlCompareWithQueryExpr_compareRange(Token token) {
        if (token == null)
            return null;

        if (isToken(token, EqlParser.ALL))
            return SqlCompareRange.ALL;
        return SqlCompareRange.ANY;
    }

    /**
     * rules: SqlInQueryExpr
     */
    public boolean SqlInQueryExpr_not(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.NOT);
    }

    /**
     * rules: SqlInValuesExpr
     */
    public boolean SqlInValuesExpr_not(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.NOT);
    }

    /**
     * rules: SqlIsNullExpr
     */
    public boolean SqlIsNullExpr_not(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.NOT);
    }

    /**
     * rules: SqlLikeExpr
     */
    public boolean SqlLikeExpr_not(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.NOT);
    }

    /**
     * rules: sqlOrderByItem
     */
    public boolean SqlOrderByItem_asc(org.antlr.v4.runtime.Token token) {
        if (isToken(token, EqlParser.DESC))
            return false;
        return true;
    }

    /**
     * rules: sqlQuerySelect
     */
    public boolean SqlQuerySelect_distinct(ParseTree node) {
        return isToken(token(node), EqlParser.DISTINCT);
    }

    /**
     * rules: sqlQuerySelect
     */
    public boolean SqlQuerySelect_forUpdate(ParseTree node) {
        return node != null;
    }

    /**
     * rules: sqlQuerySelect
     */
    public boolean SqlQuerySelect_selectAll(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.ASTERISK_);
    }

    /**
     * rules: sqlTypeExpr
     */
    public int SqlTypeExpr_precision(org.antlr.v4.runtime.Token token) {
        int value = ConvertHelper.toPrimitiveInt(text(token),
                err -> new NopEvalException(ERR_EQL_PRECISION_NOT_POSITIVE_INT).loc(loc(token)));
        if (value <= 0)
            throw new NopEvalException(ERR_EQL_PRECISION_NOT_POSITIVE_INT).loc(loc(token)).param(ARG_VALUE, value);
        return value;
    }

    /**
     * rules: sqlTypeExpr
     */
    public int SqlTypeExpr_scale(org.antlr.v4.runtime.Token token) {
        int value = ConvertHelper.toPrimitiveInt(text(token),
                err -> new NopEvalException(ERR_EQL_SCALE_NOT_NON_NEGATIVE_INT).loc(loc(token)));
        if (value < 0)
            throw new NopEvalException(ERR_EQL_SCALE_NOT_NON_NEGATIVE_INT).loc(loc(token)).param(ARG_VALUE, value);
        return value;
    }

    /**
     * rules: sqlDateTimeLiteral
     */
    public io.nop.orm.eql.enums.SqlDateTimeType SqlDateTimeLiteral_type(Token node) {
        String value = text(node);
        SqlDateTimeType dateTimeType = SqlDateTimeType.fromText(value);
        if (dateTimeType == null)
            throw new NopEvalException(ERR_EQL_INVALID_DATETIME_TYPE).loc(loc((ParserRuleContext) node))
                    .param(ARG_VALUE, value);
        return dateTimeType;
    }

    /**
     * rules: sqlIntervalExpr
     */
    public io.nop.orm.eql.enums.SqlIntervalUnit SqlIntervalExpr_intervalUnit(ParseTree node) {
        String text = text(node);
        SqlIntervalUnit unit = SqlIntervalUnit.fromText(text);
        if (unit == null)
            throw new NopEvalException(ERR_EQL_INVALID_INTERVAL_UNIT).loc(loc((ParserRuleContext) node))
                    .param(ARG_VALUE, text);
        return unit;
    }

    /**
     * rules: SqlBinaryExpr_compare
     */
    public io.nop.orm.eql.enums.SqlOperator SqlBinaryExpr_operator(ParseTree node) {
        return operator(token(node));
    }

    /**
     * rules: SqlBinaryExpr
     */
    public io.nop.orm.eql.enums.SqlOperator SqlBinaryExpr_operator(org.antlr.v4.runtime.Token token) {
        return operator(token);
    }

    /**
     * rules: SqlCompareWithQueryExpr
     */
    public io.nop.orm.eql.enums.SqlOperator SqlCompareWithQueryExpr_operator(ParseTree node) {
        return operator(token(node));
    }

    /**
     * rules: sqlUnaryExpr
     */
    public io.nop.orm.eql.enums.SqlOperator SqlUnaryExpr_operator(org.antlr.v4.runtime.Token token) {
        return operator(token);
    }

    /**
     * rules: sqlNumberLiteral
     */
    public String SqlNumberLiteral_value(org.antlr.v4.runtime.Token token) {
        return EqlParseHelper.numberLiteralValue(token);
    }

    /**
     * rules: sqlAlias
     */
    public java.lang.String SqlAlias_alias(ParseTree node) {
        String text = ParseTreeHelper.text(node);
        if (text.startsWith("'"))
            return StringHelper.unescapeJava(text.substring(1, text.length() - 1));
        return text;
    }

    /**
     * rules: sqlDateTimeLiteral
     */
    public java.lang.String SqlDateTimeLiteral_value(org.antlr.v4.runtime.Token token) {
        String text = text(token);
        return text.substring(1, text.length() - 1);
    }

    /**
     * rules: sqlStringLiteral
     */
    public java.lang.String SqlStringLiteral_value(org.antlr.v4.runtime.Token token) {
        return EqlParseHelper.stringLiteralValue(token);
    }

    /**
     * rules: sqlTypeExpr
     */
    public java.lang.String SqlTypeExpr_name(ParseTree node) {
        String name = ParseTreeHelper.text(node);
        if (StdSqlType.fromStdName(name) == null)
            throw new NopException(ERR_EQL_INVALID_SQL_TYPE).loc(ParseTreeHelper.loc(node)).param(ARG_SQL_TYPE, name)
                    .param(ARG_ALLOWED_NAMES, StdSqlType.getNames());
        return name;
    }

    /**
     * rules: sqlBitValueLiteral
     */
    public String SqlBitValueLiteral_value(org.antlr.v4.runtime.Token token) {
        return EqlParseHelper.bitLiteralValue(token);
    }

    /**
     * rules: sqlHexadecimalLiteral
     */
    public String SqlHexadecimalLiteral_value(org.antlr.v4.runtime.Token token) {
        return EqlParseHelper.hexLiteralValue(token);
    }

    @Override
    public SqlJoinType SqlJoinTableSource_joinType(ParseTree node) {
        EqlParser.JoinType_Context ctx = (EqlParser.JoinType_Context) node;
        if (ctx.leftJoin_() != null)
            return SqlJoinType.LEFT_JOIN;
        if (ctx.fullJoin_() != null)
            return SqlJoinType.FULL_JOIN;
        if (ctx.rightJoin_() != null)
            return SqlJoinType.RIGHT_JOIN;
        return SqlJoinType.JOIN;
    }

    @Override
    public SqlUnionType SqlUnionSelect_unionType(ParseTree node) {
        EqlParser.UnionType_Context ctx = (EqlParser.UnionType_Context) node;
        if (ctx.ALL() != null) {
            return SqlUnionType.UNION_ALL;
        } else {
            return SqlUnionType.UNION;
        }
    }

    @Override
    public String SqlTypeExpr_characterSet(ParseTree node) {
        EqlParser.CharacterSet_Context ctx = (EqlParser.CharacterSet_Context) node;
        return text(ctx.characterSet);
    }

    @Override
    public String SqlTypeExpr_collate(ParseTree node) {
        EqlParser.CollateClause_Context ctx = (EqlParser.CollateClause_Context) node;
        return text(ctx.collate);
    }

    @Override
    public String SqlAggregateFunction_name(ParseTree node) {
        return text(node).toLowerCase();
    }

    @Override
    public String SqlColumnName_name(ParseTree node) {
        return text(node);
    }

    @Override
    public String SqlCteStatement_name(ParseTree node) {
        return text(node);
    }

    @Override
    public String SqlDecorator_name(ParseTree node) {
        return text(node);
    }

    @Override
    public String SqlQualifiedName_name(ParseTree node) {
        return text(node);
    }

    @Override
    public String SqlRegularFunction_name(ParseTree node) {
        return text(node).toLowerCase();
    }

    @Override
    public String SqlTableName_name(ParseTree node) {
        return text(node);
    }

    @Override
    public boolean SqlLikeExpr_ignoreCase(Token token) {
        return true;
    }
}