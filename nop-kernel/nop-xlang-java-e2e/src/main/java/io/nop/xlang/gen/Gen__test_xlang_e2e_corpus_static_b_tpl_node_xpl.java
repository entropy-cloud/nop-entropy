// source: /test/xlang/e2e/corpus/static-b/tpl-node.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_b_tpl_node_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/tpl-node.xpl", 1, 2);

    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        $out.text(LOC_0, "\n<a x=\"1\">\n<b>t</b></a>");
        return null;
    }
}
