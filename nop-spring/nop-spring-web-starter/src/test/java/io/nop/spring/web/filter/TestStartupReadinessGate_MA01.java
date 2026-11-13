package io.nop.spring.web.filter;

import io.nop.core.initialize.CoreInitialization;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-006/C4-003（plan 2524，MA-01）：启动窗口就绪门禁钉——经 doFilterInternal 入口驱动
 * （R1 Major-F1：修复=入口门禁，直呼 getFilters 的钉不变绿）；rule-23 伴生钉 = 未就绪期
 * getBeansOfType 零调用。静态翻转沿裁定⑤反射机制 + @AfterEach 恢复纪律。
 */
public class TestStartupReadinessGate_MA01 {

    private Field initializedField() throws Exception {
        Field f = CoreInitialization.class.getDeclaredField("initialized");
        f.setAccessible(true);
        return f;
    }

    @AfterEach
    public void restoreStaticState() throws Exception {
        // 恢复纪律（R1 Major-F3）：initialized=false + BeanContainer 清桥
        Field f = initializedField();
        f.set(null, false);
        try {
            Class<?> bc = Class.forName("io.nop.api.core.ioc.BeanContainer");
            java.lang.reflect.Method m = bc.getMethod("registerInstance", Object.class);
            m.setAccessible(true);
            m.invoke(null, new Object[]{null});
        } catch (Exception e) {
            // BeanContainer 不可达时跳过清桥（该测试未注入桥）
        }
    }

    private void setInitialized(boolean value) throws Exception {
        initializedField().set(null, value);
    }

    private OncePerRequestFilter newFilter() throws Exception {
        Class<?> cfg = Class.forName(
                "io.nop.spring.web.filter.SpringHttpServerFilterConfiguration");
        Object config = cfg.getDeclaredConstructor().newInstance();
        // NopHttpServerFilter 为非静态内部类——反射构造须带外层实例
        Class<?> filterCls = Class.forName(
                "io.nop.spring.web.filter.SpringHttpServerFilterConfiguration$NopHttpServerFilter");
        var ctor = filterCls.getDeclaredConstructor(cfg, boolean.class);
        ctor.setAccessible(true);
        return (OncePerRequestFilter) ctor.newInstance(config, true);
    }

    @Test
    public void uninitializedRequestGets503AndNeverTouchesFilterChain() throws Exception {
        setInitialized(false);
        OncePerRequestFilter filter = newFilter();

        jakarta.servlet.http.HttpServletResponse response =
                (jakarta.servlet.http.HttpServletResponse) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{HttpServletResponse.class},
                        (proxy, method, args) -> {
                            if ("setStatus".equals(method.getName())) {
                                return null;
                            }
                            if ("getWriter".equals(method.getName())) {
                                return new java.io.PrintWriter(new java.io.StringWriter());
                            }
                            if ("getContentType".equals(method.getName())) {
                                return null;
                            }
                            return null;
                        });
        final int[] chainCalls = {0};
        FilterChain chain = (req, res) -> chainCalls[0]++;

        jakarta.servlet.http.HttpServletRequest request =
                (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{HttpServletRequest.class},
                        (proxy, method, args) -> null);

        filter.doFilter(request, response, chain);

        assertEquals(0, chainCalls[0],
                "MA-01: uninitialized request must be 503-short-circuited before the filter chain"
                        + " (defect: empty chain frozen — auth bypass until restart)");
    }

    @Test
    public void initializedRequestPassesThroughToChain() throws Exception {
        setInitialized(true);
        // 注册 stub IBeanContainer（返回空 filter 集）——getBeansOfType 走桥
        Object stubContainer = java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{io.nop.api.core.ioc.IBeanContainer.class},
                (proxy, method, args) -> {
                    if ("getBeansOfType".equals(method.getName()))
                        return java.util.Collections.emptyMap();
                    if ("getName".equals(method.getName()))
                        return "stub";
                    if ("containsBean".equals(method.getName()))
                        return false;
                    if ("hashCode".equals(method.getName()))
                        return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName()))
                        return proxy == args[0];
                    if (method.getReturnType() == boolean.class)
                        return false;
                    return null;
                });
        Class<?> bc = Class.forName("io.nop.api.core.ioc.BeanContainer");
        bc.getMethod("registerInstance", io.nop.api.core.ioc.IBeanContainer.class)
                .invoke(null, stubContainer);
        OncePerRequestFilter filter = newFilter();

        final int[] chainCalls = {0};
        FilterChain chain = (req, res) -> chainCalls[0]++;
        jakarta.servlet.http.HttpServletRequest request =
                (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{HttpServletRequest.class},
                        (proxy, method, args) -> null);
        jakarta.servlet.http.HttpServletResponse response =
                (HttpServletResponse) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{HttpServletResponse.class},
                        (proxy, method, args) -> null);

        filter.doFilter(request, response, chain);
        // 初始化后：getBeansOfType 经宿主桥（本测试无桥注册）返回空链直通——直通也是「触达链」
        assertEquals(1, chainCalls[0], "initialized request must pass the gate");
    }

    /**
     * rule-23 伴生钉（R1 Major-F1）：门禁必须经 doFilterInternal 入口驱动——源码级结构钉
     * （行为探针无法区分「入口门禁」与「getFilters 本体守卫」，后者修复后直呼仍固化空链）。
     */
    @Test
    public void gateMustBeWiredAtFilterEntry() throws Exception {
        Path source = Path.of(
                "src/main/java/io/nop/spring/web/filter/SpringHttpServerFilterConfiguration.java");
        String code = Files.readString(source);
        int doFilter = code.indexOf("protected void doFilterInternal");
        assertTrue(doFilter > 0, "sanity: doFilterInternal must exist");
        String methodBody = code.substring(doFilter);
        int getFiltersCall = methodBody.indexOf("getFilters(sys)");
        assertTrue(getFiltersCall > 0, "sanity: getFilters call must exist in doFilterInternal");
        String beforeCall = methodBody.substring(0, getFiltersCall);
        assertTrue(beforeCall.contains("isInitialized()"),
                "MA-01: readiness gate must be wired at the filter entry BEFORE the first"
                        + " getFilters evaluation (defect: gate after evaluation = frozen bypass)");
    }
}
