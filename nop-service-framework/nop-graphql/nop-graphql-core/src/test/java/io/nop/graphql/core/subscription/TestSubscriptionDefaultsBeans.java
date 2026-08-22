/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.subscription;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * subscription-defaults.beans.xml装配一致性：每个property必须在类上有对应setter（否则一旦被加载
 * NopIoC会抛ERR_IOC_UNKNOWN_BEAN_PROP），start()必须标注@PostConstruct（否则显式启用后消息订阅不启动）。
 */
public class TestSubscriptionDefaultsBeans extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testBeansPropertiesHaveSetters() {
        XNode beans = XNode.parseFromResource(new ClassPathResource(
                "classpath:_vfs/nop/graphql/beans/subscription-defaults.beans.xml"));
        assertNotNull(beans, "subscription-defaults.beans.xml must be parseable");

        List<XNode> beanNodes = beans.elements("bean");
        assertTrue(!beanNodes.isEmpty());

        for (XNode beanNode : beanNodes) {
            String className = beanNode.attrText("class");
            assertNotNull(className);
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                fail("bean class not found: " + className);
                return;
            }

            for (XNode prop : beanNode.elements("property")) {
                String propName = prop.attrText("name");
                assertNotNull(propName);
                assertTrue(hasSetter(clazz, propName),
                        "bean " + className + " has no setter for property '" + propName
                                + "' (would throw ERR_IOC_UNKNOWN_BEAN_PROP once this file is loaded)");
            }
        }
    }

    @Test
    public void testStartHasPostConstruct() {
        Method start;
        try {
            start = GraphQLSubscriptionManager.class.getMethod("start");
        } catch (NoSuchMethodException e) {
            fail("GraphQLSubscriptionManager.start not found");
            return;
        }
        assertNotNull(start.getAnnotation(PostConstruct.class),
                "start() must be annotated with @PostConstruct so that bean lifecycle starts topic subscription");
    }

    private static boolean hasSetter(Class<?> clazz, String propName) {
        String setter = "set" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(setter) && method.getParameterCount() == 1
                    && Modifier.isPublic(method.getModifiers()))
                return true;
        }
        return false;
    }
}
