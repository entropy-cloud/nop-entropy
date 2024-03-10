/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.loader;

import io.nop.api.core.annotations.aop.AopProxy;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.aop.AopCodeGenerator;
import io.nop.core.reflect.aop.IAopProxy;
import io.nop.core.reflect.aop.IMethodInterceptor;
import io.nop.ioc.IocConstants;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanDefinition;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.model.BeanInterceptorModel;
import io.nop.ioc.model.BeanModel;
import io.nop.ioc.model.BeanPointcutModel;
import io.nop.ioc.model.BeanValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.ioc.IocErrors.ARG_BEAN;
import static io.nop.ioc.IocErrors.ARG_BEAN_CLASS;
import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ARG_BEAN_TYPE;
import static io.nop.ioc.IocErrors.ARG_CLASS_NAME;
import static io.nop.ioc.IocErrors.ARG_INTERCEPTOR_BEAN;
import static io.nop.ioc.IocErrors.ARG_INTERCEPTOR_NAME;
import static io.nop.ioc.IocErrors.ERR_IOC_AOP_BEAN_NO_BEAN_CLASS;
import static io.nop.ioc.IocErrors.ERR_IOC_AOP_CLASS_NO_CONSTRUCTOR;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_AOP_PROXY_NOT_GENERATED;
import static io.nop.ioc.IocErrors.ERR_IOC_INTERCEPTOR_BEAN_NO_POINTCUT;
import static io.nop.ioc.IocErrors.ERR_IOC_LOAD_CLASS_FAIL_FOR_BEAN;
import static io.nop.ioc.IocErrors.ERR_IOC_NOT_ALLOW_BEAN_ID_TYPE;
import static io.nop.ioc.IocErrors.ERR_IOC_POINTCUT_CLASS_NOT_ANNOTATION;
import static io.nop.ioc.IocErrors.ERR_IOC_PROXY_BEAN_CLASS_NOT_INVOCATION_HANDLER;
import static io.nop.ioc.IocErrors.ERR_IOC_PROXY_BEAN_TYPE_NOT_INTERFACE;

/**
 * 处理ioc:interceptor和ioc:pointcut
 */
public class AopBeanProcessor {
    static final Logger LOG = LoggerFactory.getLogger(AopBeanProcessor.class);

    private final IClassLoader classLoader;

    private final Set<BeanDefinition> interceptorBeans = new HashSet<>();
    private final Set<BeanDefinition> interceptedBeans = new HashSet<>();

    private final Map<String, BeanDefinition> interceptorMap = new HashMap<>();

    private Map<String, BeanDefinition> enabledBeans;
    //private Set<BeanDefinition> optionalBeans;

    /**
     * annotation ==> 处理该annotation的interceptor
     */
    private final Map<Class<?>, Set<BeanDefinition>> annForInterceptors = new HashMap<>();

    // private final Map<Class<?>, Set<Method>> aopInterceptedMethods = new HashMap<>();

    // private final Map<Class<?>, Class<?>> aopProxyClasses = new HashMap<>();

    // private JaninoClassLoader aopClassLoader;
    //
    // private File rootDir;

    public AopBeanProcessor(IClassLoader classLoader) {
        this.classLoader = classLoader;
        // this.rootDir = new File(".");
    }

    public void process(Map<String, BeanDefinition> enabledBeans, Set<BeanDefinition> optionalBeans,
                        IBeanContainer parentContainer) {
        this.enabledBeans = enabledBeans;
        //this.optionalBeans = optionalBeans;
        collectBeans(enabledBeans.values());
        collectBeans(optionalBeans);

        addParentInterceptors(parentContainer);

        Set<Class<?>> annClasses = loadInterceptedAnnotations();
        applyInterceptors(annClasses);
    }

