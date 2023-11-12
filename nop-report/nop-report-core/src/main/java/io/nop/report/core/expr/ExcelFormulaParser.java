package io.nop.report.core.expr;

import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.simple.SimpleExprParser;

public class ExcelFormulaParser extends SimpleExprParser {
    public ExcelFormulaParser() {
        setUseEvalException(true);
        enableFeatures(ExprFeatures.FUNCTION_CALL);
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