/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IAnnotatedElement;
import io.nop.core.reflect.impl.AnnotationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自动识别spring内置的FactoryBean等接口，加强与spring bean的兼容性
 *
 * @author canonical_entropy@163.com
 */
class SpringBeanSupport {
    static final Logger LOG = LoggerFactory.getLogger(SpringBeanSupport.class);

    public static final String METHOD_AFTER_PROPERTIES_SET = "afterPropertiesSet";
    public static final String METHOD_DESTROY = "destroy";
    public static final String METHOD_GET_OBJECT = "getObject";
    public static final String METHOD_GET_OBJECT_TYPE = "getObjectType";
    public static final String METHOD_IS_SINGLETON = "isSingleton";

    public static final String ATTR_REQUIRED = "required";
    public static final String ATTR_NAME = "name";

    Class<?> factoryBeanClass;
    Class<?> initializingBeanClass;
    Class<?> disposableBeanClass;

    Class autowiredClass;
    Class resourceClass;
    Class injectClass;
    Class qualifierClass;
    Class javaxQualifierClass;
    Class valueClass;

    public SpringBeanSupport(IClassLoader classLoader) {
        factoryBeanClass = loadClass(classLoader, "org.springframework.beans.factory.FactoryBean");
        initializingBeanClass = loadClass(classLoader, "org.springframework.beans.factory.InitializingBean");
        disposableBeanClass = loadClass(classLoader, "org.springframework.beans.factory.DisposableBean");
        autowiredClass = loadClass(classLoader, "org.springframework.beans.factory.annotation.Autowired");
        resourceClass = loadClass(classLoader, "jakarta.annotation.Resource");
        injectClass = loadClass(classLoader, "jakarta.inject.Inject");
        javaxQualifierClass = loadClass(classLoader, "jakarta.inject.Named");
        qualifierClass = loadClass(classLoader, "org.springframework.beans.factory.annotation.Qualifier");
        valueClass = loadClass(classLoader, "org.springframework.beans.factory.annotation.Value");
    }

    public boolean isInjectPresent(IAnnotatedElement element) {
        if (injectClass == null)
            return false;
        return element.isAnnotationPresent(injectClass);
    }

    public AnnotationData getResourceAnnotation(IAnnotatedElement element) {
        return element.getAnnotationData(resourceClass);
    }

    public AnnotationData getAutowiredAnnotation(IAnnotatedElement element) {
        return element.getAnnotationData(autowiredClass);
    }

    public String getQualifier(IAnnotatedElement element) {
        String name = getAnnotationValue(element, javaxQualifierClass);
        if (name == null) {
            name = getAnnotationValue(element, qualifierClass);
        }
        return name;
    }

    public String getValue(IAnnotatedElement element) {
        InjectValue value = element.getAnnotation(InjectValue.class);
        if (value != null)
            return value.value();

        if (valueClass != null)
            return getAnnotationValue(element, valueClass);
        return null;
    }

    String getAnnotationValue(IAnnotatedElement element, Class annClass) {
        String value = (String) element.getAnnotationValue(annClass);
        if (StringHelper.isEmpty(value))
            value = null;
        return value;
    }

    public boolean isFactoryBean(Class<?> clazz) {
        if (factoryBeanClass == null)
            return false;
        return factoryBeanClass.isAssignableFrom(clazz);
    }

    public boolean isInitializingBean(Class<?> clazz) {
        if (initializingBeanClass == null)
            return false;
        return initializingBeanClass.isAssignableFrom(clazz);
    }

    public boolean isDisposableBean(Class<?> clazz) {
        if (disposableBeanClass == null)
            return false;
        return disposableBeanClass.isAssignableFrom(clazz);
    }

    static Class<?> loadClass(IClassLoader classLoader, String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            LOG.debug("ioc.not_find_spring_class:{}", className);
            return null;
        }
    }
}