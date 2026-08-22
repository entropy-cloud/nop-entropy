package io.nop.plugin.manager.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.ioc.IocErrors;
import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.plugin.manager.PluginManagerErrors.ARG_BEAN_TYPE;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES;

/**
 * {@link IPluginScope} 实现——激活期作用域句柄（设计文档 01-architecture-baseline.md §7.4）。
 *
 * <p>effect 注册/回退自管理：{@link #close()} 按 LIFO（后注册先回退）执行全部 disposer 并清空列表
 * （quiescence），单个 disposer 抛错记录并继续；close 幂等；close 后 {@link #effect(Disposable)} 抛异常。
 * 每个实例每次 activate 使用全新 scope 实例（close 后不可复用）。
 *
 * <p>getService/getServices 委托实例容器：多候选规则（primary 优先 → 唯一实现 → 多候选且无唯一
 * primary 抛明确异常，不静默返回集合）——复用 IoC 的 {@code getBeanByType} 候选解析
 * （{@code BeanFinder.findByType}：primary→唯一→多候选抛错），多候选错误翻译为插件层错误码。
 */
public class PluginScopeImpl implements IPluginScope {
    static final Logger LOG = LoggerFactory.getLogger(PluginScopeImpl.class);

    private final IBeanContainer container;

    private final List<Disposable> effects = new ArrayList<>();
    private boolean closed;

    public PluginScopeImpl(IBeanContainer container) {
        this.container = container;
    }

    @Override
    public Disposable effect(Disposable d) {
        if (d == null)
            throw new IllegalArgumentException("effect must not be null");
        synchronized (effects) {
            if (closed) {
                throw new IllegalStateException("scope is closed: effect registration is not allowed");
            }
            effects.add(d);
        }
        return () -> {
            synchronized (effects) {
                if (effects.remove(d)) {
                    d.dispose();
                }
            }
        };
    }

    @Override
    public List<Disposable> effects() {
        synchronized (effects) {
            return Collections.unmodifiableList(new ArrayList<>(effects));
        }
    }

    @Override
    public void close() {
        List<Disposable> toDispose;
        synchronized (effects) {
            if (closed)
                return;
            closed = true;
            toDispose = new ArrayList<>(effects);
            effects.clear();
        }
        // LIFO：后注册先回退
        for (int i = toDispose.size() - 1; i >= 0; i--) {
            try {
                toDispose.get(i).dispose();
            } catch (Exception e) {
                LOG.error("nop.plugin.effect-dispose-fail", e);
            }
        }
    }

    @Override
    public <T> T getService(Class<T> serviceType) {
        try {
            return container.getBeanByType(serviceType);
        } catch (NopException e) {
            if (e.getErrorCode() != null
                    && e.getErrorCode().equals(IocErrors.ERR_IOC_MULTIPLE_BEAN_WITH_TYPE.getErrorCode())) {
                throw new NopException(ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES)
                        .param(ARG_BEAN_TYPE, serviceType.getName())
                        .cause(e);
            }
            throw e;
        }
    }

    @Override
    public <T> Collection<T> getServices(Class<T> serviceType) {
        Map<String, T> beans = container.getBeansOfType(serviceType);
        return beans.values();
    }

    /**
     * 按 bean id 解析服务（代理集合的按调用重新解析路径；bean 不存在抛容器标准异常）。
     *
     * <p>仅实现层使用（插件级生命周期代理 handler），不属 {@link IPluginScope} 公开契约。
     */
    @SuppressWarnings("unchecked")
    <T> T getServiceBean(String beanId) {
        return (T) container.getBean(beanId);
    }

    /**
     * 按类型返回 bean id → bean 映射（插件级 getServices 代理包装的候选键来源；
     * 重激活后按 id 重新解析）。
     *
     * <p>仅实现层使用，不属 {@link IPluginScope} 公开契约。
     */
    <T> Map<String, T> getServiceBeans(Class<T> serviceType) {
        return container.getBeansOfType(serviceType);
    }
}
