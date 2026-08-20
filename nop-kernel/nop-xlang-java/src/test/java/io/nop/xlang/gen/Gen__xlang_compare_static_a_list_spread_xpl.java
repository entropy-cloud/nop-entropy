// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/list-spread.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_list_spread_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/list-spread.xpl", 2, 0);

    public static Object execute(IEvalScope $scope) {
        java.util.List $t0 = new java.util.ArrayList(2);
        $t0.add(Integer.valueOf(1));
        java.util.List $t1 = new java.util.ArrayList(2);
        $t1.add(Integer.valueOf(2));
        $t1.add(Integer.valueOf(3));
        XLangSemantics.spreadListAdd($t0, $t1);
        return XLangSemantics.getProperty(LOC_0, "[1,...[2,3]].length", "[1,...[2,3]]", false, "length", $t0, $scope);
    }
}
