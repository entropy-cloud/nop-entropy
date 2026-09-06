/**
 * &lt;set&gt; 缺省实现类型回归测试：
 * 修复前未指定 set-class 时缺省 ArrayList，SetValueResolver 强转 Set 抛 CCE；
 * 修复后缺省 LinkedHashSet，元素保持插入顺序。
 */
package io.nop.ioc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MySetBean;

import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSetValues extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testSetWithoutSetClassYieldsSetImplementation() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_set_values.beans.xml"));
        try {
            container.start();
            // 修复前：属性赋值时 (Set) ArrayList 强转抛 ClassCastException（适配为 NopException）
            MySetBean bean = (MySetBean) container.getBean("mySetBean");
            assertEquals(new LinkedHashSet<>(java.util.List.of("a", "b", "c")), bean.getTags(),
                    "<set> 未指定 set-class 时缺省实现必须是 Set 类型且保持插入顺序");
        } finally {
            container.stop();
        }
    }
}
