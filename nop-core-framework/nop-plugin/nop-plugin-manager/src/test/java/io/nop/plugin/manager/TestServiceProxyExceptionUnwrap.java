package io.nop.plugin.manager;

import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.test.IFailingTool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ServiceProxy 业务异常解包回归测试：
 * 修复前 Handler 直接返回 method.invoke 的结果，目标方法抛出的异常以
 * InvocationTargetException 形式泄漏（接口未声明受检异常时被 JDK 代理再包成
 * UndeclaredThrowableException），调用方的异常类型/错误码分支全部失效；
 * 修复后解包 getTargetException 原样上抛。
 *
 * <p>equals/hashCode 走目标对象的既定行为属 W4 Phase 1 裁定保留，不在本测试范围。</p>
 */
public class TestServiceProxyExceptionUnwrap {

    private static final String PLUGIN_ID = "/nop/plugin/test/service-proxy.plugin.xml";

    private PluginManagerImpl manager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        manager = new PluginManagerImpl();
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            try {
                plugin.unload();
            } catch (RuntimeException ignore) {
                // 清理容错：测试内已去激活/卸载
            }
        }
    }

    @Test
    public void testBusinessExceptionUnwrappedFromProxy() {
        IPlugin plugin = manager.loadPlugin(PLUGIN_ID);
        IFailingTool tool = plugin.getService(IFailingTool.class);

        // 修复前：UndeclaredThrowableException(cause=InvocationTargetException(cause=IllegalStateException))
        IllegalStateException e = assertThrows(IllegalStateException.class, tool::fail,
                "目标方法的业务异常必须原样上抛（解包 InvocationTargetException）");
        assertEquals("tool boom", e.getMessage(), "原始异常信息保留");
    }
}
