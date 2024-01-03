/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.impl;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.aop.IAopProxy;
import io.nop.core.reflect.aop.IMethodInterceptor;
import io.nop.ioc.IocConstants;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanDefinition;
import io.nop.ioc.api.IBeanPointcut;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.model.BeanConditionModel;
import io.nop.ioc.model.BeanInterceptorModel;
import io.nop.ioc.model.BeanModel;
import io.nop.ioc.model.BeanPointcutModel;
import io.nop.ioc.model.BeanValue;
import io.nop.xlang.expr.ExprConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public class BeanDefinition implements IBeanDefinition {
    static final Logger LOG = LoggerFactory.getLogger(BeanDefinition.class);

    public static int STATUS_UNRESOLVED = 0;

    public static int STATUS_RESOLVING = 1;
    public static int STATUS_RESOLVED = 2;
    private final BeanValue beanModel;

    private List<Class<?>> beanTypes;

    private Class<?> beanClass;

    private List<IBeanPropValueResolver> constructorArgs = Collections.emptyList();

    private boolean constructorAutowired;

    private final Map<String, BeanProperty> props = new TreeMap<>();

    private int status = STATUS_UNRESOLVED;

    private Function<IBeanContainerImplementor, ?> supplier;
    private IFunctionModel constructor;

    private IFunctionModel initMethod;
    private IFunctionModel destroyMethod;
    private IFunctionModel delayMethod;
    private IFunctionModel restartMethod;

    private IFunctionModel factoryMethod;

    private IFunctionModel refreshConfigMethod;

    private IFunctionModel beanMethod;

    private String trace;

    private Cancellable subscriptionCleanup;

    private boolean intercepted;
    private boolean removed;

    private Set<String> nextBeans = Collections.emptySet();

    /**
     * 如果当前bean为config对象，这里记录所有依赖本配置对象的所有bean的id
     */
    private Set<String> configDependants = Collections.emptySet();

    public BeanDefinition(BeanValue beanModel) {
        this.beanModel = beanModel;
    }

    public Set<String> getConfigDependants() {
        return configDependants;
    }

    public void addConfigDependant(String beanId) {
        if (configDependants.isEmpty()) {
            configDependants = new LinkedHashSet<>();
        }
        configDependants.add(beanId);
    }

    public boolean isIocForceInit(){
        return beanModel.isIocForceInit();
    }

    public Function<IBeanContainerImplementor, ?> getSupplier() {
        return supplier;
    }

    public void setSupplier(Function<IBeanContainerImplementor, ?> supplier) {
        this.supplier = supplier;
    }

    public boolean isIntercepted() {
        return intercepted;
    }

    public boolean isConstructorAutowired() {
        return constructorAutowired;
    }

    public void setConstructorAutowired(boolean constructorAutowired) {
        this.constructorAutowired = constructorAutowired;
    }

    public boolean isRemoved(){
        return removed;
    }

    public void setRemoved(boolean removed){
        this.removed = removed;
    }

    public boolean isIocDefault(){
        if(beanModel instanceof BeanModel)
            return ((BeanModel) beanModel).isIocDefault();
        return false;
    }

    public Set<String> getNextBeans() {
        return nextBeans;
    }

    public void addNextBean(String nextBean) {
        if (nextBeans.isEmpty()) {
            nextBeans = new LinkedHashSet<>();
        }
        nextBeans.add(nextBean);
    }

    public boolean containsTag(String tag) {
        if (beanModel instanceof BeanModel)
            return ((BeanModel) beanModel).containsTag(tag);
        return false;
    }

    public boolean isConcreteClass() {
        if (containsTag(IocConstants.BEAN_TAG_PROXY))
            return false;

        for (Class<?> beanType : beanTypes) {
            if (!beanType.isInterface()) {
                return true;
            }
        }
        return false;
    }

    public BeanPointcutModel getPointcut() {
        if (beanModel instanceof BeanModel)
            return ((BeanModel) beanModel).getIocPointcut();
        return null;
    }

    public void setIntercepted(boolean intercepted) {
        this.intercepted = intercepted;
    }

    public boolean hasDelayMethod() {
        return delayMethod != null || beanModel.getIocDelayStart() != null;
    }

    public boolean isLazyInit() {
        return Boolean.TRUE.equals(beanModel.getLazyInit());
    }

    public void collectDepends(Set<String> deps) {
        for (IBeanPropValueResolver resolver : constructorArgs) {
            resolver.collectDepends(deps);
        }

        for (BeanProperty prop : props.values()) {
            prop.getValueResolver().collectDepends(deps);
        }
    }

    public boolean isIocAllowOverride() {
        if (beanModel instanceof BeanModel)
            return ((BeanModel) beanModel).isIocAllowOverride();
        return false;
    }

    public boolean isEmbedded() {
        return !(beanModel instanceof BeanModel);
    }

    public boolean isAbstract() {
        if (beanModel instanceof BeanModel) {
            return ((BeanModel) beanModel).isAbstract();
        }
        return false;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isDisabled() {
        BeanConditionModel conditionModel = beanModel.getIocCondition();
        if (conditionModel == null)
            return false;
        return conditionModel.isDisabled();
    }

    public boolean isPrimary() {
        if (beanModel instanceof BeanModel)
            return ((BeanModel) beanModel).isPrimary();
        return false;
    }

    @Override
    public IBeanPointcut getIocPointcut() {
        if (beanModel instanceof BeanModel)
            return ((BeanModel) beanModel).getIocPointcut();
        return null;
    }

    public BeanValue getBeanModel() {
        return beanModel;
    }

    public BeanConditionModel getCondition() {
        return beanModel.getIocCondition();
    }

    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    public String toString() {
        return "bean[id=" + getId() + ",class=" + beanClass + "]@" + getLocation()
                + (trace == null ? "" : "<==" + trace);
    }

    public String getId() {
        if (beanModel instanceof BeanModel)
            return ((BeanModel) beanModel).getId();
        return beanModel.getEmbeddedId();
    }

    public Set<String> getNames() {
        if (beanModel instanceof BeanModel)
            return ((BeanModel) beanModel).getName();
        return null;
    }

    @Override
    public SourceLocation getLocation() {
        return beanModel.getLocation();
    }

    @Override
    public String getScope() {
        return beanModel.getScope();
    }

    public boolean isSingleton() {
        return ApiConstants.BEAN_SCOPE_SINGLETON.equals(getScope());
    }

    public boolean isPrototype() {
        return ApiConstants.BEAN_SCOPE_PROTOTYPE.equals(getScope());
    }

    public IFunctionModel getInitMethod() {
        return initMethod;
    }

    public void setInitMethod(IFunctionModel initMethod) {
        this.initMethod = initMethod;
    }

    public IFunctionModel getDestroyMethod() {
        return destroyMethod;
    }

    public void setDestroyMethod(IFunctionModel destroyMethod) {
        this.destroyMethod = destroyMethod;
    }

    public IFunctionModel getDelayMethod() {
        return delayMethod;
    }

    public void setDelayMethod(IFunctionModel delayMethod) {
        this.delayMethod = delayMethod;
    }

    public IFunctionModel getRestartMethod() {
        return restartMethod;
    }

    public void setRestartMethod(IFunctionModel restartMethod) {
        this.restartMethod = restartMethod;
    }

    public IFunctionModel getFactoryMethod() {
        return factoryMethod;
    }

    public void setFactoryMethod(IFunctionModel factoryMethod) {
        this.factoryMethod = factoryMethod;
    }

    public IFunctionModel getRefreshConfigMethod() {
        return refreshConfigMethod;
    }

    public void setRefreshConfigMethod(IFunctionModel refreshConfigMethod) {
        this.refreshConfigMethod = refreshConfigMethod;
    }

    public List<IBeanPropValueResolver> getConstructorArgs() {
        return constructorArgs;
    }

    public void setConstructorArgs(List<IBeanPropValueResolver> constructorArgs) {
        this.constructorArgs = constructorArgs;
    }

    public Map<String, BeanProperty> getProps() {
        return props;
    }

    public IFunctionModel getBeanMethod() {
        return beanMethod;
    }

    public void setBeanMethod(IFunctionModel beanMethod) {
        this.beanMethod = beanMethod;
    }

    public IFunctionModel getConstructor() {
        return constructor;
    }

    public void setConstructor(IFunctionModel constructor) {
        this.constructor = constructor;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public void setBeanClass(Class<?> beanClass) {
        this.beanClass = beanClass;
    }

    @Override
    public List<Class<?>> getBeanTypes() {
        return beanTypes;
    }

    public void setBeanTypes(List<Class<?>> types) {
        this.beanTypes = types;
    }

    public void addProp(String propName, BeanProperty prop) {
        this.props.put(propName, prop);
    }

    public BeanProperty getProp(String propName) {
        return props.get(propName);
    }

    void runXpl(IEvalAction xpl, Object bean, IBeanContainer container, IBeanScope beanScope) {
        if (xpl != null) {
            IEvalScope scope = beanScope.getEvalScope().duplicate();
            scope.setLocalValue(null, ExprConstants.SYS_VAR_THIS, bean);
            scope.setLocalValue(null, IocConstants.SYS_VAR_BEAN_DEF, this);
            scope.setLocalValue(null, IocConstants.SYS_VAR_BEAN_CONTAINER, container);
            xpl.invoke(scope);
        }
    }

    Object[] getConstructorArgs(IBeanScope scope, IBeanContainerImplementor container) {
        Object[] args = IFunctionModel.EMPTY_ARGS;
        if (constructorArgs.size() > 0) {
            args = new Object[constructorArgs.size()];
            int i, n = constructorArgs.size();
            for (i = 0; i < n; i++) {
                Object value = constructorArgs.get(i).resolveValue(container, scope);
                value = container.getClassIntrospection().convertTo(constructor.getArgRawTypes()[i], value,
                        NopException::new);
                args[i] = value;
            }
        }
        return args;
    }

    private Object newInstance(IBeanScope scope, IBeanContainerImplementor container) {
        if (supplier != null)
            return supplier.apply(container);

        Object[] args = getConstructorArgs(scope, container);
        if (this.factoryMethod != null) {
            if (beanModel.getFactoryBean() != null) {
                Object factory = container.getBean(beanModel.getFactoryBean(), true);
                return this.factoryMethod.invoke(factory, args, scope == null ? null : scope.getEvalScope());
            }
            return this.factoryMethod.invoke(null, args, scope == null ? null : scope.getEvalScope());
        }
        return constructor.invoke(null, args, scope == null ? null : scope.getEvalScope());
    }

    public Object newObject(IBeanScope scope, IBeanContainerImplementor container) {
        try {
            Object bean = newInstance(scope, container);
            LOG.debug("ioc.new-object:{}", this);

            ProducedBeanInstance producedBeanInstance = null;
            if (beanMethod != null) {
                producedBeanInstance = new ProducedBeanInstance(bean);
            } else if (beanModel.isIocProxy()) {
                producedBeanInstance = new ProducedBeanInstance(bean);
                producedBeanInstance.setBean(createProxy(new DelegateInvocationHandler((InvocationHandler) bean)));
            }

            if (scope != null) {
                scope.add(getId(), producedBeanInstance != null ? producedBeanInstance : bean);
            }

            for (Map.Entry<String, BeanProperty> entry : props.entrySet()) {
                BeanProperty prop = entry.getValue();
                prop.assignToObject(bean, entry.getKey(), container, scope);
            }

            if (getBeanModel().getDependsOn() != null) {
                // 执行init方法之前，确保依赖的bean已经被创建。
                // 当全部lazy启动的时候，即使是拓扑排序靠前的bean也不会被初始化，所以需要手工指定依赖关系
                for (String depend : getBeanModel().getDependsOn()) {
                    container.getBean(depend, true);
                }
            }

            addInterceptors(bean, scope, container);

            subscribeConfigChange(bean, scope, container);

            if (initMethod != null)
                initMethod.invoke(bean, IEvalFunction.EMPTY_ARGS, DisabledEvalScope.INSTANCE);

            runXpl(beanModel.getIocInit(), bean, container, scope);

            if (beanMethod != null && producedBeanInstance != null) {
                Object instance = beanMethod.call0(bean, DisabledEvalScope.INSTANCE);
                if (beanModel.isIocProxy()) {
                    Object proxy = createProxy((InvocationHandler) instance);
                    // 更新aop代理
                    producedBeanInstance.setBean(proxy);
                } else {
                    producedBeanInstance.setBean(instance);
                }
            }

            return producedBeanInstance != null ? producedBeanInstance : bean;
        } catch (Exception e) {
            if (e instanceof NopException) {
                NopException nopErr = (NopException) e;
                nopErr.addXplStack("createBean:" + getId() + "|" + getLocation());
            }
            LOG.error("nop.ioc.create-bean-fail:bean={}", this, e);
            throw NopException.adapt(e);
        }
    }

    void addInterceptors(Object bean, IBeanScope scope, IBeanContainerImplementor container) {
        if (intercepted && beanModel.hasIocInterceptors()) {
            IMethodInterceptor[] interceptors = new IMethodInterceptor[beanModel.getIocInterceptors().size()];
            int i = 0;
            for (BeanInterceptorModel interceptorModel : beanModel.getIocInterceptors()) {
                IMethodInterceptor interceptor = (IMethodInterceptor) container.getBean(interceptorModel.getBean(),
                        true);
                interceptors[i++] = interceptor;
            }
            ((IAopProxy) bean).$$aop_interceptors(interceptors);
        }
    }

    private Object createProxy(InvocationHandler bean) {
        Class[] ifs = beanTypes.toArray(new Class[beanTypes.size()]);
        return ReflectionManager.instance().newProxyInstance(ifs, (InvocationHandler) bean);
    }

    public void initProps(Object bean, IBeanContainerImplementor container, IBeanScope scope) {
        for (Map.Entry<String, BeanProperty> entry : props.entrySet()) {
            BeanProperty prop = entry.getValue();
            prop.assignToObject(bean, entry.getKey(), container, scope);
        }
        if (initMethod != null)
            initMethod.invoke(bean, IEvalFunction.EMPTY_ARGS, DisabledEvalScope.INSTANCE);

        runXpl(beanModel.getIocInit(), bean, container, scope);
    }

    void destroyBean(Object bean, IBeanScope beanScope, IBeanContainerImplementor container) {
        if (bean != null) {
            if (bean instanceof ProducedBeanInstance) {
                bean = ((ProducedBeanInstance) bean).getCreatedBean();
            }
            LOG.info("ioc.destroy-bean:{}", this);
            if (subscriptionCleanup != null) {
                subscriptionCleanup.cancel();
                subscriptionCleanup = null;
            }
            if (destroyMethod != null)
                destroyMethod.call0(bean, DisabledEvalScope.INSTANCE);
            runXpl(beanModel.getIocDestroy(), bean, container, beanScope);
        }
    }

    void runDelayMethod(Object bean, IBeanScope beanScope, IBeanContainerImplementor container) {
        if (bean != null) {
            if (bean instanceof ProducedBeanInstance) {
                bean = ((ProducedBeanInstance) bean).getCreatedBean();
            }
            if (delayMethod != null) {
                delayMethod.call0(bean, DisabledEvalScope.INSTANCE);
            }
            runXpl(beanModel.getIocDelayStart(), bean, container, beanScope);
        }
    }

    Object getBeanInstance(Object bean, boolean onlyProducer) {
        if (getBeanMethod() != null || beanModel.isIocProxy()) {
            ProducedBeanInstance producer = (ProducedBeanInstance) bean;
            if (onlyProducer) {
                return producer.getCreatedBean();
            }
            return producer.getBean();
        }
        return bean;
    }

    private void subscribeConfigChange(Object bean, IBeanScope scope, IBeanContainerImplementor container) {
        IConfigProvider configProvider = container.getConfigProvider();
        boolean reactiveConfig = isSingleton() && configProvider != null;

        if (reactiveConfig) {
            IConfigChangeListener refreshListener = null;
            if (refreshConfigMethod != null || beanModel.getIocRefreshConfig() != null || !configDependants.isEmpty()) {
                refreshListener = (p, vars) -> {
                    onRefreshConfig(bean, container, scope);
                };
            }

            for (Map.Entry<String, BeanProperty> entry : props.entrySet()) {
                BeanProperty prop = entry.getValue();
                if (!prop.getReactiveConfigVars().isEmpty()) {
                    // configVar发生变化的时候，会触发prop更新
                    IConfigChangeListener propChangeListener = (p, vars) -> {
                        prop.assignToObject(bean, entry.getKey(), container, scope);
                    };

                    for (String configVar : prop.getReactiveConfigVars()) {
                        if (subscriptionCleanup == null)
                            subscriptionCleanup = new Cancellable();
                        Runnable cleanup = configProvider.subscribeChange(configVar, propChangeListener);
                        subscriptionCleanup.appendOnCancelTask(cleanup);
                        subscriptionCleanup
                                .appendOnCancelTask(configProvider.subscribeChange(configVar, refreshListener));
                    }
                }
            }
        }
    }

    public void onRefreshConfig(Object bean, IBeanContainerImplementor container, IBeanScope scope) {
        if (refreshConfigMethod != null) {
            refreshConfigMethod.call0(bean, DisabledEvalScope.INSTANCE);
        }
        runXpl(beanModel.getIocRefreshConfig(), bean, container, scope);

        if (!configDependants.isEmpty()) {
            for (String beanId : configDependants) {
                container.refreshConfig(beanId);
            }
        }
    }
}