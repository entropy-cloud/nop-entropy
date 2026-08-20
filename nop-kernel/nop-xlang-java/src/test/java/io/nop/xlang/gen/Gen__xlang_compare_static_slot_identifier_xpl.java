// generated fixture: regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain -- DO NOT EDIT
// source: /xlang-compare/static/slot-identifier.xpl
package io.nop.xlang.gen;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;

public final class Gen__xlang_compare_static_slot_identifier_xpl {
    public static Object execute(IEvalScope $scope) {
        Object $v0 = null; // slot 0: x
        $v0 = Integer.valueOf(5);
        return XLangSemantics.plus(XLangSemantics.multiply($v0, Integer.valueOf(2)), Integer.valueOf(1));
    }
}
