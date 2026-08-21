// source: /test/xlang-java-gen/tags.xlib#Sum
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_java_gen_tags_xlib_Sum {
    public static Object execute(IEvalScope $scope, Object[] $args, IEvalOutput $out) {
        return $fn_1($scope, $args, null, $out);
    }

    private static Object $fn_1(io.nop.core.lang.eval.IEvalScope $scope, Object[] $args, Object[] $captured, IEvalOutput $out) {
        Object $v0 = null; // slot 0: a
        Object $v1 = null; // slot 1: b
        $v0 = $args[0];
        $v1 = $args.length > 1 ? $args[1] : null;
        return XLangSemantics.plus($v0, $v1);
    }
}
