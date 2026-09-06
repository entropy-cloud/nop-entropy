package io.nop.biz.impl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * biz-defaults.beans.xml装配一致性：nopCrudBizInitializer必须显式收集IActionDecoratorCollector，
 * 否则base=crud的动态biz对象（CrudBizInitializer.initialize）装配的装饰器链恒为空，
 * 与BizObjectBuilder.buildOperations主路径行为分叉。修复前该bean无任何property。
 */
public class TestBizDefaultsBeansWiring extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testCrudBizInitializerCollectsDecoratorCollectors() {
        XNode beans = XNode.parseFromResource(new ClassPathResource(
                "classpath:_vfs/nop/biz/beans/biz-defaults.beans.xml"));
        assertNotNull(beans, "biz-defaults.beans.xml must be parseable");

        XNode initializer = findBean(beans, "nopCrudBizInitializer");
        assertNotNull(initializer, "nopCrudBizInitializer bean must be registered");

        XNode prop = findProperty(initializer, "decoratorCollectors");
        assertNotNull(prop,
                "nopCrudBizInitializer must inject decoratorCollectors, otherwise dynamic crud biz objects never get decorators");

        XNode collect = prop.childByTag("ioc:collect-beans");
        assertNotNull(collect, "decoratorCollectors must be collected via ioc:collect-beans");
        assertEquals("io.nop.biz.decorator.IActionDecoratorCollector", collect.attrText("by-type"));
    }

    private static XNode findBean(XNode beans, String beanId) {
        for (XNode bean : beans.elements("bean")) {
            if (beanId.equals(bean.attrText("id")))
                return bean;
        }
        return null;
    }

    private static XNode findProperty(XNode bean, String propName) {
        for (XNode prop : bean.elements("property")) {
            if (propName.equals(prop.attrText("name")))
                return prop;
        }
        return null;
    }
}
