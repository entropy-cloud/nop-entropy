/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.accessor.PropertyAccessor;
import io.nop.core.reflect.accessor.PropertyAccessorBuilder;
import io.nop.core.type.IGenericType;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_UNKNOWN_BEAN_CLASS;

@GlobalInstance
public class BeanTool {
    private static IBeanTool _instance = new BeanToolImpl(ReflectionManager.instance());

    public static IBeanTool instance() {
        return _instance;
    }

    public static void registerInstance(IBeanTool tool) {
        _instance = tool;
    }

    public static IGenericType getGenericType(Type type) {
        return instance().getGenericType(type);
    }

    public static <T> T castBeanToType(Object src, Type targetType) {
        return (T) instance().castBeanToType(src, getGenericType(targetType), BeanCopyOptions.DEFAULT);
    }

    public static <T> List<T> castListItemToType(List<?> list, Type targetType) {
        if (list == null)
            return null;

        IBeanTool beanTool = instance();
        IGenericType itemType = getGenericType(targetType);
        BeanCopyOptions options = BeanCopyOptions.DEFAULT;

        List<T> ret = new ArrayList<>(list.size());
        for (Object item : list) {
            T value = (T) beanTool.castBeanToType(item, itemType, options);
            ret.add(value);
        }
        return ret;
    }

    public static <T> T buildBean(Object src, Type targetType) {
        return (T) instance().buildBean(src, getGenericType(targetType), BeanCopyOptions.DEFAULT);
    }

    public static <T> T buildBeanFromTreeBean(ITreeBean src, Type targetType) {
        return (T) instance().buildBeanFromTreeBean(src, getGenericType(targetType), BeanCopyOptions.DEFAULT);
    }

    public static void copyBean(Object src, Object target, Type targetType, boolean deep) {
        instance().copyBean(src, target, getGenericType(targetType), deep, BeanCopyOptions.DEFAULT);
    }

    public static void copyProperties(Object src, Object target) {
        copyBean(src, target, target.getClass(), false);
    }

    public static Object pluckSelected(Object src, FieldSelectionBean selection) {
        return instance().pluckSelected(src, selection);
    }

    public static Object getByIndex(Object bean, int index) {
        return instance().getByIndex(bean, index);
    }

    public static void setByIndex(Object bean, int index, Object value) {
        instance().setByIndex(bean, index, value);
    }

    /**
     * 解析a.b.c这种复杂的对象属性。其中a使用IVariableScope.getValue(name)来获取，
     * 然后b.c看作是对象a的属性，使用BeanTool.getProperty(obj,propName)来获取属性值。
     *
     * @param propPath 对象属性路径
     * @return 当对象不存在或者某一级属性不存在时返回null
     */
    public static Object getValueByPath(IVariableScope scope, String propPath) {
        int pos = propPath.indexOf('.');
        if (pos < 0)
            return scope.getValue(propPath);
        String name = propPath.substring(0, pos);
        Object value = scope.getValue(name);
        if (value == null)
            return null;

        String prop = propPath.substring(pos + 1);
        return getComplexProperty(value, prop);
    }

    public static Object getProperty(Object bean, String propName) {
        if (bean == null)
            return null;
        return instance().getProperty(bean, propName);
    }

    public static boolean hasProperty(Object bean, String propName) {
        if (bean == null)
            return false;
        return instance().hasProperty(bean, propName);
    }

    public static void setProperty(Object bean, String propName, Object propValue) {
        Guard.notNull(bean, "bean");
        instance().setProperty(bean, propName, propValue);
    }

    public static Object makeProperty(Object bean, String propName, Supplier<?> constructor) {
        return BeanPropHelper.makeSimple(instance(), bean, propName, constructor);
    }

    public static Object getComplexProperty(Object bean, String propPath) {
        if (bean == null)
            return null;

        return BeanPropHelper.getIn(instance(), bean, propPath);
    }

    public static Object tryGetComplexProperty(Object bean, String propPath) {
        if (bean == null)
            return null;

        return BeanPropHelper.tryGetIn(instance(), bean, propPath);
    }

    public static Object tryGetProperty(Object bean, String propName) {
        if (bean == null)
            return null;
        return BeanPropHelper.tryGetSimple(instance(), bean, propName);
    }

    public static Object makeComplexProperty(Object bean, String propPath, Supplier<?> constructor) {
        if (bean == null)
            return null;
        return BeanPropHelper.makeIn(instance(), bean, propPath, constructor);
    }

    public static void setComplexProperty(Object bean, String propPath, Object value) {
        Guard.notNull(bean, "null bean");
        BeanPropHelper.setIn(instance(), bean, propPath, value);
    }

    public static PropertyAccessor buildAccessor(IGenericType beanType, SourceLocation loc, String propPath) {
        return new PropertyAccessorBuilder(ReflectionManager.instance()).parseAndBuild(beanType, loc, propPath);
    }

    public static Map<String, PropertyAccessor> buildAccessors(IGenericType beanType, Collection<String> propPaths) {
        Map<String, PropertyAccessor> ret = CollectionHelper.newHashMap(propPaths.size());
        for (String propPath : propPaths) {
            PropertyAccessor accessor = buildAccessor(beanType, null, propPath);
            ret.put(propPath, accessor);
        }
        return ret;
    }

    /**
     * 返回本对象和关联对象的属性。例如
     *
     * <pre>
     * class A {
     *     String a;
     *     String c;
     * }
     *
     * class B {
     *     String b;
     *     A a;
     * }
     *
     * </pre>
     * <p>
     * B的complexPropNames集合返回["b","a.a","a.c"]
     */
    public static Set<String> getReadableComplexPropNames(Type type) {
        return getReadableComplexPropNames(getGenericType(type));
    }

    public static Set<String> getReadableComplexPropNames(IGenericType clazz) {
        return getReadableComplexPropNames(ReflectionManager.instance(), clazz);
    }

    private static Set<String> getReadableComplexPropNames(IBeanModelManager beanModelManager, IGenericType typeInfo) {
        Set<String> ret = new TreeSet<>();
        IBeanModel beanModel = beanModelManager.getBeanModelForType(typeInfo);
        if (beanModel == null)
            throw new NopException(ERR_REFLECT_UNKNOWN_BEAN_CLASS).param(ARG_CLASS_NAME, typeInfo.getClassName());
        collectComplexPropNames(beanModelManager, beanModel, typeInfo, null, ret);
        return ret;
    }

    private static void collectComplexPropNames(IBeanModelManager beanModelManager, IBeanModel beanModel,
                                                IGenericType typeInfo, String prefix, Set<String> set) {
        for (IBeanPropertyModel propModel : beanModel.getPropertyModels().values()) {
            if (!propModel.isReadable())
                continue;
            String name = prefix == null ? propModel.getName() : prefix + '.' + propModel.getName();
            set.add(propModel.getName());
            if (!propModel.isSimpleType()) {
                IBeanModel propBeanModel = beanModelManager.getBeanModelForClass(propModel.getRawClass());
                if (propBeanModel == null)
                    throw new NopException(ERR_REFLECT_UNKNOWN_BEAN_CLASS).param(ARG_CLASS_NAME,
                            propModel.getRawTypeName());
                IGenericType propType = propBeanModel.getType().refine(beanModel.getType(), typeInfo);
                collectComplexPropNames(beanModelManager, propBeanModel, propType, name, set);
            }
        }
    }

}