/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IExtPropertyGetter;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.type.IGenericType;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_BEAN_UNKNOWN_PROP;

/**
 * 在IClassModel的基础上识别与对象序列化相关的注解，主要用于JSON序列化和EL表达式中通过反射机制来访问对象属性。
 */
public interface IBeanModel extends IBeanCollectionAdapter {
    /**
     * 是否标记了DataBean注解
     */
    boolean isDataBean();

    /**
     * 是否不可变对象。标记了Immutable注解的对象要求必须是不可变的。
     */
    boolean isImmutable();

    default boolean isAbstract() {
        return Modifier.isAbstract(getRawClass().getModifiers());
    }

    String getDescription();

    IGenericType getType();

    default String getClassName() {
        return getType().getClassName();
    }

    default boolean isCollectionLike() {
        return getType().isCollectionLike();
    }

    default boolean isListLike() {
        return getType().isListLike();
    }

    default boolean isArray() {
        return getType().isArray();
    }

    default boolean isEnum() {
        return getType().isEnum();
    }

    default boolean isMapLike() {
        return getType().isMapLike();
    }

    default Class<?> getRawClass() {
        return getType().getRawClass();
    }

    default IGenericType getComponentType() {
        return getType().getComponentType();
    }

    default StdDataType getStdDataType() {
        return getType().getStdDataType();
    }

    default boolean isSimpleType() {
        return getStdDataType().isSimpleType();
    }

    String getSerializer();

    String getDeserializer();

    default String getRawTypeName() {
        return getType().getRawTypeName();
    }

    boolean hasConstructor();

    /**
     * 根据无参数构造函数来创建实例
     */
    Object newInstance();

    /**
     * 根据标记了@JsonCreator的构造函数来创建实例
     */
    Object newInstance(Object[] args);

    IFunctionModel getFactoryMethod();

    String getSubTypeProp();

    IGenericType determineSubType(String subTypeValue);

    List<String> getConstructorPropNames();

    boolean isAllowGetExtProperty();

    boolean isAllowSetExtProperty();

    boolean isAllowMakeExtProperty();

    Map<String, IBeanPropertyModel> getPropertyModels();

    default List<String> getDefaultSelectionPropNames() {
        List<String> ret = new ArrayList<>();
        forEachProp(prop -> {
            if (prop.isSerializable() && prop.isReadable() && prop.isWritable() && !prop.isLazyLoad()) {
                ret.add(prop.getName());
            }
        });
        return ret;
    }

    default void forEachProp(Consumer<IBeanPropertyModel> action) {
        for (IBeanPropertyModel propModel : getPropertyModels().values()) {
            action.accept(propModel);
        }
    }

    default void forEachSerializableProp(Consumer<IBeanPropertyModel> action) {
        for (IBeanPropertyModel propModel : getPropertyModels().values()) {
            if (propModel.isSerializable())
                action.accept(propModel);
        }
    }

    default void forEachReadableProp(Consumer<IBeanPropertyModel> action) {
        for (IBeanPropertyModel propModel : getPropertyModels().values()) {
            if (propModel.isReadable())
                action.accept(propModel);
        }
    }

    default void forEachWritableProp(Consumer<IBeanPropertyModel> action) {
        for (IBeanPropertyModel propModel : getPropertyModels().values()) {
            if (propModel.isWritable())
                action.accept(propModel);
        }
    }

    default void forEachReadWriteProp(Consumer<IBeanPropertyModel> action) {
        for (IBeanPropertyModel propModel : getPropertyModels().values()) {
            if (propModel.isReadable() && propModel.isWritable())
                action.accept(propModel);
        }
    }

    /**
     * 根据propName或者alias来获取PropertyModel
     *
     * @param propName 属性名或者alias
     */
    IBeanPropertyModel getPropertyModel(String propName);

    default IBeanPropertyModel requirePropertyModel(String propName) {
        IBeanPropertyModel prop = getPropertyModel(propName);
        if (prop == null) {
            throw new NopException(ERR_BEAN_UNKNOWN_PROP).param(ARG_CLASS_NAME, getRawTypeName()).param(ARG_PROP_NAME,
                    propName);
        }
        return prop;
    }

    default Object getProperty(Object obj, String propName) {
        return getProperty(obj, propName, DisabledEvalScope.INSTANCE);
    }

    default Object getProperty(Object obj, String propName, IEvalScope scope) {
        IBeanPropertyModel prop = getPropertyModel(propName);
        if (prop == null) {
            if (isAllowGetExtProperty())
                return getExtProperty(obj, propName, scope);

            throw new NopException(ERR_BEAN_UNKNOWN_PROP).param(ARG_CLASS_NAME, getRawTypeName()).param(ARG_PROP_NAME,
                    propName);
        }
        return prop.getPropertyValue(obj, scope);
    }

    default Object makeProperty(Object obj, String propName, IEvalScope scope) {
        IBeanPropertyModel prop = getPropertyModel(propName);
        if (prop == null) {
            if (isAllowMakeExtProperty())
                return makeExtProperty(obj, propName, scope);

            throw new NopException(ERR_BEAN_UNKNOWN_PROP).param(ARG_CLASS_NAME, getRawTypeName());
        }
        return prop.makePropertyValue(obj, scope);
    }

    default void setProperty(Object obj, String propName, Object value) {
        setProperty(obj, propName, value, DisabledEvalScope.INSTANCE);
    }

    default void setProperty(Object obj, String propName, Object value, IEvalScope scope) {
        IBeanPropertyModel prop = getPropertyModel(propName);
        if (prop == null) {
            if (isAllowSetExtProperty()) {
                setExtProperty(obj, propName, value, scope);
            } else {
                throw new NopException(ERR_BEAN_UNKNOWN_PROP).param(ARG_CLASS_NAME, getRawTypeName())
                        .param(ARG_PROP_NAME, propName);
            }
        } else {
            prop.setPropertyValue(obj, value, scope);
        }
    }

    /**
     * 除了标准的get/set之外，
     *
     * @param propName 属性名
     */
    IGenericType getBuildPropertyType(String propName);

    void buildProperty(Object obj, String propName, Object value);

    Set<String> getExtPropertyNames(Object obj);

    boolean isAllowExtProperty(Object obj, String propName);

    IExtPropertyGetter getExtPropertyGetter();

    IPropertySetter getExtPropertySetter();

    IPropertyGetter getExtPropertyMaker();

    void setExtProperty(Object bean, String propName, Object value, IEvalScope scope);

    Object getExtProperty(Object bean, String propName, IEvalScope scope);

    Object makeExtProperty(Object bean, String propName, IEvalScope scope);

    default Object getExtProperty(Object bean, String propName) {
        return getExtProperty(bean, propName, DisabledEvalScope.INSTANCE);
    }

    default void setExtProperty(Object bean, String propName, Object value) {
        setExtProperty(bean, propName, value, DisabledEvalScope.INSTANCE);
    }
}