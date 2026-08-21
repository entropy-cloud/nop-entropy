// source: /test/xlang/e2e/control.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_control_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/control.xpl", 5, 8);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: total
        Object $v1 = null; // slot 1: i
        $v0 = Integer.valueOf(0);
        $v1 = Integer.valueOf(1);
        $loop_1: while (true) {
            $iter_2: {
                Object $t0 = XLangSemantics.le($v1, Integer.valueOf(5));
                if (!XLangSemantics.truthy($t0)) {
                    break $loop_1;
                }
                Object $t1 = $v0;
                $v0 = XLangSemantics.selfAssignValue(LOC_0, "total+=i", io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_ADD, $t1, $v1);
                Object $t2 = $v1;
                $v1 = XLangSemantics.selfIncValue($t2, 1);
            } // iter block
            }
            return XLangSemantics.multiply($v0, Integer.valueOf(2));
    }
}
