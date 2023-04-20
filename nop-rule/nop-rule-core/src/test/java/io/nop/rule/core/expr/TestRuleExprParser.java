package io.nop.rule.core.expr;

import io.nop.xlang.ast.Expression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRuleExprParser {

    @Test
    public void testCompare() {
        String expr = " >= 3 and < 5";

        checkExpr("obj?.myVar >= 3 && obj?.myVar < 5", expr);
    }

    void checkExpr(String expected, String expr) {
        Expression exprObj = new RuleExprParser("obj.myVar").parseExpr(null, expr);
        assertEquals(expected, exprObj.toExprString());
    }
}
