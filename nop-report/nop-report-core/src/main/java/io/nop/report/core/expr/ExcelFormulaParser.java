/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.expr;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.report.core.functions.ReportFunctionProvider;
import io.nop.xlang.api.EvalCodeWithAst;
import io.nop.xlang.api.IFunctionProvider;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.CustomExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.simple.SimpleExprParser;

import java.util.List;

import static io.nop.report.core.XptErrors.ARG_SOURCE;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_EXCEL_FORMULA;

public class ExcelFormulaParser extends AbstractExcelFormulaParser {
    public ExcelFormulaParser() {
        setUseEvalException(true);
        enableFeatures(ExprFeatures.FUNCTION_CALL);
    }

    public static EvalCodeWithAst parseFormula(SourceLocation loc, String formula, XLangCompileTool cp) {
        Expression expr = new ExcelFormulaParser().parseExpr(loc, formula);

        XLangOutputMode oldMode = cp.getOutputMode();
        cp.outputMode(XLangOutputMode.none);
        IFunctionProvider provider = cp.getScope().getFunctionProvider();
        try {
            cp.getScope().setFunctionProvider(ReportFunctionProvider.INSTANCE);

            // 语义分析过程中可能会改expr
            EvalCodeWithAst ret = new EvalCodeWithAst(cp.buildExecutable(expr.deepClone()), formula, expr);
            return ret;
        } catch (Exception e) {
            throw new NopException(ERR_XPT_INVALID_EXCEL_FORMULA, e).loc(loc).param(ARG_SOURCE, formula);
        } finally {
            cp.getScope().setFunctionProvider(provider);
            cp.outputMode(oldMode);
        }
    }

    static Expression s_filterTpl;

    static {
        s_filterTpl = SimpleExprParser.newDefault().parseExpr(null, "e=>body");
    }

    @Override
    protected boolean isStringStart(TextScanner sc) {
        return sc.cur == '"';
    }

    @Override
    protected Expression stringExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        String s = sc.nextDoubleEscapeString();
        sc.skipBlank();
        return newLiteralExpr(loc, s);
    }

    @Override
    protected Expression newFunctionExpr(SourceLocation loc, Expression func, List<Expression> argList, boolean optional) {
        if (isCellSetFilterExpr(func, argList)) {
            String filter = ((Literal) argList.get(0)).getStringValue();
            filter = "e=>(" + filter + ")";
            CustomExpression rangeExr = (CustomExpression) argList.get(1).deepClone();

            ICellSetExecutable rangeExecutable = (ICellSetExecutable) rangeExr.getExecutable();
            // 转换成全局查找所有具有指定名称的单元格，缺省情况下是在当前层次坐标系中查找
            rangeExecutable = rangeExecutable.toAbsolute();

            ArrowFunctionExpression filterExpr = (ArrowFunctionExpression) new ReportExpressionParser().parseExpr(TextScanner.fromString(argList.get(0).getLocation(), filter));

            IEvalFunction fn = XLang.newCompileTool().compileFunction(filterExpr);
            String exprText = "IF(\"" + StringHelper.escapeJava(filter) + "\"," + rangeExecutable.getExpr() + ")";
            rangeExr.setExecutable(new FilterCellSetExecutable(loc, exprText, rangeExecutable, fn));
            return rangeExr;
        } else {
            return super.newFunctionExpr(loc, func, argList, optional);
        }
    }


    /**
     * 特殊定义了一种过滤表达式机制。 IF(filterExpr,cellSet)
     */
    protected boolean isCellSetFilterExpr(Expression func, List<Expression> argExprs) {
        if (func.getASTKind() != XLangASTKind.Identifier)
            return false;

        Identifier id = (Identifier) func;
        if (!id.getName().equals("IF"))
            return false;

        if (argExprs.size() != 2)
            return false;

        Expression filter = argExprs.get(0);
        if (filter.getASTKind() != XLangASTKind.Literal)
            return false;

        Literal filterValue = (Literal) filter;
        if (!(filterValue.getValue() instanceof String))
            return false;

        Expression rangeExpr = argExprs.get(1);
        if (rangeExpr.getASTKind() != XLangASTKind.CustomExpression)
            return false;

        CustomExpression range = (CustomExpression) rangeExpr;
        if (!(range.getExecutable() instanceof ICellSetExecutable))
            return false;
        return true;
    }

    @Override
    protected XLangOperator peekOperator(TextScanner sc) {
        if (sc.peekToken != null) {
            return (XLangOperator) sc.peekToken;
        }

        switch (sc.cur) {
            case '>':
                _peekGt(sc);
                break;
            case '<':
                _peekLt(sc);
                break;
            case '=':
                sc.setPeekToken(XLangOperator.EQ, 1);
                break;
            case '+':
                sc.setPeekToken(XLangOperator.ADD, 1);
                break;
            case '-':
                sc.setPeekToken(XLangOperator.MINUS, 1);
                break;
            case '*':
                sc.setPeekToken(XLangOperator.MULTIPLY, 1);
                break;
            case '/':
                sc.setPeekToken(XLangOperator.DIVIDE, 1);
                break;
            default:
                return null;
        }
        return (XLangOperator) sc.peekToken;
    }

    @Override
    protected void _peekGt(TextScanner sc) {
        int next = sc.peek();
        if (next == '=') {
            // >=
            sc.setPeekToken(XLangOperator.GE, 2);
        } else {
            sc.setPeekToken(XLangOperator.GT, 1);
        }
    }

    @Override
    protected void _peekLt(TextScanner sc) {
        int next = sc.peek();
        if (next == '=') {
            // <=
            sc.setPeekToken(XLangOperator.LE, 2);
        } else if (next == '>') {
            sc.setPeekToken(XLangOperator.NE, 2);
        } else {
            sc.setPeekToken(XLangOperator.LT, 1);
        }
    }

    @Override
    protected boolean consumeOrOp(TextScanner sc) {
        if (sc.tryMatchToken("AND")) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean consumeAndOp(TextScanner sc) {
        if (sc.tryMatchToken("OR")) {
            return true;
        }
        return false;
    }
}