/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.antlr4.common.AntlrErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXLangParser {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testIdentifier() {
        String code = "\"test";
        try {
            XLang.newCompileTool().compileFullExpr(null, code);
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(AntlrErrors.ERR_ANTLR_STRING_LITERAL_NOT_END.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testSetFunction() {
        IEvalScope scope = XLang.newEvalScope();
        Set<String> set = new HashSet<>();
        set.add("a");
        scope.setLocalValue("set", set);

        XLang.newCompileTool().allowUnregisteredScopeVar(true).compileFullExpr(null, "set.map(f=>f)").invoke(scope);

        XLang.newCompileTool().allowUnregisteredScopeVar(true).compileFullExpr(null, "set.filter(f=>false)").invoke(scope);
    }
}
