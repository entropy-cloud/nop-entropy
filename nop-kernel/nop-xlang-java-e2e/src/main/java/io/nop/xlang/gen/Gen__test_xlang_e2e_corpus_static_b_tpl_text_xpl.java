// source: /test/xlang/e2e/corpus/static-b/tpl-text.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_b_tpl_text_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/tpl-text.xpl", 1, 2);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/tpl-text.xpl", 1, 37);

    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        Object $v0 = null; // slot 0: v
        java.util.List $t0 = new java.util.ArrayList(2);
        $t0.add(Integer.valueOf(1));
        $t0.add(Integer.valueOf(2));
        java.util.Iterator $t1 = $t0 == null ? null : XLangSemantics.forOfIterator(LOC_0, "for(var of items)", "[1,2]", $t0);
        if ($t1 != null) {
            $loop_1: while (true) {
                $iter_2: {
                    if (!$t1.hasNext()) {
                        break $loop_1;
                    }
                    Object $t2 = $t1.next();
                    $v0 = $t2;
                    $out.text(null, "a");
                    XLangSemantics.escapeOutput(LOC_1, $out, io.nop.xlang.ast.XLangEscapeMode.xmlValue, $v0);
                } // iter block
                }
            }
            return null;
    }
}
