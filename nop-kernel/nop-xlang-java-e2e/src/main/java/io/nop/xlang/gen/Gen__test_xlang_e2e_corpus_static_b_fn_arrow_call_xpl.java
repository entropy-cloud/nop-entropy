// source: /test/xlang/e2e/corpus/static-b/fn-arrow-call.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_b_fn_arrow_call_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/fn-arrow-call.xpl", 2, 20);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: f
        $v0 = XLangSemantics.generatedFunction(1, 1, ($s, $a, $c) -> $fn_1($s, $a, $c), new Object[0]);
        Object $t0;
        if ($v0 == null) {
            $t0 = XLangSemantics.callVarFunction(LOC_0, "f(1)", false, null, null, $scope);
        } else {
            Object[] $t1 = new Object[]{Integer.valueOf(1)};
            $t0 = XLangSemantics.callVarFunction(LOC_0, "f(1)", false, $v0, $t1, $scope);
        }
        return $t0;
    }

    private static Object $fn_1(io.nop.core.lang.eval.IEvalScope $scope, Object[] $args, Object[] $captured) {
        Object $v0 = null; // slot 0: x
        $v0 = $args[0];
        return XLangSemantics.plus($v0, Integer.valueOf(8));
    }
}
