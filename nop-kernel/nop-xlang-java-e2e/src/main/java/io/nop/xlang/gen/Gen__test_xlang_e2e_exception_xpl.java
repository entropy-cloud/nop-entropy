// source: /test/xlang/e2e/exception.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_exception_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/exception.xpl", 2, 4);

    public static Object execute(IEvalScope $scope) {
        XLangSemantics.throwException(LOC_0, "throw \"e2e-boom\"", "e2e-boom");
        return null;
    }
}
