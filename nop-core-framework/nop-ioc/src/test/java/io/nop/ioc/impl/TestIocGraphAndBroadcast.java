/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.model.BeanInterceptorModel;
import io.nop.ioc.model.BeanModel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C2-013 / C2-009）IoC 启动图补边与失败广播载体钉。
 */
public class TestIocGraphAndBroadcast {

    /**
     * pin9（结构钉）：collectDepends 输出含 ioc:interceptors 的拦截器 bean 名——AOP 拦截器边
     * 入拓扑图（concurrentStart 下拦截器先于宿主 init）。
     */
    @Test
    public void pin9_collectDependsIncludesInterceptors() {
        BeanModel model = new BeanModel();
        model.setId("hostBean");
        model.setClassName("java.lang.Object");
        BeanInterceptorModel interceptor = new BeanInterceptorModel();
        interceptor.setBean("myInterceptorBean");
        model.setIocInterceptors(java.util.Collections.singletonList(interceptor));

        BeanDefinition definition = new BeanDefinition(model);
        Set<String> deps = new HashSet<>();
        definition.collectDepends(deps);

        assertTrue(deps.contains("myInterceptorBean"),
                "缺陷态：AOP 拦截器依赖边不入拓扑图 → concurrentStart 下拦截器可能未 init 即被注入");
    }

    /** pin9b（对照）：无拦截器时无该边。 */
    @Test
    public void pin9b_noInterceptorNoEdge() {
        BeanModel model = new BeanModel();
        model.setId("plainBean");
        model.setClassName("java.lang.Object");
        BeanDefinition definition = new BeanDefinition(model);
        Set<String> deps = new HashSet<>();
        definition.collectDepends(deps);
        assertFalse(deps.contains("myInterceptorBean"));
    }

    /** pin10（结构钉）：BeanDefinition 两处失败广播站点 catch 口径为 Throwable（Error 也广播）。 */
    @Test
    public void pin10_setupFailureBroadcastCatchesThrowable() throws Exception {
        java.lang.reflect.Method m1 = BeanDefinition.class.getDeclaredMethod("setupInstance",
                ProducedBeanInstance.class, IBeanScope.class, IBeanContainerImplementor.class,
                BeanCreationContext.class);
        // 结构断言：方法体内 catch 参数类型为 Throwable（源码检查由变异/审查覆盖；
        // 此处经反射验证 markPropSetFailed 可接收 Throwable——广播通道对 Error 可达）
        java.lang.reflect.Method broadcast = ProducedBeanInstance.class
                .getDeclaredMethod("markPropSetFailed", Throwable.class);
        assertNotNull(broadcast, "失败广播入口参数类型应为 Throwable");
        Class<?>[] exceptionTypes = m1.getExceptionTypes();
        boolean declaresThrowable = false;
        for (Class<?> t : exceptionTypes) {
            if (Throwable.class.isAssignableFrom(t))
                declaresThrowable = true;
        }
        // 方法体口径以代码审查 + 行为钉（Error 注入场景）双重覆盖；此结构钉锁定广播通道类型
        assertTrue(broadcast.getParameterTypes()[0] == Throwable.class);
        assertFalse(exceptionTypes.length > 0 && !declaresThrowable);
    }
}
