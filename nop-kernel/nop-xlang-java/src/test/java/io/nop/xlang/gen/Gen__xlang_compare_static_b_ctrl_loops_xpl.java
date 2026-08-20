// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/ctrl-loops.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_ctrl_loops_xpl {
    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: i
        Object $v1 = null; // slot 1: s
        $v0 = Integer.valueOf(0);
        $v1 = Integer.valueOf(0);
        $loop_1: while (true) {
            $iter_2: {
                Object $t0 = XLangSemantics.lt($v0, Integer.valueOf(4));
                if (!XLangSemantics.truthy($t0)) {
                    break $loop_1;
                }
                $v1 = XLangSemantics.plus($v1, $v0);
                Object $t1 = $v0;
                $v0 = XLangSemantics.selfIncValue($t1, 1);
            } // iter block
            }
            $loop_3: while (true) {
                $iter_4: {
                    $v1 = XLangSemantics.plus($v1, Integer.valueOf(100));
                    Object $t2 = $v0;
                    $v0 = XLangSemantics.selfIncValue($t2, 1);
                } // iter block
                    Object $t3 = XLangSemantics.lt($v0, Integer.valueOf(1));
                    if (!XLangSemantics.truthy($t3)) {
                        break $loop_3;
                    }
                }
                return $v1;
    }
}
