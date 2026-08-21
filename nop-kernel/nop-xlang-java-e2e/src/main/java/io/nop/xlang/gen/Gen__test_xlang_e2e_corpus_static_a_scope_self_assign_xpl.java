// source: /test/xlang/e2e/corpus/static-a/scope-self-assign.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_scope_self_assign_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/scope-self-assign.xpl", 2, 0);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/scope-self-assign.xpl", 3, 0);

    public static Object execute(IEvalScope $scope) {
        Object $t0 = $scope.getValue("n");
        Object $t1 = XLangSemantics.selfAssignValue(LOC_0, "n+=3", io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_ADD, $t0, Integer.valueOf(3));
        XLangSemantics.setScopeValue(LOC_0, $scope, "n", $t1);
        return XLangSemantics.getScopeValue(LOC_1, "n", $scope, "n");
    }
}
