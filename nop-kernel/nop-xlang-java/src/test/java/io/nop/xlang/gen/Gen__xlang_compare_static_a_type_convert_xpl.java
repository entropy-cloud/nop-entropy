// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-a/type-convert.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_a_type_convert_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-a/type-convert.xpl", 2, 0);

    public static Object execute(IEvalScope $scope) {
        return XLangSemantics.plus(XLangSemantics.convertValue(LOC_0, "\"12\".$toInt()", "$toInt", $scope, "12"), Integer.valueOf(1));
    }
}
