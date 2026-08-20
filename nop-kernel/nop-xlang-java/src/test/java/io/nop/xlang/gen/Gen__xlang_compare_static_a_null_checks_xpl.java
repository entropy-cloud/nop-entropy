// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/null-checks.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_null_checks_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/null-checks.xpl", 2, 1);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-a/null-checks.xpl", 2, 23);

    public static Object execute(IEvalScope $scope) {
        Object $t0;
        if (!XLangSemantics.truthy((XLangSemantics.getScopeValue(LOC_0, "x", $scope, "x")) == null)) {
            $t0 = (XLangSemantics.getScopeValue(LOC_1, "x", $scope, "x")) != null;
        } else {
            $t0 = (XLangSemantics.getScopeValue(LOC_0, "x", $scope, "x")) == null;
        }
        return $t0;
    }
}
