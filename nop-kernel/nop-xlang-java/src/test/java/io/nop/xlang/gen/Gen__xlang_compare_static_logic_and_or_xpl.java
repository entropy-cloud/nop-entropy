// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static/logic-and-or.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_logic_and_or_xpl {
    public static Object execute(IEvalScope $scope) {
        Object $t0;
        if (XLangSemantics.truthy(Boolean.TRUE)) {
            $t0 = XLangSemantics.lt(Integer.valueOf(1), Integer.valueOf(2));
        } else {
            $t0 = Boolean.TRUE;
        }
        Object $t1;
        if (!XLangSemantics.truthy($t0)) {
            $t1 = Boolean.FALSE;
        } else {
            $t1 = $t0;
        }
        return $t1;
    }
}
