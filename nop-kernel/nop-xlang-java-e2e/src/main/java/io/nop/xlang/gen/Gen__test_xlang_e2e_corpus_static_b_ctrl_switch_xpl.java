// source: /test/xlang/e2e/corpus/static-b/ctrl-switch.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_b_ctrl_switch_xpl {
    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: x
        Object $v1 = null; // slot 1: r
        $v0 = Integer.valueOf(2);
        $v1 = Integer.valueOf(0);
        Object $t0 = null;
        $sw_1: {
            if (java.util.Objects.equals($v0, Integer.valueOf(1))) {
                $v1 = Integer.valueOf(10);
                $t0 = null;
                break $sw_1;
            }
            if (java.util.Objects.equals($v0, Integer.valueOf(2))) {
                $v1 = Integer.valueOf(20);
                $t0 = null;
                break $sw_1;
            }
            $v1 = Integer.valueOf(30);
            $t0 = null;
        }
        return $v1;
    }
}
