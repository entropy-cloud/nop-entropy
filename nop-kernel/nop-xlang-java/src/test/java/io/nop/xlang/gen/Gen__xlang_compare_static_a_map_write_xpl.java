// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/map-write.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_map_write_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/map-write.xpl", 3, 0);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-a/map-write.xpl", 4, 0);
    private static final SourceLocation LOC_2 = SourceLocation.fromLine("/xlang-compare/static-a/map-write.xpl", 5, 0);
    private static final SourceLocation LOC_3 = SourceLocation.fromLine("/xlang-compare/static-a/map-write.xpl", 6, 0);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: m
        java.util.Map $t0 = io.nop.commons.util.CollectionHelper.newLinkedHashMap(0);
        $t0.put(io.nop.commons.util.StringHelper.toString("a", null), Integer.valueOf(1));
        $v0 = $t0;
        XLangSemantics.setProperty(LOC_0, "m.a = 5", "a", $v0, Integer.valueOf(5), $scope);
        XLangSemantics.selfAssignProperty(LOC_1, "m.a += 10", "a", io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_ADD, $v0, Integer.valueOf(10), $scope);
        XLangSemantics.selfAssignAttr(LOC_2, "m[\"a\"] *= 2", "\"a\"", io.nop.xlang.ast.XLangOperator.SELF_ASSIGN_MULTI, $v0, "a", Integer.valueOf(2));
        return XLangSemantics.getProperty(LOC_3, "m.a", "m", false, "a", $v0, $scope);
    }
}
