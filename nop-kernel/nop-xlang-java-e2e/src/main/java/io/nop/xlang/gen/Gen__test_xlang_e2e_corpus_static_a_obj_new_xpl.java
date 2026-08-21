// source: /test/xlang/e2e/corpus/static-a/obj-new.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_obj_new_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/obj-new.xpl", 3, 0);

    public static Object execute(IEvalScope $scope) {
        Object $t0 = XLangSemantics.guardNotNull(LOC_0, "new java.lang.StringBuilder(16)!", XLangSemantics.newInstance(LOC_0, "new java.lang.StringBuilder(16)", "java.lang.StringBuilder", new Object[]{Integer.valueOf(16)}, $scope));
        Object $t1;
        if ($t0 == null) {
            $t1 = null;
        } else {
            $t1 = XLangSemantics.invokeObjMethod(LOC_0, "new java.lang.StringBuilder(16)!.length()", $t0, "length", new Object[]{}, $scope);
        }
        return $t1;
    }
}
