// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static-b/tpl-xml-extattrs.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_b_tpl_xml_extattrs_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/xlang-compare/static-b/tpl-xml-extattrs.xpl", 1, 2);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/xlang-compare/static-b/tpl-xml-extattrs.xpl", 1, 24);
    private static final SourceLocation LOC_2 = SourceLocation.fromLine("/xlang-compare/static-b/tpl-xml-extattrs.xpl", 1, 23);

    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        $out.text(LOC_0, "\n<div a=\"1\"");
        Object $t0 = XLangSemantics.getScopeValue(LOC_1, "cnt", $scope, "cnt");
        XLangSemantics.outputXmlExtAttrs(LOC_2, "@attrs:cnt", $out, java.util.Collections.unmodifiableSet(new java.util.HashSet(java.util.Arrays.asList("a"))), $t0);
        $out.text(LOC_0, ">x</div>");
        return null;
    }
}
