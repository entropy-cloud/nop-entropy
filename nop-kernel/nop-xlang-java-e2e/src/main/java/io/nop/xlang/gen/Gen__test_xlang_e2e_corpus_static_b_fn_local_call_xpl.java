// source: /test/xlang/e2e/corpus/static-b/fn-local-call.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_b_fn_local_call_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/fn-local-call.xpl", 2, 26);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/fn-local-call.xpl", 2, 31);

    public static Object execute(IEvalScope $scope) {
        Object[] $t0 = new Object[]{Integer.valueOf(1)};
        Object $t1 = null;
        try {
            $t1 = $fn_1($scope, $t0, new Object[0]);
        } catch (java.lang.Exception $t2) {
            throw XLangSemantics.wrapCallFuncException(LOC_0, LOC_0, "f(1)", $t2);
        }
        Object[] $t3 = new Object[]{Integer.valueOf(2)};
        Object $t4 = null;
        try {
            $t4 = $fn_2($scope, $t3, new Object[0]);
        } catch (java.lang.Exception $t5) {
            throw XLangSemantics.wrapCallFuncException(LOC_1, LOC_1, "f(2)", $t5);
        }
        return XLangSemantics.plus($t1, $t4);
    }

    private static Object $fn_1(io.nop.core.lang.eval.IEvalScope $scope, Object[] $args, Object[] $captured) {
        Object $v0 = null; // slot 0: a
        $v0 = $args[0];
        return XLangSemantics.multiply($v0, Integer.valueOf(2));
    }

    private static Object $fn_2(io.nop.core.lang.eval.IEvalScope $scope, Object[] $args, Object[] $captured) {
        Object $v0 = null; // slot 0: a
        $v0 = $args[0];
        return XLangSemantics.multiply($v0, Integer.valueOf(2));
    }
}
