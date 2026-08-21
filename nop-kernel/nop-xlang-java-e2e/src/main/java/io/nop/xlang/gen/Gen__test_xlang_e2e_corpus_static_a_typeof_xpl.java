// source: /test/xlang/e2e/corpus/static-a/typeof.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_typeof_xpl {
    public static Object execute(IEvalScope $scope) {
        return XLangSemantics.typeOf(Integer.valueOf(3));
    }
}
