/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.antlr4.common.ParseTreeHelper;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlFunction;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlWindowFrame;
import io.nop.orm.eql.ast.SqlWindowFrameBound;
import io.nop.orm.eql.enums.SqlCompareRange;
import io.nop.orm.eql.enums.SqlDateTimeType;
import io.nop.orm.eql.enums.SqlIntervalUnit;
import io.nop.orm.eql.enums.SqlJoinType;
import io.nop.orm.eql.enums.SqlUnionType;
import io.nop.orm.eql.enums.SqlWindowFrameBoundType;
import io.nop.orm.eql.enums.SqlWindowFrameType;
import io.nop.orm.eql.parse.antlr.EqlParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;

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
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_WINDOW_FRAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_PRECISION_NOT_POSITIVE_INT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_SCALE_NOT_NON_NEGATIVE_INT;
import static io.nop.orm.eql.parse.EqlParseHelper.operator;

@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class EqlASTBuildVisitor extends _EqlASTBuildVisitor {

    /**
     * WI1 BUG-5: PG 形态 interval 字符串的归一化解析格式：单个数值 + 空白 + 单位词
     * （单位大小写不敏感）。多字段值（如 '1-2'）不匹配，落归一化层显式报错。
     */
    static final java.util.regex.Pattern INTERVAL_STRING_PATTERN = java.util.regex.Pattern
            .compile("(\\d+(?:\\.\\d+)?)\\s+([A-Za-z]+)");

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

    @Override
    public boolean SqlUpdate_returnAll(Token token) {
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
     * WI1 BUG-5: PG 形态 {@code interval '1 day'}（BaseRule.g4 sqlIntervalExpr 的
     * {@code INTERVAL STRING_} 备选分支）。构建期归一化为 value+unit 装入**既有**
     * {@link io.nop.orm.eql.ast.SqlIntervalExpr}（不新增 AST 节点、不改 EqlAST.xjava）；
     * 单位大小写不敏感。多字段值（如 '1-2'）与非法定义在归一化层显式拒绝，复用
     * {@code ERR_EQL_INVALID_INTERVAL_UNIT}；文法层失败（如 {@code '1-2' year to month}）
     * 走 antlr 错误码。生成方法体只建空节点 + validate()（必填校验必失败），因此完全
     * 重写、不调 super。逐方言模板 {@code sqlIntervalLiteral} 归 WI7b（唯一 owner）。
     */
    @Override
    public io.nop.orm.eql.ast.SqlIntervalExpr visitSqlIntervalExpr_string(
            EqlParser.SqlIntervalExpr_stringContext ctx) {
        String raw = EqlParseHelper.stringLiteralValue(ctx.STRING_().getSymbol());
        java.util.regex.Matcher m = INTERVAL_STRING_PATTERN.matcher(raw.trim());
        if (!m.matches())
            throw new NopException(ERR_EQL_INVALID_INTERVAL_UNIT).loc(loc(ctx)).param(ARG_VALUE, raw);

        // 单位大小写不敏感，且接受 PG 的复数形式（'3 hours' / '3 Hours' → HOUR）
        String unitText = m.group(2);
        SqlIntervalUnit unit = SqlIntervalUnit.fromText(unitText);
        if (unit == null && unitText.length() > 1
                && unitText.regionMatches(true, unitText.length() - 1, "s", 0, 1))
            unit = SqlIntervalUnit.fromText(unitText.substring(0, unitText.length() - 1));
        if (unit == null)
            throw new NopException(ERR_EQL_INVALID_INTERVAL_UNIT).loc(loc(ctx)).param(ARG_VALUE, raw);

        SqlNumberLiteral value = new SqlNumberLiteral();
        value.setLocation(ParseTreeHelper.loc(ctx));
        value.setValue(m.group(1));

        io.nop.orm.eql.ast.SqlIntervalExpr ret = new io.nop.orm.eql.ast.SqlIntervalExpr();
        ret.setLocation(ParseTreeHelper.loc(ctx));
        ret.setExpr(value);
        ret.setIntervalUnit(unit);
        ret.normalize();
        ret.validate();
        return ret;
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
        String name = text(node).toUpperCase(Locale.ROOT);
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
            if (ctx.INTERSECT() != null)
                return SqlUnionType.INTERSECT_ALL;
            if (ctx.EXCEPT() != null)
                return SqlUnionType.EXCEPT_ALL;
            return SqlUnionType.UNION_ALL;
        } else {
            if (ctx.INTERSECT() != null)
                return SqlUnionType.INTERSECT;
            if (ctx.EXCEPT() != null)
                return SqlUnionType.EXCEPT;
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
        return text(node).toLowerCase(Locale.ROOT);
    }

    @Override
    public String SqlTableName_name(ParseTree node) {
        return text(node);
    }

    // WI17: TUMBLE(t, INTERVAL) 伪表函数表源——t 的限定形式 表名.列名 拆为两个裸标识符
    @Override
    public String SqlTumbleTableSource_tableName(ParseTree node) {
        return text(node);
    }

    @Override
    public String SqlTumbleTableSource_timeColumn(ParseTree node) {
        return text(node);
    }

    @Override
    public boolean SqlLikeExpr_ignoreCase(Token token) {
        return true;
    }

    @Override
    public boolean SqlCteStatement_recursive(Token token) {
        return true;
    }

    @Override
    public SqlFunction SqlWindowExpr_function(ParseTree node) {
        return (SqlFunction) node.accept(this);
    }

    @Override
    public String SqlWindowExpr_windowName(ParseTree node) {
        return text(node);
    }

    @Override
    public String SqlWindowDecl_name(ParseTree node) {
        return text(node);
    }

    @Override
    public SqlWindowFrameType SqlWindowFrame_unit(ParseTree node) {
        EqlParser.SqlWindowFrameUnit_Context ctx = (EqlParser.SqlWindowFrameUnit_Context) node;
        if (ctx.ROWS() != null)
            return SqlWindowFrameType.ROWS;
        if (ctx.RANGE() != null)
            return SqlWindowFrameType.RANGE;
        if (ctx.GROUPS() != null)
            return SqlWindowFrameType.GROUPS;
        throw new NopEvalException(ERR_EQL_INVALID_WINDOW_FRAME).loc(loc(ctx)).param(ARG_VALUE, text(ctx));
    }

    @Override
    public SqlWindowFrameBoundType SqlWindowFrameBound_boundType(ParseTree node) {
        EqlParser.SqlWindowFrameBoundType_Context ctx = (EqlParser.SqlWindowFrameBoundType_Context) node;
        if (ctx.UNBOUNDED() != null)
            return ctx.PRECEDING() != null ? SqlWindowFrameBoundType.UNBOUNDED_PRECEDING
                    : SqlWindowFrameBoundType.UNBOUNDED_FOLLOWING;
        if (ctx.CURRENT() != null)
            return SqlWindowFrameBoundType.CURRENT_ROW;
        return ctx.PRECEDING() != null ? SqlWindowFrameBoundType.PRECEDING
                : SqlWindowFrameBoundType.FOLLOWING;
    }

    /**
     * `unbounded preceding|following` 在语法上会被可选的 offset=sqlExpr 先吃掉 unbounded 标识符
     * （sqlExpr 可经 unreservedWord_ 匹配任意非保留关键字），此处归一化为 UNBOUNDED_PRECEDING /
     * UNBOUNDED_FOLLOWING 并清空 offset。
     */
    @Override
    public SqlWindowFrameBound visitSqlWindowFrameBound(EqlParser.SqlWindowFrameBoundContext ctx) {
        SqlWindowFrameBound ret = super.visitSqlWindowFrameBound(ctx);
        if (ret.getBoundType() == SqlWindowFrameBoundType.PRECEDING
                || ret.getBoundType() == SqlWindowFrameBoundType.FOLLOWING) {
            SqlExpr offset = ret.getOffset();
            if (offset instanceof SqlColumnName) {
                String name = ((SqlColumnName) offset).getName();
                if ("unbounded".equalsIgnoreCase(name)) {
                    ret.setOffset(null);
                    ret.setBoundType(ret.getBoundType() == SqlWindowFrameBoundType.PRECEDING
                            ? SqlWindowFrameBoundType.UNBOUNDED_PRECEDING
                            : SqlWindowFrameBoundType.UNBOUNDED_FOLLOWING);
                }
            }
        }
        return ret;
    }

    /**
     * 标准要求 BETWEEN 与第二个 bound 成对出现；语法层 `BETWEEN?` 宽松接受以规避
     * 同一 prop 跨备选分支的生成器校验限制，这里显式 fail-fast 而非静默接受残缺形态。
     */
    @Override
    public SqlWindowFrame visitSqlWindowFrame(EqlParser.SqlWindowFrameContext ctx) {
        if (ctx.BETWEEN() == null && ctx.end != null) {
            throw new NopEvalException(ERR_EQL_INVALID_WINDOW_FRAME).loc(loc(ctx))
                    .param(ARG_VALUE, "AND 第二边界必须与 BETWEEN 成对出现");
        }
        if (ctx.BETWEEN() != null && ctx.end == null) {
            throw new NopEvalException(ERR_EQL_INVALID_WINDOW_FRAME).loc(loc(ctx))
                    .param(ARG_VALUE, "BETWEEN 后必须有 AND 第二边界");
        }
        return super.visitSqlWindowFrame(ctx);
    }

    @Override
    public String SqlCollectionAccessExpr_collFuncName(ParseTree node) {
        return text(node).toLowerCase(Locale.ROOT);
    }
}