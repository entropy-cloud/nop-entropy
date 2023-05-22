/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.api.core.annotations.config.ConfigField;
import io.nop.api.core.annotations.core.BeanClass;
import io.nop.api.core.annotations.core.BeanDeserializer;
import io.nop.api.core.annotations.core.BeanSerializer;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.LazyLoad;
import io.nop.api.core.annotations.core.PropertyGetter;
import io.nop.api.core.annotations.core.PropertySetter;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IAnnotatedElement;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.accessor.ArrayLengthGetter;
import io.nop.core.reflect.accessor.CollectionSizeGetter;
import io.nop.core.reflect.accessor.FunctionSpecializedPropertyGetter;
import io.nop.core.reflect.accessor.FunctionSpecializedPropertySetter;
import io.nop.core.reflect.accessor.JsonAnyPropertyGetter;
import io.nop.core.reflect.accessor.JsonAnyPropertySetter;
import io.nop.core.reflect.accessor.MapPropertyAccessor;
import io.nop.core.reflect.accessor.MissingHookExtPropertyGetter;
import io.nop.core.reflect.accessor.MissingHookExtPropertyMaker;
import io.nop.core.reflect.accessor.MissingHookExtPropertySetter;
import io.nop.core.reflect.bean.BeanModel;
import io.nop.core.reflect.bean.BeanPropertyModel;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.reflect.bean.MethodBeanConstructor;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.reflect.hook.IPropMakeMissingHook;
import io.nop.core.reflect.hook.IPropSetMissingHook;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.nop.commons.util.StringHelper.beanPropName;
import static io.nop.core.CoreErrors.ARG_ALIAS;
import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_OTHER_PROP_NAME;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_PROP_ALIAS_CONFLICT;
import static io.nop.core.CoreErrors.ERR_REFLECT_CLASS_NO_DEFAULT_CONSTRUCTOR;

