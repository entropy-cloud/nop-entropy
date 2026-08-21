// source: /test/xlang/e2e/corpus/static-a/map-access.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_map_access_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/map-access.xpl", 3, 0);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/map-access.xpl", 3, 6);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: m
        java.util.Map $t0 = io.nop.commons.util.CollectionHelper.newLinkedHashMap(1);
        $t0.put(io.nop.commons.util.StringHelper.toString("a", null), Integer.valueOf(1));
        $t0.put(io.nop.commons.util.StringHelper.toString("b", null), Integer.valueOf(2));
        $v0 = $t0;
        return XLangSemantics.plus(XLangSemantics.getProperty(LOC_0, "m.a", "m", false, "a", $v0, $scope), XLangSemantics.getAttr(LOC_1, "m[\"b\"]", "m", "\"b\"", false, $v0, "b"));
    }
}
