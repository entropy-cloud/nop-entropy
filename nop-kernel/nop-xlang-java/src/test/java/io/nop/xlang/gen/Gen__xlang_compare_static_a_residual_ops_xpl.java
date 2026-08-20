// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/residual-ops.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_residual_ops_xpl {
    public static Object execute(IEvalScope $scope) {
        Object $t0 = null;
        if ($t0 == null) {
            $t0 = Integer.valueOf(5);
        }
        return XLangSemantics.plus(XLangSemantics.plus(XLangSemantics.plus(io.nop.commons.util.MathHelper.neg(Integer.valueOf(4)), io.nop.commons.util.MathHelper.bneg(Integer.valueOf(3))), io.nop.xlang.utils.EvalHelper.binaryOp(io.nop.xlang.ast.XLangOperator.MOD, Integer.valueOf(7), Integer.valueOf(3))), $t0);
    }
}
