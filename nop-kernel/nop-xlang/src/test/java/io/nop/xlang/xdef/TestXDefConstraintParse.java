/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.xdef.impl.XDefCheckRequire;
import io.nop.xlang.xdef.impl.XDefCheckUnique;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXDefConstraintParse {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParseConstraintDeclarations() {
        IXDefinition def = SchemaLoader.loadXDefinition("/test/test-constraints.xdef");

        assertEquals(2, def.getXdefCheckUniques().size());
        XDefCheckUnique unique = def.getXdefCheckUniques().get(0);
        assertEquals("uniqueItemName", unique.getId());
        assertEquals("//groups/item", unique.getSelect());
        assertEquals("name", unique.getProp());
        assertEquals(XDefCheckScope.siblings, unique.getScope());
        assertEquals("test.err.item-code-duplicated", def.getXdefCheckUniques().get(1).getErrorCode());
        assertEquals("item编码[{attrValue}]在同类目下重复", def.getXdefCheckUniques().get(1).getMessage());

        assertEquals(1, def.getXdefCheckMutexs().size());
        assertEquals("mutexKind", def.getXdefCheckMutexs().get(0).getId());
        assertTrue(def.getXdefCheckMutexs().get(0).getAtLeastOne());

        assertEquals(1, def.getXdefCheckRequires().size());
        XDefCheckRequire require = def.getXdefCheckRequires().get(0);
        assertEquals("requireRefWhenSpecial", require.getId());
        assertTrue(require.getCondition() != null, "condition should be compiled to IEvalAction");
        assertTrue(require.getRequiredProps().contains("ref"));
        assertTrue(require.getForbiddenProps().contains("alt"));
    }

    @Test
    public void testDuplicateRuleId() {
        NopException e = assertThrows(NopException.class,
                () -> SchemaLoader.loadXDefinition("/test/test-constraints-bad-id.xdef"));
        assertEquals(XLangErrors.ERR_XDEF_CHECK_DUPLICATE_RULE_ID.getErrorCode(), e.getErrorCode());
        assertEquals("dupRule", e.getParam("ruleId"));
    }

    @Test
    public void testInvalidSelect() {
        NopException e = assertThrows(NopException.class,
                () -> SchemaLoader.loadXDefinition("/test/test-constraints-bad-select.xdef"));
        assertEquals(XLangErrors.ERR_XDEF_CHECK_SELECT_COMPILE_ERROR.getErrorCode(), e.getErrorCode());
        assertEquals("badSelect", e.getParam("ruleId"));
    }

    @Test
    public void testInvalidCondition() {
        NopException e = assertThrows(NopException.class,
                () -> SchemaLoader.loadXDefinition("/test/test-constraints-bad-condition.xdef"));
        assertEquals(XLangErrors.ERR_XDEF_CHECK_CONDITION_COMPILE_ERROR.getErrorCode(), e.getErrorCode());
        assertEquals("badCondition", e.getParam("ruleId"));
    }

    @Test
    public void testDefTypeNotImplemented() {
        NopException e = assertThrows(NopException.class,
                () -> SchemaLoader.loadXDefinition("/test/test-constraints-def-type.xdef"));
        assertEquals(XLangErrors.ERR_XDEF_CHECK_NOT_IMPLEMENTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testGlobalScopeNotImplemented() {
        NopException e = assertThrows(NopException.class,
                () -> SchemaLoader.loadXDefinition("/test/test-constraints-global-scope.xdef"));
        assertEquals(XLangErrors.ERR_XDEF_CHECK_NOT_IMPLEMENTED.getErrorCode(), e.getErrorCode());
        assertEquals("globalScope", e.getParam("ruleId"));
    }

    @Test
    public void testBootstrapXdefSelfParse() {
        // xdef.xdef自身声明的check-*/def-type元素在业务名字空间下(keys.NS为xdef-meta)，不受根级约束解析影响
        IXDefinition def = new XDefinitionParser()
                .parseFromResource(VirtualFileSystem.instance().getResource("/nop/schema/xdef.xdef"));
        assertTrue(def.getXdefCheckUniques().isEmpty());
        assertTrue(def.getXdefCheckRefs().isEmpty());
        assertTrue(def.getXdefCheckMutexs().isEmpty());
        assertTrue(def.getXdefCheckRequires().isEmpty());
        assertTrue(def.getXdefDefTypes().isEmpty());
    }
}
