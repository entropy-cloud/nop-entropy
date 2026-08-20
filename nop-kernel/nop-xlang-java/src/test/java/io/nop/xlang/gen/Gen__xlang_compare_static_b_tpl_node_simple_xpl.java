// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/tpl-node-simple.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_tpl_node_simple_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-b/tpl-node-simple.xpl", 1, 7);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-b/tpl-node-simple.xpl", 1, 2);

    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        Object[] $t0 = new Object[]{};
        io.nop.core.lang.eval.ExitMode[] $t1 = new io.nop.core.lang.eval.ExitMode[1];
        Object $t2 = null;
        try {
            $t2 = XLangSemantics.genNode($out, $t1, ($h, $e, $f) -> $gen_1($scope, $h, $e, $f), $t0);
        } finally {
        }
        if ($t1[0] == io.nop.core.lang.eval.ExitMode.RETURN) {
            return $t2;
        }
        if ($t1[0] != null) {
            return $t2;
        }
        return $t2;
    }

    private static void $gen_1(io.nop.core.lang.eval.IEvalScope $scope, io.nop.core.lang.xml.IXNodeHandler $out, io.nop.core.lang.eval.ExitMode[] $exit, Object[] $frame) {
        java.util.Map $t0 = null;
        String $t1 = null;
        try {
            $t0 = XLangSemantics.genNodeAttrs(new String[]{"x"}, new io.nop.api.core.util.SourceLocation[]{LOC_0}, new Object[]{"1"}, null, null, java.util.Collections.unmodifiableSet(new java.util.HashSet(java.util.Arrays.asList("x"))));
            $t1 = XLangSemantics.genNodeTagName(LOC_1, "a", "a", null, "@node:a");
        } finally {
        }
        XLangSemantics.genNodeHandler($out, LOC_1, $t1, $t0, null);
    }
}