    void addParentInterceptors(IBeanContainer parentContainer) {
        if (parentContainer instanceof IBeanContainerImplementor) {
            Map<String, IBeanDefinition> beans = ((IBeanContainerImplementor) parentContainer)
                    .getBeanDefinitionsByType(IMethodInterceptor.class);

            for (IBeanDefinition bean : beans.values()) {
                if (bean.getIocPointcut() != null) {
                    interceptorMap.put(bean.getId(), (BeanDefinition) bean);
                    interceptorBeans.add((BeanDefinition) bean);
                }
            }
        }
    }

    /**
     * 收集所有interceptor配置，以及所有有可能需要应用interceptor的bean
     */
    void collectBeans(Collection<BeanDefinition> beans) {
        for (BeanDefinition bean : beans) {
            if (bean.isAbstract() || bean.isDisabled())
                continue;

            BeanValue beanModel = bean.getBeanModel();
            checkProxy(bean);
            normalizeClassName(bean);

            if (beanModel.hasIocInterceptors() || beanModel.isIocAop()) {
                if (StringHelper.isEmpty(beanModel.getClassName()))
                    throw new NopException(ERR_IOC_AOP_BEAN_NO_BEAN_CLASS).param(ARG_BEAN, bean).param(ARG_BEAN_NAME,
                            bean.getId());

                interceptedBeans.add(bean);
            }

            if (beanModel instanceof BeanModel) {
                BeanModel bm = (BeanModel) beanModel;
                if (bm.getIocPointcut() != null) {
                    interceptorBeans.add(bean);
                    interceptorMap.put(bean.getId(), bean);
                }
            }
        }
    }

    /**
     * 如果没有设置class属性，则试图从ioc:type设置中获取
     */
    void normalizeClassName(BeanDefinition bean) {
        BeanValue beanModel = bean.getBeanModel();
        String className = beanModel.getClassName();
        if (StringHelper.isEmpty(className)) {
            Set<String> types = beanModel.getIocType();
            if (CollectionHelper.getSize(types) == 1) {
                if (types.contains(IocConstants.CONFIG_BEAN_ID)) {
                    if (!(beanModel instanceof BeanModel))
                        throw new NopException(ERR_IOC_NOT_ALLOW_BEAN_ID_TYPE).param(ARG_BEAN, bean)
                                .param(ARG_BEAN_NAME, bean.getId());

                    types = Collections.singleton(bean.getId());
                    beanModel.setIocType(types);
                }
                className = CollectionHelper.first(types);
                beanModel.setClassName(className);
            }
        }
    }

