package io.nop.shell;

import io.nop.commons.env.PlatformEnv;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestShellLib {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testLib() {
        if (!PlatformEnv.isWindows())
            return;

        String text = "<shell:Run xpl:lib='/nop/shell/xlib/shell.xlib' command='net' />";

        XNode node = XNodeParser.instance().parseFromText(null, text);
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileTag(node, XLangOutputMode.none);
        ShellResult result = (ShellResult) action.invoke(XLang.newEvalScope());
        System.out.println(JsonTool.serialize(result, true));
        assertEquals(1, result.getReturnCode());
    }
}
