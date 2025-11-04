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

    @Test
    public void testReturn() {
        IEvalScope scope = XLang.newEvalScope();
        XLang.newCompileTool().compileFullExpr(null, "\n return {\na:1,b:2}").invoke(scope);

        String source = "\n" +
                "                 return {\n" +
                "                    type: 'select',\n" +
                "                    searchable: true,\n" +
                "                    clearable: true,\n" +
                "                    multiple: false,\n" +
                "                    selectFirst: dispMeta?.selectFirst,\n" +
                "                    source: dispMeta?.sourceUrl,\n" +
                "                    value: editMode == 'add' ? dispMeta?.defaultValue ?? propMeta?.defaultValue : null\n" +
                "                 }\n" +
                "            ";
        XLang.newCompileTool().allowUnregisteredScopeVar(true).compileFullExpr(null, source);
    }

    @Test
    public void testArrayFunction() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("arr", new int[]{1, 2, 3, 4, 5});

        XLang.newCompileTool().allowUnregisteredScopeVar(true).compileFullExpr(null, "arr.map(f=>f+1)").invoke(scope);
    }
}