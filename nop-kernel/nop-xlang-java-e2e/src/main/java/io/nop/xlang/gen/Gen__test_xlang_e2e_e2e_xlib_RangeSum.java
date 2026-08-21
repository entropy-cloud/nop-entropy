// source: /test/xlang/e2e/e2e.xlib#RangeSum
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_e2e_xlib_RangeSum {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/e2e.xlib", 23, 24);

    public static Object execute(IEvalScope $scope, Object[] $args, IEvalOutput $out) {
        return $fn_1($scope, $args, null, $out);
    }

    private static Object $fn_1(io.nop.core.lang.eval.IEvalScope $scope, Object[] $args, Object[] $captured, IEvalOutput $out) {
        Object $v0 = null; // slot 0: n
        Object $v1 = null; // slot 1: total
        Object $v2 = null; // slot 2: i
        $v0 = $args[0];
        $v1 = Integer.valueOf(0);
        $v2 = Integer.valueOf(1);
        $loop_1: while (true) {
            $iter_2: {
                Object $t0 = XLangSemantics.le($v2, $v0);
                if (!XLangSemantics.truthy($t0)) {
                    break $loop_1;
                }
                Object $t1 = $v1;
                $v1 = XLangSemantics.selfAssignValue(LOC_0, "total+=i", io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_ADD, $t1, $v2);
                Object $t2 = $v2;
                $v2 = XLangSemantics.selfIncValue($t2, 1);
            } // iter block
            }
            return $v1;
    }
}
