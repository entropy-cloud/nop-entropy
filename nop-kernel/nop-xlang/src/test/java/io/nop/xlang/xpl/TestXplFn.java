/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.xlang.XLangErrors.ERR_XPL_FN_BODY_IS_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestXplFn {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testFnBodyIsFunction_throws() {
        XNode node = XNode.make("test");
        node.content("(x) => x + 1");

        XLangCompileTool cp = XLang.newCompileTool();
        IFunctionType functionType = new GenericTypeParser().parseFunctionTypeFromText(null, "(x:int)=>int");

        NopException e = assertThrows(NopException.class,
                () -> cp.compileEvalFunction(node, functionType, XLangOutputMode.none));
        assertEquals(ERR_XPL_FN_BODY_IS_FUNCTION.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testFnPlainBody_ok() {
        XNode node = XNode.make("test");
        node.content("x + 1");

        XLangCompileTool cp = XLang.newCompileTool();
        IFunctionType functionType = new GenericTypeParser().parseFunctionTypeFromText(null, "(x:int)=>int");

        assertDoesNotThrow(() -> cp.compileEvalFunction(node, functionType, XLangOutputMode.none));
    }
}
