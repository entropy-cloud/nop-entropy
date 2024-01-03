package io.nop.report.core.expr;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.report.core.model.ExpandedCellSet;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.CustomExpression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.ast.print.XLangExpressionPrinter;

import static io.nop.report.core.XptErrors.ARG_EXPR;
import static io.nop.report.core.XptErrors.ERR_XPT_NOT_SUPPORT_EXPR_IN_FORMULA;

/**
 * 保持表达式的其他部分，但是将层次坐标展开成Excel中的Range表达式
 */
public class ReportFormulaGenerator extends XLangExpressionPrinter {
    private final IEvalScope scope;

    public ReportFormulaGenerator(IEvalScope scope) {
        this.scope = scope;
    }

    @Override
    public void visitCustomExpression(CustomExpression node) {
        transformCellSet(node);
    }

    private void transformCellSet(CustomExpression expr) {
        IExecutableExpression executable = expr.getExecutable();
        ExpandedCellSet cellSet = (ExpandedCellSet) XLang.execute(executable, scope);
        if (cellSet == null || cellSet.isEmpty()) {
            print("''");
        } else {
            print(StringHelper.join(cellSet.getCellRanges(), ","));
        }
    }

    @Override
    public void visitMemberExpression(MemberExpression node) {
        if (isCellValueExpr(node)) {
            CustomExpression expr = (CustomExpression) node.getObject();
            transformCellSet(expr);
            return;
        }
        throw new NopException(ERR_XPT_NOT_SUPPORT_EXPR_IN_FORMULA)
                .param(ARG_EXPR, node.toExprString());
    }

    @Override
    public void visitIfStatement(IfStatement node) {
        print("IF(");
        visit(node.getTest());
        print(',');
        visit(node.getConsequent());
        if (node.getAlternate() != null) {
            print(',');
            visit(node.getAlternate());
        }
        print(')');
    }

    @Override
    public void visitLogicalExpression(LogicalExpression node) {
        printLogicalExpr(null, node);
    }

    void printLogicalExpr(XLangOperator prevOp, LogicalExpression node) {
        if (node.getOperator() != prevOp) {
            print(node.getOperator());
            print('(');
        }
        visit(node.getLeft());

        if (node.getRight() != null) {
            print(',');

            if (node.getRight() instanceof LogicalExpression) {
                printLogicalExpr(node.getOperator(), (LogicalExpression) node.getRight());
            } else {
                visit(node.getRight());
            }
        }

        if (node.getOperator() != prevOp) {
            print(')');
        }
    }

    @Override
    protected XLangExpressionPrinter print(XLangOperator operator) {
        if (operator == XLangOperator.OR) {
            print("OR");
        } else if (operator == XLangOperator.AND) {
            print("AND");
        } else if (operator == XLangOperator.EQ) {
            print("=");
        } else if (operator == XLangOperator.NE) {
            print("<>");
        } else if (operator == XLangOperator.NOT) {
            print("NOT");
        } else {
            super.print(operator);
        }
        return this;
    }

    private boolean isCellValueExpr(MemberExpression node) {
        if (node.getObject() instanceof CustomExpression) {
            CustomExpression expr = (CustomExpression) node.getObject();
            if (!(expr.getExecutable() instanceof ICellSetExecutable))
                return false;

            if (node.getProperty() instanceof Identifier)
                return ((Identifier) node.getProperty()).getName().equals("value");
        }
        return false;
    }
}
