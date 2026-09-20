/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.XLangErrors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 端到端验证：从DslNodeLoader入口加载DSL实例，经XDslExtender合并与XDslValidator阶段一校验后，
 * 由挂在XDslValidator.validate尾部的XDefConstraintValidator执行文档级约束规则
 */
public class TestXDefConstraintValidation {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private XNode load(String schemaPath, String instancePath) {
        IResource resource = VirtualFileSystem.instance().getResource(instancePath);
        return DslNodeLoader.INSTANCE.loadFromResource(resource, schemaPath, XDslExtendPhase.validate).getNode();
    }

    private NopException loadError(String schemaPath, String instancePath) {
        return assertThrows(NopException.class, () -> load(schemaPath, instancePath));
    }

    @Test
    public void testOkInstance() {
        XNode node = load("/test/test-constraints.xdef", "/test/test-constraints-ok.xml");
        assertEquals("constraint-test", node.getTagName());
    }

    @Test
    public void testUniqueCodeDocumentScopeWithCustomErrorCode() {
        NopException e = loadError("/test/test-constraints.xdef", "/test/test-constraints-dup-code.xml");
        // 声明的errorCode生效：动态错误码替代平台默认码
        assertEquals("test.err.item-code-duplicated", e.getErrorCode());
        assertEquals("uniqueItemCode", e.getParam("ruleId"));
        assertEquals("c1", e.getParam("attrValue"));
    }

    @Test
    public void testUniquePropFallbackToUniqueAttr() {
        NopException e = loadError("/test/test-constraints.xdef", "/test/test-constraints-dup-name.xml");
        assertEquals(XLangErrors.ERR_XDSL_CHECK_UNIQUE_VIOLATION.getErrorCode(), e.getErrorCode());
        // 未声明prop的规则回退到select命中节点def声明的unique-attr
        assertEquals("uniqueByFallback", e.getParam("ruleId"));
        assertEquals("name", e.getParam("attrName"));
        assertEquals("i1", e.getParam("attrValue"));
    }

    @Test
    public void testSiblingsScopeBucketsByParent() {
        // 同名节点分属不同父节点（不同group下的items），siblings分桶下不构成重复
        load("/test/test-constraints-siblings.xdef", "/test/test-constraints-siblings-ok.xml");

        NopException e = loadError("/test/test-constraints-siblings.xdef",
                "/test/test-constraints-siblings-violation.xml");
        assertEquals(XLangErrors.ERR_XDSL_CHECK_UNIQUE_VIOLATION.getErrorCode(), e.getErrorCode());
        assertEquals("siblingsName", e.getParam("ruleId"));
        assertEquals("dup", e.getParam("attrValue"));
    }

    @Test
    public void testMutexBothPresent() {
        NopException e = loadError("/test/test-constraints.xdef", "/test/test-constraints-mutex-both.xml");
        assertEquals(XLangErrors.ERR_XDSL_CHECK_MUTEX_VIOLATION.getErrorCode(), e.getErrorCode());
        assertEquals("mutexKind", e.getParam("ruleId"));
        assertEquals("a,b", e.getParam("attrValue"));
    }

    @Test
    public void testMutexAtLeastOne() {
        NopException e = loadError("/test/test-constraints.xdef", "/test/test-constraints-mutex-none.xml");
        assertEquals(XLangErrors.ERR_XDSL_CHECK_MUTEX_VIOLATION.getErrorCode(), e.getErrorCode());
        assertEquals("mutexKind", e.getParam("ruleId"));
        assertEquals("", e.getParam("attrValue"));
    }

    @Test
    public void testRequireMissing() {
        NopException e = loadError("/test/test-constraints.xdef", "/test/test-constraints-require-missing.xml");
        assertEquals(XLangErrors.ERR_XDSL_CHECK_REQUIRE_VIOLATION.getErrorCode(), e.getErrorCode());
        assertEquals("requireRefWhenSpecial", e.getParam("ruleId"));
        assertEquals("ref", e.getParam("attrName"));
    }

    @Test
    public void testRequireForbidden() {
        NopException e = loadError("/test/test-constraints.xdef", "/test/test-constraints-require-forbidden.xml");
        assertEquals(XLangErrors.ERR_XDSL_CHECK_REQUIRE_VIOLATION.getErrorCode(), e.getErrorCode());
        assertEquals("requireRefWhenSpecial", e.getParam("ruleId"));
        assertEquals("alt", e.getParam("attrName"));
    }

    @Test
    public void testCheckRefNotImplemented() {
        // check-ref的执行属P1：xdef加载成功（声明可机读），DSL实例校验期显式fail-loud
        NopException e = loadError("/test/test-constraints-ref.xdef", "/test/test-constraints-ref-instance.xml");
        assertEquals(XLangErrors.ERR_XDEF_CHECK_NOT_IMPLEMENTED.getErrorCode(), e.getErrorCode());
        assertEquals("dependsRef", e.getParam("ruleId"));
    }

    @Test
    public void testNoConstraintSchemaPassThrough() {
        // 无约束声明的xdef加载路径行为不变（零开销直通），用既有测试模型验证
        IResource resource = VirtualFileSystem.instance().getResource("/test/test-constraints-siblings-ok.xml");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource, "/test/test-constraints-siblings.xdef",
                XDslExtendPhase.validate).getNode();
        assertEquals("sibling-test", node.getTagName());
    }
}
