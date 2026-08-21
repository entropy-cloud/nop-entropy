// source: /test/xlang/e2e/corpus/static-a/binding-object.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_a_binding_object_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/binding-object.xpl", 2, 4);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-a/binding-object.xpl", 3, 8);

    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: x
        Object $v1 = null; // slot 1: y
        Object $v2 = null; // slot 2: rest
        java.util.Map $t0 = io.nop.commons.util.CollectionHelper.newLinkedHashMap(1);
        $t0.put(io.nop.commons.util.StringHelper.toString("x", null), Integer.valueOf(1));
        $t0.put(io.nop.commons.util.StringHelper.toString("y", null), Integer.valueOf(2));
        $t0.put(io.nop.commons.util.StringHelper.toString("z", null), Integer.valueOf(3));
        java.util.Map $t1 = XLangSemantics.asMapBinding(LOC_0, "let {x:x,y:y,...rest] = {\"x\":1,\"y\":2,\"z\":3};", $t0);
        Object $t2 = $t1.get("x");
        $v0 = $t2;
        Object $t3 = $t1.get("y");
        $v1 = $t3;
        java.util.Map $t4 = new java.util.LinkedHashMap();
        for (java.util.Iterator it = $t1.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry entry = (java.util.Map.Entry) it.next();
            if (!java.util.Collections.unmodifiableSet(new java.util.HashSet(java.util.Arrays.asList("x", "y"))).contains(entry.getKey())) {
                $t4.put(entry.getKey(), entry.getValue());
            }
        }
        $v2 = $t4;
        return XLangSemantics.plus(XLangSemantics.plus($v0, $v1), XLangSemantics.getProperty(LOC_1, "rest.z", "rest", false, "z", $v2, $scope));
    }
}
