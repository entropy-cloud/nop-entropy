// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/ctrl-for-break-continue.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_ctrl_for_break_continue_xpl {
    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: s
        Object $v1 = null; // slot 1: i
        $v0 = Integer.valueOf(0);
        $v1 = Integer.valueOf(0);
        $loop_1: while (true) {
            $iter_2: {
                Object $t0 = XLangSemantics.lt($v1, Integer.valueOf(5));
                if (!XLangSemantics.truthy($t0)) {
                    break $loop_1;
                }
                Object $t1 = XLangSemantics.eq($v1, Integer.valueOf(2));
                Object $t2;
                if (XLangSemantics.truthy($t1)) {
                    break $iter_2;
                } else {
                    $t2 = null;
                }
                Object $t3 = XLangSemantics.eq($v1, Integer.valueOf(4));
                Object $t4;
                if (XLangSemantics.truthy($t3)) {
                    break $loop_1;
                } else {
                    $t4 = null;
                }
                $v0 = XLangSemantics.plus($v0, $v1);
            } // iter block
                Object $t5 = $v1;
                $v1 = XLangSemantics.selfIncValue($t5, 1);
            }
            return $v0;
    }
}
