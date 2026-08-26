// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/exception-fn-throw.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_exception_fn_throw_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-b/exception-fn-throw.xpl", 2, 21);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-b/exception-fn-throw.xpl", 2, 37);

    public static Object execute(IEvalScope $scope) {
        Object[] $t0 = new Object[]{};
        Object $t1 = null;
        try {
            $t1 = $fn_1($scope, $t0, new Object[0]);
        } catch (java.lang.Exception $t2) {
            throw XLangSemantics.wrapCallFuncException(LOC_1, LOC_1, "f()", $t2);
        }
        return $t1;
    }

    private static Object $fn_1(io.nop.core.lang.eval.IEvalScope $scope, Object[] $args, Object[] $captured) {
        Object $t0 = XLangSemantics.guardNotNull(LOC_0, "\"x\"!", "x");
        Object $t1;
        if ($t0 == null) {
            $t1 = null;
        } else {
            $t1 = XLangSemantics.invokeObjMethod(LOC_0, "\"x\"!.charAt(9)", $t0, "charAt", new Object[]{Integer.valueOf(9)}, $scope);
        }
        return $t1;
    }
}
