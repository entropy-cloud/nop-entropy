// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/tpl-xml.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_tpl_xml_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-b/tpl-xml.xpl", 1, 2);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-b/tpl-xml.xpl", 1, 15);

    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        $out.text(LOC_0, "\n<div a=\"1\"");
        Object $t0 = XLangSemantics.plus(Integer.valueOf(2), Integer.valueOf(3));
        XLangSemantics.outputXmlAttr(LOC_1, $out, "b", $t0);
        $out.text(LOC_0, ">x</div>");
        return null;
    }
}