public class BeanModelBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(BeanModelBuilder.class);

    public IBeanModel buildFromClassModel(IClassModel classModel) {
        IGenericType type = classModel.getType();
        BeanModel beanModel = new BeanModel();
        beanModel.setType(type);
        beanModel.setDescription(classModel.getDescription());

        initSubTypes(beanModel, classModel);

        if (classModel.isAnnotationPresent(ImmutableBean.class)) {
            beanModel.setImmutable(true);
        }
        if (classModel.getRawClass().isEnum())
            beanModel.setImmutable(true);

        if (!classModel.isAbstract()) {
            IFunctionModel constructor = classModel.getConstructor(0);
            if (constructor != null) {
                beanModel.setConstructor(new MethodBeanConstructor(constructor));
            } else {
                IFunctionModel method = getConstructorMethod(classModel);
                if (method != null) {
                    beanModel.setConstructorEx(new MethodBeanConstructor(method));
                    beanModel.setConstructorPropNames(getConstructorArgNames(method));
                }
            }
        } else {
            BeanClass beanClass = classModel.getAnnotation(BeanClass.class);
            if (beanClass != null) {
                Class<?> clazz = beanClass.value();
                Constructor<?> constructor = ClassHelper.getDefaultConstructor(clazz);
                if (constructor == null || constructor.getParameterCount() > 0)
                    throw new NopException(ERR_REFLECT_CLASS_NO_DEFAULT_CONSTRUCTOR).param(ARG_CLASS_NAME,
                            clazz.getName());

                beanModel.setConstructor(new JavaConstructorBeanConstructor(constructor));
            }
        }

        // 特殊识别Map和List
        if (beanModel.getConstructor() == null) {
            if (classModel.getRawClass() == Map.class) {
                beanModel.setConstructor(LinkedHashMap::new);
            } else if (classModel.getRawClass() == List.class || classModel.getRawClass() == Collection.class) {
                beanModel.setConstructor(ArrayList::new);
            } else if (classModel.getRawClass() == Set.class) {
                beanModel.setConstructor(LinkedHashSet::new);
            }
        }

        if (classModel.isAnnotationPresent(DataBean.class) || classModel.getRawClass().isAnnotation()) {
            beanModel.setDataBean(true);
        }

        BeanSerializer serializer = classModel.getAnnotation(BeanSerializer.class);
        if (serializer != null)
            beanModel.setSerializer(serializer.value());

        BeanDeserializer deserializer = classModel.getAnnotation(BeanDeserializer.class);
        if (deserializer != null) {
            beanModel.setDeserializer(deserializer.value());
        }

        // beanModel.setBuilderMethodProvider();

        // 对Map的派生类不考虑属性
        if (beanModel.isMapLike()) {
            beanModel.setExtPropertyGetter(MapPropertyAccessor.INSTANCE);
            beanModel.setExtPropertySetter(MapPropertyAccessor.INSTANCE);
            beanModel.setExtPropertyMaker(MapPropertyAccessor.INSTANCE);
        } else {
            buildProps(beanModel, classModel);

            if (IPropGetMissingHook.class.isAssignableFrom(type.getRawClass())) {
                beanModel.setExtPropertyGetter(MissingHookExtPropertyGetter.INSTANCE);
            }
            if (IPropSetMissingHook.class.isAssignableFrom(type.getRawClass())) {
                beanModel.setExtPropertySetter(MissingHookExtPropertySetter.INSTANCE);
            }
            if (IPropMakeMissingHook.class.isAssignableFrom(type.getRawClass())) {
                beanModel.setExtPropertyMaker(MissingHookExtPropertyMaker.INSTANCE);
            } else {
                beanModel.setExtPropertyMaker(beanModel.getExtPropertyGetter());
            }
        }

        beanModel.setFactoryMethod(classModel.getFactoryMethod());

        if (classModel.isAnnotationPresent(DataBean.class) || classModel.isAnnotationPresent(ConfigBean.class))
            initDefaultValues(beanModel);

        return beanModel;
    }

    private void initDefaultValues(BeanModel beanModel) {
        if (!beanModel.isAbstract() && beanModel.getConstructorPropNames().isEmpty()) {
            Object bean = beanModel.newInstance();
            for (IBeanPropertyModel propModel : beanModel.getPropertyModels().values()) {
                // 如果具有ConfigField注解，则以注解的缺省值为准
                if (propModel.getGetter() != null && propModel.getDefaultValue() == null) {
                    Object defaultValue = propModel.getGetter()
                            .getProperty(bean, propModel.getName(), DisabledEvalScope.INSTANCE);
                    ((BeanPropertyModel) propModel).setDefaultValue(defaultValue);
                }
            }
        }
    }

    private void initSubTypes(BeanModel beanModel, IClassModel classModel) {
        JsonTypeInfo typeInfo = classModel.getAnnotation(JsonTypeInfo.class);
        if (typeInfo != null) {
            String property = typeInfo.property();
            if (!StringHelper.isEmpty(property)) {
                beanModel.setTypeProp(property);
            }
        }

        JsonSubTypes subTypes = classModel.getAnnotation(JsonSubTypes.class);
        if (subTypes != null) {
            Map<String, IGenericType> typeMap = new HashMap<>();
            for (JsonSubTypes.Type subType : subTypes.value()) {
                String name = subType.name();
                Class<?> value = subType.value();
                typeMap.put(name, ReflectionManager.instance().buildRawType(value));
            }
            beanModel.setTypeMap(CollectionHelper.immutableMap(typeMap));
        }
    }

    private BeanPropertyModel newProp(String name, IGenericType type, IPropertyGetter getter, IPropertySetter setter) {
        BeanPropertyModel prop = new BeanPropertyModel(name, type, getter, setter);
        return prop;
    }

    private List<String> getConstructorArgNames(IFunctionModel method) {
        return method.getArgs().stream().map(arg -> {
            JsonProperty prop = arg.getAnnotation(JsonProperty.class);
            if (prop != null && prop.value().length() > 0)
                return prop.value();
            return arg.getName();
        }).collect(Collectors.toList());
    }

    // 当没有缺省构造函数的情况下，查找待参数的构造函数
    private IFunctionModel getConstructorMethod(IClassModel classModel) {
        // 优先使用标记了JsonCreator的构造函数
        for (IFunctionModel method : classModel.getConstructors()) {
            if (method.getArgCount() > 0) {
                if (method.isAnnotationPresent(JsonCreator.class)) {
                    return method;
                }
            }
        }

        // 选择第一个构造函数
        for (IFunctionModel method : classModel.getConstructors()) {
            if (method.getArgCount() > 0) {
                return method;
            }
        }
        return null;
    }

    private void buildProps(BeanModel beanModel, IClassModel classModel) {
        Map<String, String> propAliases = new HashMap<>();
        Map<String, BeanPropertyModel> props = new TreeMap<>();

        Map<String, PropCandidate> candidateMap = collectPropCandidates(classModel, beanModel);

        for (IFieldModel field : classModel.getFields().values()) {
            if (field.isPublic() || field.isAnnotationPresent(JsonProperty.class)) {
                String propName = getPropName(field);
                makePropCandidate(candidateMap, propName).field = field;
            }
        }

        for (Map.Entry<String, PropCandidate> entry : candidateMap.entrySet()) {
            String propName = entry.getKey();
            PropCandidate candidate = entry.getValue();
            if (candidate.isInvalid())
                continue;
            if (candidate.propName != null)
                propName = candidate.propName;

            BeanPropertyModel prop = buildProp(propName, candidate, propAliases);
            if (candidate.field != null && candidate.field.isAnnotationPresent(JsonIgnore.class)) {
                prop.setSerializable(false);
            } else if (candidate.getMethod != null && candidate.getMethod.isAnnotationPresent(JsonIgnore.class)) {
                prop.setSerializable(false);
            }
            props.put(propName, prop);
        }
        initExtProperties(classModel, props);

        ReflectionManager.instance().enhanceBeanModel(beanModel, classModel, props, propAliases);

        beanModel.setPropertyModels((Map) CollectionHelper.immutableSortedMap(props));
        beanModel.setPropAliases(CollectionHelper.immutableMap(propAliases));
    }

    private BeanPropertyModel buildProp(String propName, PropCandidate candidate, Map<String, String> propAliases) {
        BeanPropertyModel prop = new BeanPropertyModel();
        prop.setName(propName);

        if (candidate.field != null && candidate.field.isPublic()) {
            prop.setGetter(candidate.field.getGetter());
            prop.setSetter(candidate.field.getSetter());
            prop.setType(candidate.field.getType());
        }

        if (candidate.setMethod != null) {
            prop.setSetter(new FunctionSpecializedPropertySetter(candidate.setMethod));
            prop.setType(candidate.setMethod.getArgs().get(0).getType());
        }

        if (candidate.makeMethod != null) {
            prop.setMaker(new FunctionSpecializedPropertyGetter(candidate.makeMethod.getInvoker()));
            prop.setType(candidate.makeMethod.getReturnType());
        }

        // 属性类型以get方法为准
        if (candidate.getMethod != null) {
            prop.setGetter(new FunctionSpecializedPropertyGetter(candidate.getMethod.getInvoker()));
            prop.setType(candidate.getMethod.getReturnType());
            prop.setLazyLoad(candidate.getMethod.isAnnotationPresent(LazyLoad.class));
        }

        if (candidate.isField()) {
            prop.setField(true);
        }

        if (candidate.field != null) {
            processPropAnnotations(prop, candidate.field, propAliases);
        }
        if (candidate.getMethod != null) {
            processPropAnnotations(prop, candidate.getMethod, propAliases);
        }
        if (candidate.setMethod != null) {
            processPropAnnotations(prop, candidate.setMethod, propAliases);
        }

        initConfigField(prop, candidate);
        return prop;
    }

    private void initConfigField(BeanPropertyModel prop, PropCandidate candidate) {
        ConfigField configField = candidate.getConfigField();
        if (configField != null) {
            if (!configField.name().isEmpty()) {
                prop.setConfigVarName(configField.name());
            } else {
                prop.setConfigVarName(StringHelper.camelCaseToHyphen(prop.getName()));
            }
            if (!configField.defaultValue().isEmpty()) {
                Object value = ConvertHelper.convertTo(prop.getRawClass(), configField.defaultValue(),
                        err -> new NopException(err).param(ARG_PROP_NAME, prop.getName()));
                prop.setDefaultValue(value);
            }
        }
    }


    // 对于列表和数组，增加length属性
    private void initExtProperties(IClassModel classModel, Map<String, BeanPropertyModel> propModels) {
        if (classModel.isArray()) {
            BeanPropertyModel prop = newProp(CoreConstants.FIELD_LENGTH, PredefinedGenericTypes.INT_TYPE,
                    ArrayLengthGetter.INSTANCE, null);
            propModels.put(CoreConstants.FIELD_LENGTH, prop);
        } else if (classModel.getType().isCollectionLike()) {
            if (!propModels.containsKey(CoreConstants.FIELD_LENGTH)) {
                BeanPropertyModel prop = newProp(CoreConstants.FIELD_LENGTH, PredefinedGenericTypes.INT_TYPE,
                        CollectionSizeGetter.INSTANCE, null);
                propModels.put(CoreConstants.FIELD_LENGTH, prop);
            }
        }
    }

    private String getJsonPropName(IAnnotatedElement ann) {
        JsonProperty prop = ann.getAnnotation(JsonProperty.class);
        if (prop != null && prop.value().length() > 0) {
            return prop.value();
        }
        return null;
    }

    private boolean isBooleanGetter(IFunctionModel method) {
        if (method.getReturnType().isBooleanType()) {
            return method.getName().startsWith("is") && method.getName().length() > 2;
        }
        return false;
    }

    private boolean isJsonIgnore(IAnnotatedElement ann) {
        JsonIgnore jsonIgnore = ann.getAnnotation(JsonIgnore.class);
        return jsonIgnore != null && jsonIgnore.value();
    }

    private void processPropAnnotations(BeanPropertyModel prop, IAnnotatedElement ann,
                                        Map<String, String> propAliases) {
        if (prop.getDescription() == null) {
            Description desc = ann.getAnnotation(Description.class);
            if (desc != null)
                prop.setDescription(desc.value());
        }
        JsonProperty jsonProp = ann.getAnnotation(JsonProperty.class);
        if (jsonProp != null) {
            switch (jsonProp.access()) {
                case READ_ONLY: {
                    prop.setSetter(null);
                    break;
                }
                case WRITE_ONLY:
                    prop.setGetter(null);
                    break;
                default:
            }
        }

        JsonAlias alias = ann.getAnnotation(JsonAlias.class);
        if (alias != null) {
            for (String name : alias.value()) {
                String otherPropName = propAliases.put(prop.getName(), name);
                if (otherPropName != null && !otherPropName.equals(name)) {
                    throw new NopException(ERR_REFLECT_BEAN_PROP_ALIAS_CONFLICT).param(ARG_PROP_NAME, prop.getName())
                            .param(ARG_ALIAS, alias).param(ARG_OTHER_PROP_NAME, otherPropName);
                }
            }
        }

        if (prop.getBizObjName() == null) {
            BizObjName bizObjName = ann.getAnnotation(BizObjName.class);
            if (bizObjName != null) {
                prop.setBizObjName(bizObjName.value());
            }
        }

        if (!isJsonIgnore(ann)) {
            if (prop.isReadable())
                prop.setSerializable(true);

            BeanSerializer serializer = ann.getAnnotation(BeanSerializer.class);
            if (serializer != null) {
                prop.setSerializer(serializer.value());
            }

            BeanDeserializer deserializer = ann.getAnnotation(BeanDeserializer.class);
            if (deserializer != null) {
                prop.setDeserializer(deserializer.value());
            }

            JsonInclude include = ann.getAnnotation(JsonInclude.class);
            if (include != null)
                prop.setJsonInclude(include.value());
        }
    }

    static class PropCandidate {
        IFieldModel field;
        IFunctionModel getMethod;
        IFunctionModel setMethod;
        IFunctionModel makeMethod;
        String propName;

        public ConfigField getConfigField() {
            ConfigField configField;
            if (getMethod != null) {
                configField = getMethod.getAnnotation(ConfigField.class);
                if (configField != null)
                    return configField;
            }
            if (setMethod != null) {
                configField = setMethod.getAnnotation(ConfigField.class);
                if (configField != null)
                    return configField;
            }
            if (field != null) {
                configField = field.getAnnotation(ConfigField.class);
                if (configField != null)
                    return configField;
            }
            return null;
        }

        // 如果没有get/set方法，则检查是否存在public的field
        boolean isInvalid() {
            if (getMethod == null && setMethod == null) {
                return field == null || !field.isPublic();
            }
            return false;
        }

        boolean isField() {
            return getMethod == null && setMethod == null && field != null;
        }
    }

    private Map<String, PropCandidate> collectPropCandidates(IClassModel classModel, BeanModel beanModel) {
        Map<String, PropCandidate> candidateMap = new HashMap<>();
        for (IFunctionModel method : classModel.getMethods()) {
            if (!method.isPublic())
                continue;

            if (method.getArgCount() == 0) {
                if (!method.isReturnVoid()) {
                    if (method.isAnnotationPresent(JsonAnyGetter.class)) {
                        if (Map.class.isAssignableFrom(method.getReturnClass())) {
                            if (beanModel.getExtPropertyGetter() != null)
                                LOG.warn("nop.core.reflect.bean.multiple-any-getter:className={},method={}",
                                        classModel.getClassName(), method.getName());
                            beanModel.setExtPropertyGetter(new JsonAnyPropertyGetter(method));
                            continue;
                        }
                    }

                    // getter
                    if (isGetter(method)) {
                        String propName = getPropName(method);
                        addGetMethod(makePropCandidate(candidateMap, propName), classModel, method);
                    } else if (isMaker(method)) {
                        String propName = beanPropName(method.getName().substring("make".length()));
                        addMakeMethod(makePropCandidate(candidateMap, propName), classModel, method);
                    }
                }
            } else if (method.getArgCount() == 1) {
                if (isSetter(method)) {
                    String propName = getPropName(method);
                    addSetMethod(makePropCandidate(candidateMap, propName), classModel, method);
                }
            } else if (method.getArgCount() == 2) {
                if (method.isAnnotationPresent(JsonAnySetter.class)) {
                    if (beanModel.getExtPropertySetter() != null)
                        LOG.warn("nop.core.reflect.bean.multiple-any-setter:className={},method={}",
                                classModel.getClassName(), method.getName());
                    beanModel.setExtPropertySetter(new JsonAnyPropertySetter(method));
                }
            }
        }
        return candidateMap;
    }

    private boolean isMaker(IFunctionModel method) {
        return method.getName().startsWith("make") && method.getName().length() > 4;
    }

    private boolean isSetter(IFunctionModel method) {
        if (method.isAnnotationPresent(PropertySetter.class))
            return true;

        if (!method.getName().startsWith("set") || method.getName().length() <= 3)
            return false;

        if (method.getReturnType().isVoidType() || isSelfReturn(method))
            return true;

        return false;
    }

    private boolean isSelfReturn(IFunctionModel method) {
        return method.getReturnClass().isAssignableFrom(method.getDeclaringClass());
    }

    private boolean isGetter(IFunctionModel method) {
        if (method.isAnnotationPresent(PropertyGetter.class))
            return true;
        if (isBooleanGetter(method))
            return true;
        if (method.getName().startsWith("get") && method.getName().length() > 3)
            return true;
        return false;
    }

    private void addGetMethod(PropCandidate candidate, IClassModel classModel, IFunctionModel getMethod) {
        if (candidate.getMethod != null) {
            LOG.warn("nop.core.reflect.bean.multiple-getter-for-same-prop:className={},methodA={},methodB={}",
                    classModel.getClassName(), candidate.getMethod.getName(), getMethod.getName());
        } else {
            candidate.getMethod = getMethod;
            String name = this.getJsonPropName(getMethod);
            if (name != null)
                candidate.propName = name;
        }
    }

    private void addSetMethod(PropCandidate candidate, IClassModel classModel, IFunctionModel method) {
        if (candidate.setMethod != null) {
            LOG.warn("nop.core.reflect.bean.multiple-setter-for-same-prop:className={},methodA={},methodB={}",
                    classModel.getClassName(), candidate.setMethod.getName(), method.getName());
        } else {
            candidate.setMethod = method;
            String name = this.getJsonPropName(method);
            if (name != null)
                candidate.propName = name;
        }
    }

    private void addMakeMethod(PropCandidate candidate, IClassModel classModel, IFunctionModel method) {
        if (candidate.makeMethod != null) {
            LOG.warn("nop.core.reflect.bean.multiple-maker-for-same-prop:className={},methodA={},methodB={}",
                    classModel.getClassName(), candidate.makeMethod.getName(), method.getName());
        } else {
            candidate.makeMethod = method;
        }
    }

    private PropCandidate makePropCandidate(Map<String, PropCandidate> candidateMap, String name) {
        PropCandidate candidate = candidateMap.computeIfAbsent(name, k -> new PropCandidate());
        return candidate;
    }

    private String getPropName(IFieldModel field) {
        String name = this.getJsonPropName(field);
        if (name != null)
            return name;
        return field.getName();
    }

    private String getPropName(IFunctionModel method) {
        String name = method.getName();
        if (name.startsWith("get"))
            return beanPropName(name.substring("get".length()));
        if (name.startsWith("is"))
            return beanPropName(name.substring("is".length()));
        if (name.startsWith("set")) {
            return beanPropName(name.substring("set".length()));
        }
        return name;
    }
}