// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/tpl-collect-sql.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_tpl_collect_sql_xpl {
    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        Object[] $t0 = new Object[]{};
        io.nop.core.lang.eval.ExitMode[] $t1 = new io.nop.core.lang.eval.ExitMode[1];
        Object $t2 = null;
        try {
            $t2 = XLangSemantics.collectSql($scope, $t1, ($s, $o, $e, $f) -> $body_1($s, $o, $e, $f), $t0);
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
            $out.text(null, "select 1");
            $t0 = null;
        } finally {
        }
        return $t0;
    }
}
