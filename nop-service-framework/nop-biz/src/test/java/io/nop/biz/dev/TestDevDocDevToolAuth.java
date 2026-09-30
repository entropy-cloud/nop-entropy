package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.directive.Auth;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G5-13-01：DevDoc/DevTool 是debug部署下暴露的内省与破坏性维护面（导出IoC容器XML、全部配置值、
 * 全量schema、清空全局缓存），平台语义下 auth==null 即公开访问（GraphQLActionAuthChecker 对
 * auth==null 返回true），因此全部公开操作必须要求admin角色，对齐同包DevStatBizModel先例。
 * <p>
 * 修复前：两个BizModel的全部方法均无@Auth注解，本测试在修复前失败（先红后绿）。
 */
public class TestDevDocDevToolAuth {

    @Test
    public void testAllDevDocOperationsRequireAdminRole() {
        List<String> bizOps = publicBizOperations(DevDocBizModel.class);
        assertTrue(bizOps.size() >= 7, "DevDocBizModel should expose at least 7 biz operations, found: " + bizOps);
        assertAllRequireAdmin(DevDocBizModel.class, bizOps);
    }

    @Test
    public void testAllDevToolOperationsRequireAdminRole() {
        List<String> bizOps = publicBizOperations(DevToolBizModel.class);
        assertEquals(2, bizOps.size(), "DevToolBizModel should expose exactly 2 biz mutations: " + bizOps);
        assertAllRequireAdmin(DevToolBizModel.class, bizOps);
    }

    private static List<String> publicBizOperations(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()))
                continue;
            if (method.isAnnotationPresent(BizQuery.class) || method.isAnnotationPresent(BizMutation.class)
                    || method.isAnnotationPresent(BizLoader.class)) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static void assertAllRequireAdmin(Class<?> clazz, List<String> opNames) {
        for (String name : opNames) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals(name) || !Modifier.isPublic(method.getModifiers()))
                    continue;
                Auth auth = method.getAnnotation(Auth.class);
                assertNotNull(auth, clazz.getSimpleName() + "." + method.getName() + " must declare @Auth");
                assertEquals("admin", auth.roles(),
                        clazz.getSimpleName() + "." + method.getName() + " must require admin role");
            }
        }
    }
}
