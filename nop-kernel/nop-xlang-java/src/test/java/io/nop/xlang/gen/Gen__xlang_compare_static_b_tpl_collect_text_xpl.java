// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/tpl-collect-text.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_tpl_collect_text_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-b/tpl-collect-text.xpl", 1, 34);

    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        Object[] $t0 = new Object[]{};
        io.nop.core.lang.eval.ExitMode[] $t1 = new io.nop.core.lang.eval.ExitMode[1];
        Object $t2 = null;
        try {
            $t2 = XLangSemantics.collectText($scope, $t1, ($s, $o, $e, $f) -> $body_1($s, $o, $e, $f), $t0);
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

    private static Object $body_1(io.nop.core.lang.eval.IEvalScope $scope, io.nop.core.lang.eval.IEvalOutput $out, io.nop.core.lang.eval.ExitMode[] $exit, Object[] $frame) {
        Object $t0 = null;
        try {
            $out.text(null, "a");
            Object $t1 = XLangSemantics.plus(Integer.valueOf(1), Integer.valueOf(2));
            XLangSemantics.escapeOutput(LOC_0, $out, io.nop.xlang.ast.XLangEscapeMode.none, $t1);
            $out.text(null, "c");
            $t0 = null;
        } finally {
        }
        return $t0;
    }
}
