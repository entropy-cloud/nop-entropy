// source: /test/xlang/e2e/corpus/static-a/exception-convert.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_exception_convert_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/exception-convert.xpl", 2, 0);

    public static Object execute(IEvalScope $scope) {
        return XLangSemantics.convertValue(LOC_0, "\"abc\".$toInt()", "$toInt", $scope, "abc");
    }
}
