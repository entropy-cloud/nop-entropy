// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static/exception-method.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_exception_method_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static/exception-method.xpl", 2, 0);

    public static Object execute(IEvalScope $scope) {
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
