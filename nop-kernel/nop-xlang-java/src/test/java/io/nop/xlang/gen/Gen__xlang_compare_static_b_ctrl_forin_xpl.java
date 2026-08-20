// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/ctrl-forin.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_ctrl_forin_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-b/ctrl-forin.xpl", 2, 10);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: s
        Object $v1 = null; // slot 1: k
        $v0 = "";
        java.util.Map $t0 = io.nop.commons.util.CollectionHelper.newLinkedHashMap(1);
        $t0.put(io.nop.commons.util.StringHelper.toString("a", null), Integer.valueOf(1));
        $t0.put(io.nop.commons.util.StringHelper.toString("b", null), Integer.valueOf(2));
        java.util.Map $t1 = XLangSemantics.asForInMap(LOC_0, "for(var in items)", $t0);
        if ($t1 != null) {
            java.util.Iterator $t2 = $t1.keySet().iterator();
            $loop_1: while (true) {
                $iter_2: {
                    if (!$t2.hasNext()) {
                        break $loop_1;
                    }
                    Object $t3 = $t2.next();
                    $v1 = $t3;
                    $v0 = XLangSemantics.plus($v0, $v1);
                } // iter block
                }
            }
            return $v0;
    }
}
