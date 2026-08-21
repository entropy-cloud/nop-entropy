// source: /test/xlang/e2e/expr.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_expr_xpl {
    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: a
        Object $v1 = null; // slot 1: b
        $v0 = Integer.valueOf(3);
        $v1 = Integer.valueOf(4);
        return XLangSemantics.plus(XLangSemantics.multiply($v0, $v1), Integer.valueOf(1));
    }
}
