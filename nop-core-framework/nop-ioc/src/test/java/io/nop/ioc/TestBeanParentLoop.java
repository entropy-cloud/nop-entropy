/**
 * parent 环引用检测回归测试：
 * 修复前 resolveParent 递归前从不设置 STATUS_RESOLVING，环检测分支为死代码，
 * A(parent=B) 且 B(parent=A) 时无限递归直至 StackOverflowError；
 * 修复后递归前标记 RESOLVING，重入即抛 ERR_IOC_PARENT_REF_CONTAINS_LOOP。
 */
package io.nop.ioc;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBeanParentLoop extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParentRefLoopFailsWithExplicitError() {
        // 修复前：此处抛 StackOverflowError（Error 而非 NopException，测试直接红）
        NopException e = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> new AppBeanContainerLoader().loadFromResource("test",
                        attachmentResource("test_parent_loop.beans.xml")));
        assertEquals(IocErrors.ERR_IOC_PARENT_REF_CONTAINS_LOOP.getErrorCode(), e.getErrorCode(),
                "parent 环引用必须抛明确错误码，不得无限递归");
        // 检测点在环闭合处：resolve(A) 标记 RESOLVING 后递归 resolve(B)，B 的 parent 指向
        // 仍处 RESOLVING 的 A → 从 B 的视角抛出（loopRef=A）
        assertEquals("beanB", e.getParam("beanName"), "错误信息必须定位到环上的 bean");
        assertEquals("beanA", e.getParam("loopRef"), "loopRef 指向环的另一端");
    }
}
