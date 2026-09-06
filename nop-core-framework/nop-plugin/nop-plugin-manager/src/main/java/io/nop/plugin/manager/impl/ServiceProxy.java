package io.nop.plugin.manager.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.ReflectionManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_BEAN_ID;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_BEAN_TYPE;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_SERVICE_CANDIDATE_NOT_FOUND;

/**
 * 激活态绑定代理（设计文档 01-architecture-baseline.md §7.1）：{@code IPlugin.getService(s)}
 * 返回的代理在插件 deactivate 完成后调用必须快速失败（抛 INACTIVE），不悬空。
 *
 * <p>代理语义（W4 Phase 1 裁定保留，绑定对象从实例改插件激活态）：
 * <ul>
 *   <li>实现 = {@link ReflectionManager#newProxyInstance}（GraalVM 原生镜像注册代理类，仓库惯例）。</li>
 *   <li><b>按调用重新解析</b>：handler 每次调用从插件<b>当前</b>激活 scope 重新解析目标 bean
 *       （不捕获一次性引用）——重新 activate 后同一代理引用恢复可用；scope 为 null
 *       （deactivate 完成后已置空）按 INACTIVE 处理，不 NPE。</li>
 *   <li>{@code equals/hashCode/toString} 与业务方法同规则（需 ACTIVATED，非激活态抛 INACTIVE——
 *   一致且无悬空）。</li>
 *   <li>集合代理（{@code beanId != null}）以 bean id 为候选键，重激活后按 id 重新解析；
 *   解析不到候选抛明确异常（防御性失败，不静默返回 null）。</li>
 * </ul>
 */
final class ServiceProxy {

    private ServiceProxy() {
    }

    @SuppressWarnings("unchecked")
    static <T> T newInstance(VfsPluginDefinition plugin, Class<T> serviceType) {
        return (T) ReflectionManager.instance().newProxyInstance(new Class[]{serviceType},
                new Handler(plugin, serviceType, null));
    }

    @SuppressWarnings("unchecked")
    static <T> T newInstance(VfsPluginDefinition plugin, Class<T> serviceType, String beanId) {
        return (T) ReflectionManager.instance().newProxyInstance(new Class[]{serviceType},
                new Handler(plugin, serviceType, beanId));
    }

    static class Handler implements InvocationHandler {
        private final VfsPluginDefinition plugin;
        private final Class<?> serviceType;
        private final String beanId;

        Handler(VfsPluginDefinition plugin, Class<?> serviceType, String beanId) {
            this.plugin = plugin;
            this.serviceType = serviceType;
            this.beanId = beanId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 激活态检查：ACTIVATED 且 scope 非空（deactivate 完成后 scope 已置空 → INACTIVE，不 NPE）
            PluginScopeImpl scope = (PluginScopeImpl) plugin.getScope();
            if (scope == null) {
                throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, plugin.getPluginId());
            }
            Object target = resolveTarget(scope);
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                // 反射调用必须解包：目标方法抛出的业务异常（错误码/异常类型语义）原样上抛，
                // 不得以 InvocationTargetException 形式泄漏给调用方（接口未声明受检异常时
                // 还会被 JDK 代理再包成 UndeclaredThrowableException）
                throw e.getTargetException();
            }
        }

        private Object resolveTarget(PluginScopeImpl scope) {
            if (beanId == null) {
                return scope.getService(serviceType);
            }
            Object bean = scope.getServiceBean(beanId);
            if (bean == null) {
                throw new NopException(ERR_PLUGIN_SERVICE_CANDIDATE_NOT_FOUND)
                        .param(ARG_BEAN_TYPE, serviceType.getName())
                        .param(ARG_BEAN_ID, beanId);
            }
            return bean;
        }
    }
}
