// source: /test/xlang/e2e/template.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__test_xlang_e2e_template_xpl {
    private static final SourceLocation LOC_0 = SourceLocation.fromLine("/test/xlang/e2e/template.xpl", 3, 13);
    private static final SourceLocation LOC_1 = SourceLocation.fromLine("/test/xlang/e2e/template.xpl", 3, 28);

    public static Object execute(IEvalScope $scope, IEvalOutput $out) {
        Object $v0 = null; // slot 0: name
        $v0 = "xlang";
        $out.text(null, "\n    hello ");
        XLangSemantics.escapeOutput(LOC_0, $out, io.nop.xlang.ast.XLangEscapeMode.xmlValue, $v0);
        $out.text(null, "! value=");
        Object $t0 = XLangSemantics.multiply(Integer.valueOf(6), Integer.valueOf(7));
        XLangSemantics.escapeOutput(LOC_1, $out, io.nop.xlang.ast.XLangEscapeMode.xmlValue, $t0);
        $out.text(null, " end\n");
        return null;
    }
}
