// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/fn-closure-cell.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_fn_closure_cell_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-b/fn-closure-cell.xpl", 2, 33);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-b/fn-closure-cell.xpl", 2, 42);
    private static final SourceLocation LOC_2 = SourceLocation.fromLine("/xlang-compare/static-b/fn-closure-cell.xpl", 2, 49);
    private static final SourceLocation LOC_3 = SourceLocation.fromLine("/xlang-compare/static-b/fn-closure-cell.xpl", 2, 56);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: x
        Object $v1 = null; // slot 1: inc
        $v0 = XLangSemantics.setRefValue($v0, Integer.valueOf(1));
        $v1 = XLangSemantics.generatedFunction(0, 0, ($s, $a, $c) -> $fn_1($s, $a, $c), new Object[]{$v0});
        Object $t0;
        if ($v1 == null) {
            $t0 = XLangSemantics.callVarFunction(LOC_1, "inc()", false, null, null, $scope);
        } else {
            Object[] $t1 = new Object[]{};
            $t0 = XLangSemantics.callVarFunction(LOC_1, "inc()", false, $v1, $t1, $scope);
        }
        Object $t2;
        if ($v1 == null) {
            $t2 = XLangSemantics.callVarFunction(LOC_2, "inc()", false, null, null, $scope);
        } else {
            Object[] $t3 = new Object[]{};
            $t2 = XLangSemantics.callVarFunction(LOC_2, "inc()", false, $v1, $t3, $scope);
        }
        return XLangSemantics.getRefValue(LOC_3, "x", "x", $v0);
    }

    private static Object $fn_1(io.nop.core.lang.eval.IEvalScope $scope, Object[] $args, Object[] $captured) {
        Object $v0 = null; // slot 0: x
        $v0 = $captured[0];
        Object $t0 = XLangSemantics.plus(XLangSemantics.getRefValue(LOC_0, "x", "x", $v0), Integer.valueOf(1));
        $v0 = XLangSemantics.setRefValue($v0, $t0);
        return null;
    }
}
