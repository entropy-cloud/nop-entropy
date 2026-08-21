// source: /test/xlang/e2e/corpus/static-a/scope-chain.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_scope_chain_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/scope-chain.xpl", 2, 0);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/scope-chain.xpl", 2, 15);

    public static Object execute(IEvalScope $scope) {
        return XLangSemantics.plus(XLangSemantics.multiply(XLangSemantics.getScopeValue(LOC_0, "x", $scope, "x"), Integer.valueOf(2)), XLangSemantics.getScopeValue(LOC_1, "x", $scope, "x"));
    }
}
