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
import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.ast.SqlValuesTableSource;
import io.nop.orm.eql.ast.SqlWindowFrame;
import io.nop.orm.eql.ast.SqlWindowFrameBound;
import io.nop.orm.eql.enums.SqlCompareRange;
import io.nop.orm.eql.enums.SqlDateTimeType;
import io.nop.orm.eql.enums.SqlIntervalUnit;
import io.nop.orm.eql.enums.SqlJoinType;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.enums.SqlUnionType;
import io.nop.orm.eql.enums.SqlWindowFrameBoundType;
import io.nop.orm.eql.enums.SqlWindowFrameType;
import io.nop.orm.eql.parse.antlr.EqlParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.nop.antlr4.common.ParseTreeHelper.isToken;
import static io.nop.antlr4.common.ParseTreeHelper.loc;
import static io.nop.antlr4.common.ParseTreeHelper.text;
import static io.nop.antlr4.common.ParseTreeHelper.token;
import static io.nop.orm.eql.OrmEqlErrors.ARG_ALLOWED_NAMES;
import static io.nop.orm.eql.OrmEqlErrors.ARG_EXPR;
import static io.nop.orm.eql.OrmEqlErrors.ARG_SQL_TYPE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_VALUE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_DATETIME_TYPE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_INTERVAL_UNIT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_SQL_TYPE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_WINDOW_FRAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_PRECISION_NOT_POSITIVE_INT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_SCALE_NOT_NON_NEGATIVE_INT;
import static io.nop.orm.eql.parse.EqlParseHelper.operator;
import static io.nop.orm.eql.OrmEqlErrors.ARG_ARG_COUNT;
import static io.nop.orm.eql.OrmEqlErrors.ARG_ROW_INDEX;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_VALUES_ROW_ARITY_MISMATCH;

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
     * rules: sqlOrderByItem（WI7 Phase 3：NULLS FIRST/LAST 文法激活，token→Boolean 三态
     * 转换，同 {@link #SqlOrderByItem_asc(Token)} 先例——FIRST=true / LAST=false；缺省
     * （无 NULLS 子句）由生成体的 ctx.nullsFirst null 检查保证不赋值、保持 Boolean null）。
     * 消费侧门/缺省序推导见 {@code AstToSqlGenerator.visitSqlOrderByItem}（Phase 2 先行落地）。
     */
    public Boolean SqlOrderByItem_nullsFirst(org.antlr.v4.runtime.Token token) {
        return isToken(token, EqlParser.FIRST);
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
     * WI12 Phase 2（plan 38，裁定
     * ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §1.3）：
     * {@code nowait=NOWAIT} token label → boolean 字段直达赋值
     * （{@code SqlQuerySelect_selectAll(Token)} 同形先例）。
     * rules: sqlQuerySelect
     */
    public boolean SqlQuerySelect_nowait(org.antlr.v4.runtime.Token token) {
        return token != null;
    }

    /**
     * WI12 Phase 2（plan 38，裁定 §1.3）：{@code skipLocked=skipLocked_} rule label →
     * boolean 字段直达赋值（{@code SqlQuerySelect_forUpdate(ParseTree)} 同形先例）。
     * rules: sqlQuerySelect
     */
    public boolean SqlQuerySelect_skipLocked(ParseTree node) {
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
        // WI2 BUG-7: 词法层关键字大小写不敏感（Alphabet.g4 fragment [Aa]），小写
        // date/time/timestamp 正常进入本方法；fromText 是大小写敏感精确匹配，须先
        // 归一化，否则小写输入落入错误分支。错误分支此前对 Token 参数做
        // (ParserRuleContext) 强转 ⇒ ClassCastException 掩盖 ERR_EQL_INVALID_DATETIME_TYPE；
        // loc(Token) 重载已存在（ParseTreeHelper），直接使用。
        SqlDateTimeType dateTimeType = SqlDateTimeType.fromText(value.toUpperCase(Locale.ROOT));
        if (dateTimeType == null)
            throw new NopEvalException(ERR_EQL_INVALID_DATETIME_TYPE).loc(loc(node))
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
     * WI5 Phase 1: SQL-92 标准函数语法 {@code POSITION(x IN y)}（BaseRule.g4
     * sqlExpr_special 的 {@code SqlRegularFunction_position} 备选分支）。构建期归一化
     * 装入**既有** {@link SqlRegularFunction}（name=instr，args=[source, begin]——
     * instr(str, substr) 参数序要求调用点换序），不新增 AST 节点、不改 EqlAST.xjava。
     * 生成方法体只建空节点 + validate()（name 必填校验必失败），因此完全重写、不调 super。
     * 方言未注册 instr 时由 EqlTransformVisitor 的既有 ERR_EQL_UNKNOWN_FUNCTION 路径响亮
     * 报错。裁定：ai-dev/design/eql-cross-db-compat/2026-10-04-wi5-phase0-adjudications.md §3。
     */
    @Override
    public SqlRegularFunction visitSqlRegularFunction_position(EqlParser.SqlRegularFunction_positionContext ctx) {
        SqlExpr begin = (SqlExpr) visit(ctx.sqlExpr_bit());
        SqlExpr source = (SqlExpr) visit(ctx.sqlExpr());

        SqlRegularFunction ret = new SqlRegularFunction();
        ret.setLocation(ParseTreeHelper.loc(ctx));
        ret.setName("instr");
        List<SqlExpr> args = new ArrayList<>(2);
        args.add(source);
        args.add(begin);
        ret.setArgs(args);
        ret.normalize();
        ret.validate();
        return ret;
    }

    /**
     * WI8 Phase 1（plan 27；裁定 ai-dev/design/eql-cross-db-compat/
     * 2026-10-04-wi8-phase0-adjudications.md §一.2/§九）：{@code a IS NOT DISTINCT FROM b}
     * 的构建期归一化——装入既有 {@link SqlBinaryExpr}（operator=SAFE_EQ，左右操作数保序），
     * 不新增 AST 节点、不改 EqlAST.xjava。生成方法体只建空节点 + validate()（left/operator/
     * right 必填校验必失败），因此完全重写、不调 super（visitSqlRegularFunction_position
     * 同款）。方言输出（h2 等 native / mysql 族 <=> / 其余响亮报错）由 AstToSqlGenerator
     * 渲染三分支承担；Oracle 三节点展开由生产 IEqlAstTransformer 承担（Phase 3）。
     */
    @Override
    public io.nop.orm.eql.ast.SqlBinaryExpr visitSqlBinaryExpr_isNotDistinctFrom(
            EqlParser.SqlBinaryExpr_isNotDistinctFromContext ctx) {
        io.nop.orm.eql.ast.SqlBinaryExpr ret = new io.nop.orm.eql.ast.SqlBinaryExpr();
        ret.setLocation(ParseTreeHelper.loc(ctx));
        ret.setLeft((SqlExpr) visit(ctx.sqlExpr_primary()));
        ret.setOperator(SqlOperator.SAFE_EQ);
        ret.setRight((SqlExpr) visit(ctx.sqlExpr_predicate()));
        ret.normalize();
        ret.validate();
        return ret;
    }

    /**
     * WI8 Phase 1：{@code a IS DISTINCT FROM b} 的构建期归一化——
     * {@link SqlNotExpr} 包装 {@code SqlBinaryExpr(operator=SAFE_EQ)}（裁定 §一.2 规范形态；
     * 否定方向文本由 AstToSqlGenerator 方言分支直出，不走 NOT 包装）。生成方法体只建
     * 空节点 + validate()（expr 必填必失败），因此完全重写、不调 super。
     */
    @Override
    public io.nop.orm.eql.ast.SqlNotExpr visitSqlNotExpr_isDistinctFrom(
            EqlParser.SqlNotExpr_isDistinctFromContext ctx) {
        io.nop.orm.eql.ast.SqlBinaryExpr eq = new io.nop.orm.eql.ast.SqlBinaryExpr();
        eq.setLeft((SqlExpr) visit(ctx.sqlExpr_primary()));
        eq.setOperator(SqlOperator.SAFE_EQ);
        eq.setRight((SqlExpr) visit(ctx.sqlExpr_predicate()));
        eq.normalize();
        eq.validate();

        io.nop.orm.eql.ast.SqlNotExpr ret = new io.nop.orm.eql.ast.SqlNotExpr();
        ret.setLocation(ParseTreeHelper.loc(ctx));
        ret.setExpr(eq);
        ret.normalize();
        ret.validate();
        return ret;
    }

    /**
     * WI8 Phase 2（plan 27；裁定 ai-dev/design/eql-cross-db-compat/
     * 2026-10-04-wi8-phase0-adjudications.md §一.3/§二.2/§五）：{@code a IS TRUE} 的构建期
     * 真值表展开——{@code a IS NOT NULL AND a = TRUE}（安全词表，只含既有节点，全方言安全，
     * 规避 MySQL 原生 {@code IS TRUE} 的数值真值语义）。生成方法体是**透明委托**
     * （返回操作数节点 = 静默丢谓词），因此完全重写、不调 super——本方法是谓词语义的
     * 唯一载体，缺失即静默降级（Minimum Rules #24）。
     */
    @Override
    public io.nop.orm.eql.ast.SqlExpr visitSqlAndExpr_isTrue(EqlParser.SqlAndExpr_isTrueContext ctx) {
        return buildBooleanTruthExpansion(ctx.sqlExpr_primary(), true, true);
    }

    /** WI8 Phase 2：{@code a IS NOT TRUE} ⇒ {@code NOT (a IS NOT NULL AND a = TRUE)}——NULL 行为
     * TRUE（roadmap 警告：不得降级为 {@code a<>0}/{@code NOT a}，后者 NULL 行为 NULL）。 */
    @Override
    public io.nop.orm.eql.ast.SqlExpr visitSqlNotExpr_isNotTrue(EqlParser.SqlNotExpr_isNotTrueContext ctx) {
        return wrapNot(buildBooleanTruthExpansion(ctx.sqlExpr_primary(), true, true), ctx);
    }

    /** WI8 Phase 2：{@code a IS FALSE} ⇒ {@code a IS NOT NULL AND a = FALSE}。 */
    @Override
    public io.nop.orm.eql.ast.SqlExpr visitSqlAndExpr_isFalse(EqlParser.SqlAndExpr_isFalseContext ctx) {
        return buildBooleanTruthExpansion(ctx.sqlExpr_primary(), false, true);
    }

    /** WI8 Phase 2：{@code a IS NOT FALSE} ⇒ {@code NOT (a IS NOT NULL AND a = FALSE)}——NULL 行为 TRUE。 */
    @Override
    public io.nop.orm.eql.ast.SqlExpr visitSqlNotExpr_isNotFalse(EqlParser.SqlNotExpr_isNotFalseContext ctx) {
        return wrapNot(buildBooleanTruthExpansion(ctx.sqlExpr_primary(), false, true), ctx);
    }

    /**
     * 真值表展开共用体：{@code (operand IS NOT NULL) AND (operand = <booleanLiteral>)}。
     * 逐行验算见裁定 §二.2（TRUE/FALSE/NULL/非布尔操作数行全二值）；布尔字面量渲染经
     * {@code dialect.getBooleanValueLiteral}（default 系=1/0，h2=true/false），数值操作数
     * {@code 2 = 1} 恒 FALSE——天然规避 MySQL truthy 分叉。
     *
     * <p>⚠️ 操作数上下文 visit **两次**（两个独立子树实例）：AST 框架禁止节点双亲
     * （{@code nop.err.core.lang.ast-node-not-allow-multiple-parent}），共享实例不可行；
     * 两实例语义同源（同一 source 区间重复构建）。操作数含参数标记（?）时响亮拒绝
     * （{@link OrmEqlErrors#ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER}）：双实例 =
     * 标记节点复制 = 1 个 EQL 参数变 2 个 JDBC 参数，绑定错位——静默复制违反 Minimum
     * Rules #24。`IS [NOT] UNKNOWN` 归一化单引用操作数，不受此限。</p>
     */
    private io.nop.orm.eql.ast.SqlExpr buildBooleanTruthExpansion(ParserRuleContext operandCtx,
            boolean literalValue, boolean requireNotNull) {
        SqlExpr notNullOperand = (SqlExpr) visit(operandCtx);
        SqlExpr eqOperand = (SqlExpr) visit(operandCtx);
        rejectParamMarkerOperand(notNullOperand, operandCtx);

        io.nop.orm.eql.ast.SqlIsNullExpr notNull = new io.nop.orm.eql.ast.SqlIsNullExpr();
        notNull.setLocation(ParseTreeHelper.loc(operandCtx));
        notNull.setExpr(notNullOperand);
        notNull.setNot(requireNotNull);
        notNull.normalize();
        notNull.validate();

        io.nop.orm.eql.ast.SqlBooleanLiteral literal = new io.nop.orm.eql.ast.SqlBooleanLiteral();
        literal.setLocation(ParseTreeHelper.loc(operandCtx));
        literal.setValue(literalValue);
        literal.normalize();
        literal.validate();

        io.nop.orm.eql.ast.SqlBinaryExpr eq = new io.nop.orm.eql.ast.SqlBinaryExpr();
        eq.setLocation(ParseTreeHelper.loc(operandCtx));
        eq.setLeft(eqOperand);
        eq.setOperator(SqlOperator.EQ);
        eq.setRight(literal);
        eq.normalize();
        eq.validate();

        io.nop.orm.eql.ast.SqlAndExpr and = new io.nop.orm.eql.ast.SqlAndExpr();
        and.setLocation(ParseTreeHelper.loc(operandCtx));
        and.setLeft(notNull);
        and.setRight(eq);
        and.normalize();
        and.validate();
        return and;
    }

    private io.nop.orm.eql.ast.SqlExpr wrapNot(io.nop.orm.eql.ast.SqlExpr expr, ParserRuleContext ctx) {
        io.nop.orm.eql.ast.SqlNotExpr ret = new io.nop.orm.eql.ast.SqlNotExpr();
        ret.setLocation(ParseTreeHelper.loc(ctx));
        ret.setExpr(expr);
        ret.normalize();
        ret.validate();
        return ret;
    }

    /**
     * 展开型 NULL 谓词（IS [NOT] TRUE/FALSE 构建期展开 + Phase 3 OracleEqlAstTransformer 的
     * DISTINCT FROM guard 式）的操作数守护：子树含 {@link io.nop.orm.eql.ast.SqlParameterMarker}
     * 即 true（调用方响亮报错，错误码
     * {@link OrmEqlErrors#ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER}）。
     * `IS [NOT] UNKNOWN` 归一化不复制操作数，无需本守护。public static 供 transformer 复用
     * （单一事实来源）。
     */
    public static boolean containsParameterMarker(io.nop.orm.eql.ast.EqlASTNode node) {
        if (node instanceof io.nop.orm.eql.ast.SqlParameterMarker)
            return true;
        boolean[] found = new boolean[1];
        node.forEachChild(child -> {
            if (!found[0] && containsParameterMarker(child))
                found[0] = true;
        });
        return found[0];
    }

    private void rejectParamMarkerOperand(SqlExpr operand, ParserRuleContext ctx) {
        if (containsParameterMarker(operand)) {
            String echo = operand.toSqlString().replaceAll("\\s+", " ").trim();
            throw new NopEvalException(ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER)
                    .loc(loc(ctx)).param(ARG_EXPR, echo);
        }
    }

    /**
     * WI5 Phase 4: TRIM 标准三段式的构建期归一化（BaseRule.g4 sqlExpr_special 的三个
     * trim 备选分支）。两档设计（裁定 §1.4/§4）：
     * <ul>
     * <li>无 chars：TRIM(BOTH FROM y) → trim(y)、LEADING → ltrim(y)、TRAILING → rtrim(y)
     * （目标函数单参、全体方言已注册）；</li>
     * <li>带 chars：TRIM([dir] chars FROM y) → trimChars/ltrimChars/rtrimChars(y, chars)
     * （args=[str, chars] 主串在前，方向进逐方言模板；无模板方言编译期
     * ERR_EQL_UNKNOWN_FUNCTION 响亮报错——禁止静默丢 chars、禁止降级单参 trim）。</li>
     * </ul>
     * 生成方法体只建空节点或透明委托，因此三个方法均完全重写、不调 super。单参
     * {@code trim(x)} 不经过本组方法（留在 sqlRegularFunction 路径）。
     */
    @Override
    public SqlRegularFunction visitSqlRegularFunction_trimDirection(
            EqlParser.SqlRegularFunction_trimDirectionContext ctx) {
        String dir = text(token(ctx.trimDirection__())).toLowerCase(Locale.ROOT);
        return newTrimFunction(ctx, dir, null, (SqlExpr) visit(ctx.sqlExpr()));
    }

    @Override
    public SqlRegularFunction visitSqlRegularFunction_trimDirectionChars(
            EqlParser.SqlRegularFunction_trimDirectionCharsContext ctx) {
        String dir = text(token(ctx.trimDirection__())).toLowerCase(Locale.ROOT);
        SqlExpr chars = (SqlExpr) visit(ctx.sqlExpr_simple());
        return newTrimFunction(ctx, dir, chars, (SqlExpr) visit(ctx.sqlExpr()));
    }

    @Override
    public SqlRegularFunction visitSqlRegularFunction_trimChars(
            EqlParser.SqlRegularFunction_trimCharsContext ctx) {
        SqlExpr chars = (SqlExpr) visit(ctx.sqlExpr_simple());
        return newTrimFunction(ctx, "both", chars, (SqlExpr) visit(ctx.sqlExpr()));
    }

    private SqlRegularFunction newTrimFunction(EqlParser.SqlExpr_specialContext ctx, String dir, SqlExpr chars,
            SqlExpr str) {
        boolean withChars = chars != null;
        String name;
        switch (dir) {
            case "leading":
                name = withChars ? "ltrimChars" : "ltrim";
                break;
            case "trailing":
                name = withChars ? "rtrimChars" : "rtrim";
                break;
            case "both":
                name = withChars ? "trimChars" : "trim";
                break;
            default:
                // 文法层 trimDirection__ 仅放行 leading/trailing/both，此分支不可达；
                // 照 SqlDateTimeLiteral 同款防御（不新起错误码，Phase 0 裁定 §6）
                throw new IllegalStateException("Unknown trim direction: " + dir);
        }

        SqlRegularFunction ret = new SqlRegularFunction();
        ret.setLocation(ParseTreeHelper.loc(ctx));
        ret.setName(name);
        List<SqlExpr> args = new ArrayList<>(2);
        args.add(str);
        if (withChars)
            args.add(chars);
        ret.setArgs(args);
        ret.normalize();
        ret.validate();
        return ret;
    }

    /**
     * WI5 Phase 3: SQL-92 标准函数语法 {@code EXTRACT(f FROM d)}（BaseRule.g4
     * sqlExpr_special 的 {@code SqlRegularFunction_extract} 备选分支，字段规则
     * extractField_ 只放行七个有 lowering 目标的字段）。构建期归一化装入**既有**
     * {@link SqlRegularFunction}（name=字段名小写，单参 [d]——EXTRACT(YEAR FROM d)
     * → year(d)），与方言函数表注册名一一同名，不新增 AST 节点。生成方法体只建空节点 +
     * validate()（必填校验必失败），因此完全重写、不调 super。既有逗号形态
     * {@code extract(f,d)} 走 sqlRegularFunction 路径不变。
     */
    @Override
    public SqlRegularFunction visitSqlRegularFunction_extract(EqlParser.SqlRegularFunction_extractContext ctx) {
        String field = text(token(ctx.extractField__())).toLowerCase(Locale.ROOT);
        SqlExpr source = (SqlExpr) visit(ctx.sqlExpr());

        SqlRegularFunction ret = new SqlRegularFunction();
        ret.setLocation(ParseTreeHelper.loc(ctx));
        ret.setName(field);
        List<SqlExpr> args = new ArrayList<>(1);
        args.add(source);
        ret.setArgs(args);
        ret.normalize();
        ret.validate();
        return ret;
    }

    /**
     * WI5 Phase 2: SQL-92 标准函数语法 {@code SUBSTRING(x FROM a [FOR b])}（BaseRule.g4
     * sqlExpr_special 的 {@code SqlRegularFunction_substring} 备选分支）。构建期归一化
     * 装入**既有** {@link SqlRegularFunction}（name=substr，FOR 缺省 2 参、否则 3 参，
     * 复用方言函数表 substr 的 minArgCount=2/maxArgCount=3 两元多态），不新增 AST 节点。
     * 生成方法体只建空节点 + validate()（name 必填校验必失败），因此完全重写、不调 super。
     * 负起点索引等方言语义分叉见 owner doc 差异表（EQL 只做无损换形不做语义合并）。
     */
    @Override
    public SqlRegularFunction visitSqlRegularFunction_substring(EqlParser.SqlRegularFunction_substringContext ctx) {
        SqlExpr str = (SqlExpr) visit(ctx.sqlExpr_bit(0));
        SqlExpr start = (SqlExpr) visit(ctx.sqlExpr_bit(1));

        SqlRegularFunction ret = new SqlRegularFunction();
        ret.setLocation(ParseTreeHelper.loc(ctx));
        ret.setName("substr");
        List<SqlExpr> args = new ArrayList<>(3);
        args.add(str);
        args.add(start);
        if (ctx.FOR() != null)
            args.add((SqlExpr) visit(ctx.sqlExpr_bit(2)));
        ret.setArgs(args);
        ret.normalize();
        ret.validate();
        return ret;
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
     * WI7b（plan 37，roadmap EC ①；ⓒ-A 裁定文档 §3）：GROUP BY 位置 grouping 操作符
     * {@code ROLLUP(args)} / {@code CUBE(args)}（BaseRule.g4 sqlGroupByItem 前置 label 备选）。
     * 构建 wrapper：{@code SqlGroupByItem.expr = SqlGroupingOp(type, args)}——items 列表类型
     * 零改动；生成方法体只建空节点 + validate()（expr 必填必失败），因此完全重写、不调 super
     * （visitSqlIntervalExpr_string 同款）。备选前置使 GROUP BY 位置新节点胜出（守护用例 =
     * TestEqlGroupingOpsDialect 在 h2 上断言 NOT_SUPPORT_FEATURE 而非 UNKNOWN_FUNCTION）。
     */
    @Override
    public io.nop.orm.eql.ast.SqlGroupByItem visitSqlGroupByItem_rollup(
            EqlParser.SqlGroupByItem_rollupContext ctx) {
        return buildGroupingOpItem(ctx, io.nop.orm.eql.enums.SqlGroupingOpType.ROLLUP, ctx.functionArgs_());
    }

    @Override
    public io.nop.orm.eql.ast.SqlGroupByItem visitSqlGroupByItem_cube(
            EqlParser.SqlGroupByItem_cubeContext ctx) {
        return buildGroupingOpItem(ctx, io.nop.orm.eql.enums.SqlGroupingOpType.CUBE, ctx.functionArgs_());
    }

    private io.nop.orm.eql.ast.SqlGroupByItem buildGroupingOpItem(ParserRuleContext ctx,
            io.nop.orm.eql.enums.SqlGroupingOpType type, EqlParser.FunctionArgs_Context argsCtx) {
        io.nop.orm.eql.ast.SqlGroupingOp op = new io.nop.orm.eql.ast.SqlGroupingOp();
        op.setLocation(ParseTreeHelper.loc(ctx));
        op.setType(type);
        if (argsCtx != null)
            op.setArgs(buildFunctionArgs_(argsCtx));
        else
            op.setArgs(java.util.Collections.emptyList());

        io.nop.orm.eql.ast.SqlGroupByItem item = new io.nop.orm.eql.ast.SqlGroupByItem();
        item.setLocation(ParseTreeHelper.loc(ctx));
        item.setExpr(op);
        item.normalize();
        item.validate();
        return item;
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
        // WI9b: 多词类型名（TIME/TIMESTAMP WITH LOCAL TIME ZONE）的 token 文本经
        // ParseTreeHelper.text = RuleContext.getText() 无分隔符拼接（空白 token 被 lexer 跳过），
        // 需从 token span 重建含空白原文并归一化为枚举名，否则多词备选会"解析过 + 语义必拒"
        // （BUG-11 同型；裁定 ai-dev/design/eql-cross-db-compat/2026-10-05-wi9b-phase0-adjudications.md §5.3）。
        // 单 token 名称（既有全部键与 sqlIdentifier_ 兜底）走原路径，行为逐字节不变。
        if (node instanceof ParserRuleContext) {
            ParserRuleContext ctx = (ParserRuleContext) node;
            if (ctx.getStart() != null && ctx.getStop() != null
                    && ctx.getStart().getTokenIndex() != ctx.getStop().getTokenIndex()) {
                String spaced = ctx.getStart().getInputStream()
                        .getText(org.antlr.v4.runtime.misc.Interval.of(
                                ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()));
                if (spaced != null)
                    name = spaced.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
            }
        }
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
        if (ctx.crossJoin_() != null)
            return SqlJoinType.CROSS_JOIN;
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

    /**
     * WI2 BUG-15（裁定=候选A 解析期拒绝，裁定文档 §六）：SqlTypeExpr 的 characterSet
     * 零消费点——AstToEqlGenerator.visitSqlTypeExpr 只打印 name/precision/scale，
     * AstToSqlGenerator.visitSqlCastExpr 绕过 SqlTypeExpr ⇒ 字段有值即被静默丢弃。
     * 本访问器被调用 = 文法分支可达（今天 STRING token 无 lexer 规则而永久不可达）=
     * 显式拒绝，防止未来文法修复后退化为 BUG-1 同型静默丢弃。守护测试
     * TestEqlSqlTypeClauseGuard 锁定"一旦可达即失败"。
     */
    @Override
    public String SqlTypeExpr_characterSet(ParseTree node) {
        throw new NopException(ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT)
                .loc(loc(node))
                .param(ARG_VALUE, text(node));
    }

    /**
     * WI2 BUG-15：同 {@link #SqlTypeExpr_characterSet(ParseTree)}——quoted 形态
     * {@code collate 'xx'}（STRING_ 可词法化）今天即可达，此前 collate 字段被写入后
     * 被两个生成器静默丢弃；本守卫把该通路即刻转为响亮拒绝。
     */
    @Override
    public String SqlTypeExpr_collate(ParseTree node) {
        throw new NopException(ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT)
                .loc(loc(node))
                .param(ARG_VALUE, text(node));
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

    /**
     * WI12 Phase 4（plan 38，裁定
     * ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §4）：
     * VALUES 表源构建——生成体只映射 rows/alias 两个 label prop；本覆写补充
     * ① sqlColumnAliases__ 内联规则的别名列清单文本填充（List<String> 无 label→prop
     * 自动映射通路），② ragged rows（行元数不一致）解析期响亮
     * （{@link OrmEqlErrors#ERR_EQL_VALUES_ROW_ARITY_MISMATCH}，不允许静默）。
     */
    @Override
    public SqlValuesTableSource visitSqlValuesTableSource(
            EqlParser.SqlValuesTableSourceContext ctx) {
        SqlValuesTableSource ret = super.visitSqlValuesTableSource(ctx);

        EqlParser.SqlColumnAliases__Context cols = ctx.sqlColumnAliases__();
        if (cols != null) {
            List<String> names = new ArrayList<>(cols.sqlIdentifier_().size());
            for (EqlParser.SqlIdentifier_Context id : cols.sqlIdentifier_())
                names.add(text(id));
            ret.setColumns(names);
        }

        int arity = ret.getRows().get(0).getValues().size();
        for (int i = 1; i < ret.getRows().size(); i++) {
            int rowArity = ret.getRows().get(i).getValues().size();
            if (rowArity != arity)
                throw new NopException(ERR_EQL_VALUES_ROW_ARITY_MISMATCH)
                        .loc(ret.getRows().get(i).getLocation())
                        .param(ARG_ROW_INDEX, i + 1)
                        .param(ARG_ARG_COUNT, rowArity);
        }
        return ret;
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