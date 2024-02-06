/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.impl;

import io.nop.api.core.annotations.config.OnConfigRefresh;
import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IAnnotatedElement;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.impl.AnnotationData;
import io.nop.core.reflect.impl.ClassLoaderRawTypeResolver;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.parse.GenericTypeParser;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class DefaultBeanClassIntrospection implements IBeanClassIntrospection {
    private final IClassLoader classLoader;
    private final SpringBeanSupport springBeanSupport;

    public DefaultBeanClassIntrospection(IClassLoader classLoader) {
        this.classLoader = classLoader;
        this.springBeanSupport = new SpringBeanSupport(classLoader);
    }

    @Override
    public IFunctionModel getInitMethod(IClassModel classModel) {
        if (springBeanSupport.isInitializingBean(classModel.getRawClass())) {
            return classModel.getMethod(SpringBeanSupport.METHOD_AFTER_PROPERTIES_SET, 0);
        }
        return classModel.getMethodWithAnnotation(PostConstruct.class, 0);
    }

    @Override
    public IFunctionModel getDestroyMethod(IClassModel classModel) {
        if (springBeanSupport.isDisposableBean(classModel.getRawClass())) {
            return classModel.getMethod(SpringBeanSupport.METHOD_DESTROY, 0);
        }

        IFunctionModel fn = classModel.getMethodWithAnnotation(PreDestroy.class, 0);
        if (fn == null) {
            // Spring和Quarkus都会自动调用AutoCloseable接口上的close方法
            if (AutoCloseable.class.isAssignableFrom(classModel.getRawClass())) {
                fn = classModel.getMethod("close", 0);
            }
        }
        return fn;
    }

    @Override
    public IFunctionModel getBeanMethod(IClassModel classModel) {
        if (springBeanSupport.isFactoryBean(classModel.getRawClass())) {
            return classModel.getMethod(SpringBeanSupport.METHOD_GET_OBJECT, 0);
        }
        for (IFunctionModel fn : classModel.getMethods()) {
            if (fn.getArgCount() == 0) {
                if (fn.isAnnotationPresent(BeanMethod.class))
                    return fn;
            }
        }
        return null;
    }

    @Override
    public IFunctionModel getRefreshConfigMethod(IClassModel classModel) {
        if (IConfigRefreshable.class.isAssignableFrom(classModel.getRawClass())) {
            return classModel.getMethod("refreshConfig", 0);
        }
        return classModel.getMethodWithAnnotation(OnConfigRefresh.class, 0);
    }

    public boolean isAllowedConfigVarType(IGenericType type) {
        Class<?> targetType = type.getRawClass();
        if (targetType == Class.class)
            return true;
        if (targetType == IGenericType.class)
            return true;
        if (targetType == String[].class)
            return true;
        if (targetType == URL.class)
            return true;
        if (targetType == File.class)
            return true;
        if (targetType == IResource.class)
            return true;

        if (type.isCollectionLike() && type.getComponentType() == PredefinedGenericTypes.STRING_TYPE)
            return true;

        return StdDataType.fromJavaClass(targetType).isSimpleType();
    }

    @Override
    public Object convertTo(Class<?> targetType, Object value, Function<ErrorCode, NopException> errorFactory) {
        if (targetType == Class.class && value instanceof IGenericType) {
            return ((IGenericType) value).getRawClass();
        }

        if (targetType == IGenericType.class && value instanceof Class) {
            return ReflectionManager.instance().buildRawType((Class<?>) value);
        }

        if (value instanceof String) {
            // value = replaceVar(value.toString());

            if (targetType == File.class) {
                return new File(value.toString());
            }
            if (targetType == URL.class) {
                try {
                    return new URL(value.toString());
                } catch (Exception e2) {
                    throw NopException.adapt(e2);
                }
            }
            if (targetType == String[].class) {
                return StringHelper.stripedSplit(value.toString(), ',').toArray(StringHelper.EMPTY_STRINGS);
            }
            if (targetType == List.class) {
                return StringHelper.stripedSplit(value.toString(), ',');
            }

            if (targetType == Set.class) {
                return ConvertHelper.toCsvSet(value, errorFactory);
            }

            if (targetType == IResource.class)
                return VirtualFileSystem.instance().getResource(value.toString());

            if (targetType == Class.class) {
                try {
                    return classLoader.loadClass(value.toString());
                } catch (Throwable e2) {
                    throw NopException.adapt(e2);
                }
            }

            if (targetType == IGenericType.class) {
                return new GenericTypeParser().rawTypeResolver(new ClassLoaderRawTypeResolver(classLoader))
                        .parseFromText(null, value.toString());
            }
        }
        return ConvertHelper.convertTo(targetType, value, errorFactory);
    }

    @Override
    public BeanInjectInfo getPropertyInject(String propName, IFunctionModel propModel) {
        IFunctionArgument argModel = propModel.getArgs().get(0);
        return getInjectInfo(propName, argModel.getType(), propModel, argModel);
    }

    @Override
    public BeanInjectInfo getFieldInject(IFieldModel field) {
        return getInjectInfo(field.getName(), field.getType(), field, null);
    }

    @Override
    public BeanInjectInfo getArgumentInject(IFunctionArgument argModel) {
        BeanInjectInfo injectInfo = getInjectInfo(argModel.getName(), argModel.getType(), argModel, null);
        if (injectInfo == null) {
            // 缺省情况下按照类型自动注入
            return new BeanInjectInfo(null, argModel.getType(), null, false);
        }
        return injectInfo;
    }

    BeanInjectInfo getInjectInfo(String name, IGenericType type, IAnnotatedElement element, IAnnotatedElement argModel) {
        BeanInjectInfo inject = getAutowiredInject(type, element, argModel);

        if (inject == null)
            inject = getValueInject(element);

        if (inject == null)
            inject = getResourceInject(name, element);

        return inject;
    }

    BeanInjectInfo getResourceInject(String name, IAnnotatedElement element) {
        AnnotationData ann = springBeanSupport.getResourceAnnotation(element);
        if (ann != null) {
            String beanName = (String) ann.getProperty(SpringBeanSupport.ATTR_NAME);
            if (StringHelper.isEmpty(beanName)) {
                beanName = name;
            }
            boolean optional = element.isAnnotationPresent(Nullable.class);
            return new BeanInjectInfo(beanName, null, null, optional);
        }
        return null;
    }

    BeanInjectInfo getValueInject(IAnnotatedElement element) {
        String value = springBeanSupport.getValue(element);
        if (value == null)
            return null;
        return new BeanInjectInfo(null, null, value, false);
    }

    BeanInjectInfo getAutowiredInject(IGenericType type, IAnnotatedElement element, IAnnotatedElement argModel) {
        String beanName = springBeanSupport.getQualifier(element);
        if (beanName == null && argModel != null)
            beanName = springBeanSupport.getQualifier(argModel);

        boolean inject = springBeanSupport.isInjectPresent(element);
        if (inject) {
            boolean optional = (argModel != null ? argModel : element).isAnnotationPresent(Nullable.class);
            if (beanName == null) {
                return new BeanInjectInfo(null, type, null, optional);
            }
            return new BeanInjectInfo(beanName, null, null, optional);
        }

        AnnotationData autowired = springBeanSupport.getAutowiredAnnotation(element);
        if (autowired == null)
            return null;

        boolean required = ConvertHelper.toPrimitiveBoolean(autowired.getProperty(SpringBeanSupport.ATTR_REQUIRED),
                true, NopException::new);
        if (beanName == null) {
            return new BeanInjectInfo(null, type, null, !required);
        }
        return new BeanInjectInfo(beanName, null, null, !required);
    }
}