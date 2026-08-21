// source: /test/xlang/e2e/corpus/static-b/tpl-collect-node.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_corpus_static_b_tpl_collect_node_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/tpl-collect-node.xpl", 1, 2);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/tpl-collect-node.xpl", 1, 39);
    private static final SourceLocation LOC_2 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/tpl-collect-node.xpl", 1, 36);
    private static final SourceLocation LOC_3 = SourceLocation.fromLine("/test/xlang/e2e/corpus/static-b/tpl-collect-node.xpl", 1, 31);

    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        Object[] $t0 = new Object[]{};
        io.nop.core.lang.eval.ExitMode[] $t1 = new io.nop.core.lang.eval.ExitMode[1];
        Object $t2 = null;
        try {
            $t2 = XLangSemantics.collectNode($scope, LOC_0, false, $t1, ($s, $o, $e, $f) -> $body_1($s, $o, $e, $f), $t0);
        } finally {
        }
        if ($t1[0] == io.nop.core.lang.eval.ExitMode.RETURN) {
            return $t2;
        }
        if ($t1[0] != null) {
            return $t2;
        }
        return $t2;
    }

    private static Object $body_2(io.nop.core.lang.eval.IEvalScope $scope, io.nop.core.lang.eval.IEvalOutput $out, io.nop.core.lang.eval.ExitMode[] $exit, Object[] $frame) {
        Object $t0 = null;
        try {
            $out.value(LOC_1, "t");
            $t0 = null;
        } finally {
        }
        return $t0;
    }

    private static void $gen_3(io.nop.core.lang.eval.IEvalScope $scope, io.nop.core.lang.xml.IXNodeHandler $out, io.nop.core.lang.eval.ExitMode[] $exit, Object[] $frame) {
        java.util.Map $t0 = null;
        String $t1 = null;
        try {
            $t0 = XLangSemantics.genNodeAttrs(new String[]{"x"}, new io.nop.api.core.util.SourceLocation[]{LOC_2}, new Object[]{"1"}, null, null, java.util.Collections.unmodifiableSet(new java.util.HashSet(java.util.Arrays.asList("x"))));
            $t1 = XLangSemantics.genNodeTagName(LOC_3, "a", "a", null, "@node:a");
        } finally {
        }
        XLangSemantics.genNodeHandler($out, LOC_3, $t1, $t0, () -> $body_2($scope, $out, $exit, $frame));
    }

    private static Object $body_1(io.nop.core.lang.eval.IEvalScope $scope, io.nop.core.lang.eval.IEvalOutput $out, io.nop.core.lang.eval.ExitMode[] $exit, Object[] $frame) {
        Object $t0 = null;
        try {
            Object[] $t1 = new Object[]{};
            io.nop.core.lang.eval.ExitMode[] $t2 = new io.nop.core.lang.eval.ExitMode[1];
            Object $t3 = null;
            try {
                $t3 = XLangSemantics.genNode($out, $t2, ($h, $e, $f) -> $gen_3($scope, $h, $e, $f), $t1);
            } finally {
            }
            if ($t2[0] == io.nop.core.lang.eval.ExitMode.RETURN) {
                return $t3;
            }
            if ($t2[0] != null) {
                return $t3;
            }
            $t0 = $t3;
        } finally {
        }
        return $t0;
    }
}
