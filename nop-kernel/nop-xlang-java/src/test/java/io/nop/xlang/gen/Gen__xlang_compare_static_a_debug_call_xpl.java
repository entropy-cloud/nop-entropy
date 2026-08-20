// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/debug-call.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_debug_call_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/debug-call.xpl", 2, 0);

    public static Object execute(IEvalScope $scope) {
        String $t0 = io.nop.api.core.convert.ConvertHelper.toString("p");
        io.nop.xlang.utils.DebugHelper.v(LOC_0, $t0, "\"abc\"", "abc");
        return "abc";
    }
}
