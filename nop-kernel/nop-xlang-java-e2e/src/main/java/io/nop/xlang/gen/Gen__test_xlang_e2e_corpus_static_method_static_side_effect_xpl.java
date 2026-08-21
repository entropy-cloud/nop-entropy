// source: /test/xlang/e2e/corpus/static/method-static-side-effect.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_method_static_side_effect_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static/method-static-side-effect.xpl", 2, 0);

    public static Object execute(IEvalScope $scope) {
        return XLangSemantics.invokeGlobalFunction(LOC_0, "assign(\"result\",1 + 2)", "assign", new Object[]{"result", XLangSemantics.plus(Integer.valueOf(1), Integer.valueOf(2))}, $scope);
    }
}
