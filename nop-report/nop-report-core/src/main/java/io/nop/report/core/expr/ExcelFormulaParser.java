package io.nop.report.core.expr;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.report.core.functions.ReportFunctionProvider;
import io.nop.xlang.api.EvalCodeWithAst;
import io.nop.xlang.api.IFunctionProvider;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.expr.ExprFeatures;

import static io.nop.report.core.XptErrors.ARG_SOURCE;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_EXCEL_FORMULA;

public class ExcelFormulaParser extends AbstractExcelFormulaParser {
    public ExcelFormulaParser() {
        setUseEvalException(true);
        enableFeatures(ExprFeatures.FUNCTION_CALL);
    }

    public static IEvalAction parseFormula(SourceLocation loc, String formula, XLangCompileTool cp) {
        Expression expr = new ExcelFormulaParser().parseExpr(loc, formula);

        XLangOutputMode oldMode = cp.getOutputMode();
        cp.outputMode(XLangOutputMode.none);
        IFunctionProvider provider = cp.getScope().getFunctionProvider();
        try {
            cp.getScope().setFunctionProvider(ReportFunctionProvider.INSTANCE);

            // 语义分析过程中可能会改expr
            EvalCodeWithAst ret = new EvalCodeWithAst(expr, formula, cp.buildEvalAction(expr.deepClone()));
            return ret;
        } catch (Exception e) {
            throw new NopException(ERR_XPT_INVALID_EXCEL_FORMULA, e).loc(loc).param(ARG_SOURCE, formula);
        } finally {
            cp.getScope().setFunctionProvider(provider);
            cp.outputMode(oldMode);
        }
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