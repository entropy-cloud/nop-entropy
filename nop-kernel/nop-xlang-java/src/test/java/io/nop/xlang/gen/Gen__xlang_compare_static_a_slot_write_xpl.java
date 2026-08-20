// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/slot-write.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_slot_write_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/slot-write.xpl", 5, 0);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-a/slot-write.xpl", 6, 0);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: a
        $v0 = Integer.valueOf(6);
        Object $t0 = $v0;
        $v0 = XLangSemantics.selfIncValue($t0, 1);
        Object $t1 = $v0;
        $v0 = XLangSemantics.selfIncValue($t1, -1);
        Object $t2 = $v0;
        $v0 = XLangSemantics.selfAssignValue(LOC_0, "a+=3", io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_ADD, $t2, Integer.valueOf(3));
        Object $t3 = $v0;
        $v0 = XLangSemantics.selfAssignValue(LOC_1, "a*=2", io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_MULTI, $t3, Integer.valueOf(2));
        return $v0;
    }
}
