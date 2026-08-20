// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/exception-prop.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_exception_prop_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/exception-prop.xpl", 3, 0);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: m
        java.util.Map $t0 = io.nop.commons.util.CollectionHelper.newLinkedHashMap(0);
        $t0.put(io.nop.commons.util.StringHelper.toString("a", null), Integer.valueOf(1));
        $v0 = $t0;
        return XLangSemantics.getProperty(LOC_0, "m.b.c", "m.b", false, "c", XLangSemantics.getProperty(LOC_0, "m.b", "m", false, "b", $v0, $scope), $scope);
    }
}
