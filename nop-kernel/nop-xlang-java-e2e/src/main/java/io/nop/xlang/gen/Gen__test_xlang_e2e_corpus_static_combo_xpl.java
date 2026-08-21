// source: /test/xlang/e2e/corpus/static/combo.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_combo_xpl {
    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: a
        $v0 = Integer.valueOf(3);
        Object $t0 = XLangSemantics.gt($v0, Integer.valueOf(2));
        Object $t1;
        if (XLangSemantics.truthy($t0)) {
            $t1 = XLangSemantics.eq(XLangSemantics.plus("v", $v0), "v3");
        } else {
            $t1 = $t0;
        }
        return $t1;
    }
}