    /**
     * 标记了ioc:proxy=true，则ioc:type设置必须全部是接口，且bean的结果类型必须是InvocationHandler
     */
    void checkProxy(BeanDefinition bean) {
        BeanValue beanModel = bean.getBeanModel();
        if (beanModel.isIocProxy()) {
            Class<?> resultType = null;
            if (bean.getFactoryMethod() != null) {
                resultType = bean.getFactoryMethod().getReturnClass();
            } else if (bean.getBeanClass() != null) {
                resultType = bean.getBeanClass();
            }

            if (resultType != null) {
                if (!InvocationHandler.class.isAssignableFrom(resultType))
                    throw new NopException(ERR_IOC_PROXY_BEAN_CLASS_NOT_INVOCATION_HANDLER).param(ARG_BEAN, bean)
                            .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN_CLASS, resultType);
            }

            List<Class<?>> types = bean.getBeanTypes();
            if (types == null)
                return;

            for (Class<?> type : types) {
                if (!type.isInterface()) {
                    throw new NopException(ERR_IOC_PROXY_BEAN_TYPE_NOT_INTERFACE).param(ARG_BEAN, bean)
                            .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN_TYPE, type);
                }
            }
        }
    }

    Set<Class<?>> loadInterceptedAnnotations() {
        Set<Class<?>> annClasses = new HashSet<>();

        for (BeanDefinition bean : this.interceptorBeans) {
            BeanModel beanModel = (BeanModel) bean.getBeanModel();
            BeanPointcutModel pointcut = beanModel.getIocPointcut();
            Set<String> annClassNames = pointcut.getAnnotations();

            List<Class<?>> classes = loadPointcutClasses(bean, annClassNames);
            pointcut.setAnnotationClasses(classes);
            annClasses.addAll(classes);

            for (Class<?> clazz : classes) {
                annForInterceptors.computeIfAbsent(clazz, k -> new HashSet<>()).add(bean);
            }
        }
        return annClasses;
    }

    List<Class<?>> loadPointcutClasses(BeanDefinition bean, Set<String> classNames) {
        List<Class<?>> classes = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            try {
                Class<?> clazz = classLoader.loadClass(className);
                if (!Annotation.class.isAssignableFrom(clazz))
                    throw new NopException(ERR_IOC_POINTCUT_CLASS_NOT_ANNOTATION).param(ARG_BEAN, bean)
                            .param(ARG_BEAN_NAME, bean.getId()).param(ARG_CLASS_NAME, className);
                classes.add(clazz);

            } catch (ClassNotFoundException e) {
                if (bean.isDisabled()) {
                    LOG.debug("nop.ioc.load-class-fail-for-disabled-bean:bean={},class={}", bean, className, e);
                } else {
                    throw new NopException(ERR_IOC_LOAD_CLASS_FAIL_FOR_BEAN).param(ARG_BEAN, bean)
                            .param(ARG_BEAN_NAME, bean.getId()).param(ARG_CLASS_NAME, className);
                }
            }
        }
        return classes;
    }

    /**
     * 根据pointcut设置查找需要被拦截的bean，并为它们追加interceptor配置
     *
     * @param annClasses 有可能被处理的annotation
     */
    void applyInterceptors(Set<Class<?>> annClasses) {
        for (BeanDefinition bean : interceptedBeans) {
            BeanValue beanModel = bean.getBeanModel();

            Class<?> aopClass = loadAopClass(bean.getBeanClass());
            if (aopClass == null) {
                if (beanModel.hasIocInterceptors())
                    throw new NopException(ERR_IOC_BEAN_AOP_PROXY_NOT_GENERATED).source(bean)
                            .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN, bean);

                bean.setIntercepted(false);
                beanModel.setIocAop(false);
                continue;
            }

            Set<Class<?>> pointcutAnns = new HashSet<>();
            Set<BeanDefinition> interceptorBeans = new HashSet<>();
            if (beanModel.hasIocInterceptors()) {
                // 使用明确指定的interceptor
                for (BeanInterceptorModel interceptor : beanModel.getIocInterceptors()) {
                    BeanDefinition interceptorBean = getInterceptor(interceptor, bean);
                    if (interceptorBean != null) {
                        interceptorBeans.add(interceptorBean);
                        BeanModel refModel = (BeanModel) interceptorBean.getBeanModel();
                        pointcutAnns.addAll(refModel.getIocPointcut().getAnnotationClasses());
                    }
                }
            }

            if (beanModel.isIocAop()) {
                pointcutAnns.addAll(annClasses);
            }

            if (!isUsedAnnotation(aopClass, pointcutAnns)) {
                bean.setIntercepted(false);
                beanModel.setIocAop(false);
                continue;
            }

            if (ReflectionManager.instance().isRecordForNativeImage()) {
                for (Class<?> annClass : pointcutAnns) {
                    ReflectionManager.instance().logReflectClass(annClass);
                }
            }

            bean.setIntercepted(true);

            if (beanModel.isIocAop()) {
                for (Class<?> clazz : pointcutAnns) {
                    Set<BeanDefinition> itBeans = this.annForInterceptors.get(clazz);
                    if (itBeans != null) {
                        for (BeanDefinition itBean : itBeans) {
                            if (interceptorBeans.add(itBean)) {
                                BeanInterceptorModel model = new BeanInterceptorModel();
                                model.setBean(itBean.getId());
                                model.setOrder(itBean.getPointcut().getOrder());
                                beanModel.addIocInterceptor(model);
                            }
                        }
                    }
                }
            }

            beanModel.getIocInterceptors().sort(Comparator.comparing(BeanInterceptorModel::getOrder));

            changeConstructor(bean, aopClass);
        }
    }

    BeanDefinition getInterceptor(BeanInterceptorModel interceptor, BeanDefinition bean) {
        BeanDefinition interceptorBean = enabledBeans.get(interceptor.getBean());
        if (interceptorBean == null)
            interceptorBean = interceptorMap.get(interceptor.getBean());

        if (interceptorBean != null) {
            BeanModel model = (BeanModel) interceptorBean.getBeanModel();
            if (model.getIocPointcut() == null)
                throw new NopException(ERR_IOC_INTERCEPTOR_BEAN_NO_POINTCUT).source(interceptor).param(ARG_BEAN, bean)
                        .param(ARG_BEAN_NAME, bean.getId()).param(ARG_INTERCEPTOR_NAME, interceptor.getBean())
                        .param(ARG_INTERCEPTOR_BEAN, interceptorBean);
        }
        return interceptorBean;
    }

    /**
     * 将bean的构造器修改为使用AopProxy对象的构造函数
     */
    void changeConstructor(BeanDefinition bean, Class<?> aopClass) {
        LOG.info("nop.ioc.use-aop-proxy:bean={}", bean);

        BeanValue beanModel = bean.getBeanModel();
        int argCount = beanModel.getConstructorArgCount();
        IClassModel classModel = ReflectionManager.instance().getClassModel(aopClass);
        IFunctionModel constructor = classModel.getConstructor(argCount);
        if (constructor == null)
            throw new NopException(ERR_IOC_AOP_CLASS_NO_CONSTRUCTOR).param(ARG_BEAN, bean)
                    .param(ARG_BEAN_NAME, bean.getId()).param(ARG_CLASS_NAME, aopClass.getCanonicalName());

        bean.setConstructor(constructor);
    }

    /**
     * 判断AopClass是否识别指定的注解
     */
    boolean isUsedAnnotation(Class<?> aopClass, Collection<?> annotations) {
        AopProxy proxy = aopClass.getAnnotation(AopProxy.class);
        if (proxy == null)
            return false;
        for (Class<?> clazz : proxy.value()) {
            if (annotations.contains(clazz)) {
                return true;
            }
        }
        return false;
    }

    // JaninoClassLoader makeAopClassLoader() {
    // if (aopClassLoader != null)
    // return aopClassLoader;
    //
    // aopClassLoader = JaninoClassLoader.createForProject(ClassHelper.getDefaultClassLoader(), rootDir,
    // BaseTestCase.isTestRunning());
    // return aopClassLoader;
    // }

    // void generateAopCode(Class<?> beanClass) {
    // AopCodeGenerator gen = new AopCodeGenerator();
    // Set<Method> methods = aopInterceptedMethods.get(beanClass);
    // String code = gen.buildForMethods(beanClass, new ArrayList<>(methods));
    //
    // String aopClassName = AopCodeGenerator.getAopClassName(beanClass);
    // File file = JaninoClassLoader.getSourceFile(rootDir, aopClassName, BaseTestCase.isTestRunning());
    //
    // if (isSourceChanged(file, code)) {
    // FileHelper.writeText(file, code, null);
    // }
    // }
    //
    // private boolean isSourceChanged(File file, String source) {
    // if (file.length() > 0) {
    // String text = FileHelper.readText(file, null);
    // if (text.equals(source))
    // return false;
    // }
    // return true;
    // }

    Class<?> loadAopClass(Class<?> clazz) {
        if (IAopProxy.class.isAssignableFrom(clazz))
            return clazz;

        try {
            String aopClassName = AopCodeGenerator.getAopClassName(clazz);
            return classLoader.loadClass(aopClassName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}