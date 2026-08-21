// source: /test/xlang/e2e/corpus/static-a/prop-in.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_prop_in_xpl {
    public static Object execute(IEvalScope $scope) {
        java.util.Map $t0 = io.nop.commons.util.CollectionHelper.newLinkedHashMap(0);
        $t0.put(io.nop.commons.util.StringHelper.toString("a", null), Integer.valueOf(1));
        return XLangSemantics.propIn("a", $t0);
    }
}
