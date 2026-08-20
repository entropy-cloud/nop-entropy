// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/global-var.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_global_var_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/global-var.xpl", 2, 0);

    public static Object execute(IEvalScope $scope) {
        Object $t0 = XLangSemantics.guardNotNull(LOC_0, "$Math!", XLangSemantics.getGlobalVarValue(LOC_0, "$Math", $scope, "$Math"));
        Object $t1;
        if ($t0 == null) {
            $t1 = null;
        } else {
            $t1 = XLangSemantics.invokeObjMethod(LOC_0, "$Math!.abs(-3)", $t0, "abs", new Object[]{io.nop.commons.util.MathHelper.neg(Integer.valueOf(3))}, $scope);
        }
        return $t1;
    }
}
