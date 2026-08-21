// source: /test/xlang/e2e/corpus/static-a/binding-array.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_binding_array_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/binding-array.xpl", 2, 4);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/binding-array.xpl", 3, 4);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: a
        Object $v1 = null; // slot 1: rest
        java.util.List $t0 = new java.util.ArrayList(3);
        $t0.add(Integer.valueOf(1));
        $t0.add(Integer.valueOf(2));
        $t0.add(Integer.valueOf(3));
        java.util.List $t1 = XLangSemantics.asListBinding(LOC_0, "let [a,...rest] = [1,2,3];", $t0);
        $v0 = $t1.get(0);
        Object $t2 = io.nop.commons.util.CollectionHelper.copyTail($t1, 1);
        $v1 = $t2;
        return XLangSemantics.plus($v0, XLangSemantics.getProperty(LOC_1, "rest.length", "rest", false, "length", $v1, $scope));
    }
}
