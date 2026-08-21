// source: /test/xlang/e2e/e2e.xlib#Greet
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_e2e_xlib_Greet {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/e2e.xlib", 13, 30);

    public static Object execute(IEvalScope $scope, Object[] $args, IEvalOutput $out) {
        return $fn_1($scope, $args, null, $out);
    }

    private static Object $fn_1(io.nop.core.lang.eval.IEvalScope $scope, Object[] $args, Object[] $captured, IEvalOutput $out) {
        Object $v0 = null; // slot 0: name
        $v0 = $args[0];
        $out.text(null, "hello ");
        XLangSemantics.escapeOutput(LOC_0, $out, io.nop.xlang.ast.XLangEscapeMode.none, $v0);
        $out.text(null, "!");
        return null;
    }
}
