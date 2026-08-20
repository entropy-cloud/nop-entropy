// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /itest-xlang/tenant/unit.itxpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__itest_xlang_tenant_unit_itxpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/itest-xlang/tenant/unit.itxpl", 1, 0);

    public static Object execute(IEvalScope $scope) {
        return XLangSemantics.plus(XLangSemantics.multiply(XLangSemantics.getScopeValue(LOC_0, "x", $scope, "x"), Integer.valueOf(2)), Integer.valueOf(1));
    }
}
