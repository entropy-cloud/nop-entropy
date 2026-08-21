// source: /test/xlang/e2e/corpus/static/arith-plus.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_arith_plus_xpl {
    public static Object execute(IEvalScope $scope) {
        return XLangSemantics.plus(Integer.valueOf(1), XLangSemantics.multiply(Integer.valueOf(2), Integer.valueOf(3)));
    }
}
