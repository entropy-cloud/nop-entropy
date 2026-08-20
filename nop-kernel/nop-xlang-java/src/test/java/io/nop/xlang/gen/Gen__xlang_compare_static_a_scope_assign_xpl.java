// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/scope-assign.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_scope_assign_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/scope-assign.xpl", 2, 11);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-a/scope-assign.xpl", 2, 0);
    private static final SourceLocation LOC_2 = SourceLocation.fromLine("/xlang-compare/static-a/scope-assign.xpl", 3, 0);

    public static Object execute(IEvalScope $scope) {
        Object $t0 = XLangSemantics.plus(XLangSemantics.getScopeValue(LOC_0, "n", $scope, "n"), Integer.valueOf(2));
        XLangSemantics.setScopeValue(LOC_1, $scope, "n", $t0);
        return XLangSemantics.getScopeValue(LOC_2, "n", $scope, "n");
    }
}
