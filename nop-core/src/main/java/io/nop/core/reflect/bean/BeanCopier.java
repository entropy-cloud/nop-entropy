/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.CloneHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IDeepCloneable;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ARG_SRC_LENGTH;
import static io.nop.core.CoreErrors.ARG_SRC_TYPE;
import static io.nop.core.CoreErrors.ARG_TARGET_LENGTH;
import static io.nop.core.CoreErrors.ARG_TARGET_TYPE;
import static io.nop.core.CoreErrors.ARG_TYPE_VALUE;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_NO_CLASS_FOR_TYPE;
import static io.nop.core.CoreErrors.ERR_REFLECT_CAST_VALUE_TO_TARGET_TYPE_FAIL;
import static io.nop.core.CoreErrors.ERR_REFLECT_COPY_BEAN_ARRAY_LENGTH_NOT_MATCH;
import static io.nop.core.CoreErrors.ERR_REFLECT_NOT_COLLECTION_TYPE;
import static io.nop.core.CoreErrors.ERR_REFLECT_UNKNOWN_BEAN_PROP;

public class BeanCopier implements IBeanCopier {
    private static final Logger LOG = LoggerFactory.getLogger(BeanCopier.class);

    public static final BeanCopier INSTANCE = new BeanCopier();

    public void copyBean(@Nonnull Object src, @Nonnull Object target, @Nonnull IGenericType targetType, boolean deep,
                         BeanCopyOptions options) {
        Guard.notNull(src, "src");
        Guard.notNull(target, "target");
        Guard.notNull(targetType, "targetType");

        if (options == null)
            options = BeanCopyOptions.DEFAULT;

        _copyBean(src, target, targetType, deep, options.getSelection(), options);
    }

    private void _copyBean(Object src, Object target, IGenericType targetType, boolean deep,
                           FieldSelectionBean selection, BeanCopyOptions options) {
        Class<?> targetClass = target.getClass();

        if (options.isAllowLoop()) {
            options.addMappedObj(src, target);
        }

        if (targetClass.isArray()) {
            _copyToArray(src, target, targetType, deep, selection, options);
        } else if (target instanceof Collection) {
            _copyToCollection(src, (Collection) target, targetType, deep, selection, options);
        } else if (src.getClass().isArray() || src instanceof Collection) {
            throw new NopException(ERR_REFLECT_NOT_COLLECTION_TYPE).param(ARG_CLASS_NAME, target.getClass().getName());
        } else if (!options.isAllowMapExt() && src instanceof Map) {
            IBeanModel targetModel = getTargetBeanModel(src, targetType, options);
            _copyMapToBean((Map<String, Object>) src, target, targetModel, null, targetType, deep, selection, options);
        } else {
            IBeanModel targetModel = getTargetBeanModel(src, targetType, options);
            _copyObj(src, target, targetModel, null, targetType, deep, selection, options);
        }
    }

    private IBeanCollectionAdapter getCollectionAdapter(Object src) {
        if (src.getClass().isArray()) {
            return ArrayBeanCollectionAdapter.INSTANCE;
        }
        if (!(src instanceof Collection))
            throw new NopException(ERR_REFLECT_NOT_COLLECTION_TYPE).param(ARG_CLASS_NAME, src.getClass().getName());
        return BeanCollectionAdapter.INSTANCE;
    }

    private void _copyToArray(Object src, Object target, IGenericType targetType, boolean deep,
                              FieldSelectionBean selection, BeanCopyOptions options) {
        IBeanCollectionAdapter srcAdapter = getCollectionAdapter(src);
        int n = srcAdapter.getSize(src);
        if (n != Array.getLength(target))
            throw new NopException(ERR_REFLECT_COPY_BEAN_ARRAY_LENGTH_NOT_MATCH).param(ARG_SRC_LENGTH, n)
                    .param(ARG_TARGET_LENGTH, Array.getLength(target));

        Class<?> srcCompType = srcAdapter.getComponentType(src);

        // 数组元素类型一致
        if (srcCompType == targetType.getComponentType().getRawClass()) {
            if (!deep || isSimpleType(srcCompType)) {
                System.arraycopy(src, 0, target, 0, 0);
                return;
            }
        }

        IGenericType genericType = targetType.getComponentType();
        srcAdapter.forEach(src, (v, i) -> {
            if (v != null) {
                v = beanToType(deep, v, genericType, selection, options);
            } else if (targetType.isPrimitive()) {
                v = ConvertHelper.getDefault(targetType.getRawClass());
            }
            Array.set(target, i, v);
        });
    }

    private void _copyToCollection(Object src, Collection<Object> target, IGenericType targetType, boolean deep,
                                   FieldSelectionBean selection, BeanCopyOptions options) {

        IBeanCollectionAdapter srcAdapter = getCollectionAdapter(src);

        Class<?> srcCompType = srcAdapter.getComponentType(src);

        int size = srcAdapter.getSize(src);
        if (size == 0) {
            target.clear();
            return;
        }

        // 数组元素类型一致
        if (srcCompType == targetType.getComponentType().getRawClass()) {
            if (!deep || isSimpleType(srcCompType)) {
                srcAdapter.forEach(src, (v, i) -> {
                    target.add(v);
                });
                return;
            }
        }

        IGenericType genericType = targetType.getComponentType();
        srcAdapter.forEach(src, (v, i) -> {
            if (v != null) {
                v = beanToType(deep, v, genericType, selection, options);
            } else if (targetType.isPrimitive()) {
                v = ConvertHelper.getDefault(targetType.getRawClass());
            }
            target.add(v);
        });
    }

    private void _copyMapToBean(Map<String, Object> src, Object target, IBeanModel targetModel,
                                Collection<String> ignorePropNames, IGenericType targetType, boolean deep, FieldSelectionBean selection,
                                BeanCopyOptions options) {
        if (selection != null) {
            for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
                String propName = entry.getKey();
                if (ignorePropNames != null && ignorePropNames.contains(propName))
                    continue;

                FieldSelectionBean subSelection = entry.getValue();
                String srcName = propName;
                if (subSelection != null)
                    srcName = subSelection.getName();

                Object value = src.get(srcName);
                setFieldValue(targetModel, target, propName, value, targetType, deep, subSelection, options);
            }
        } else {
            src.forEach((propName, value) -> {
                setFieldValue(targetModel, target, propName, value, targetType, deep, null, options);
            });
        }
    }

    private void _copyObj(Object src, Object target, IBeanModel targetModel, Collection<String> ignorePropNames,
                          IGenericType targetType, boolean deep, FieldSelectionBean selection, BeanCopyOptions options) {
        IBeanModel srcModel = options.getBeanModelManager().getBeanModelForClass(src.getClass());
        IEvalScope scope = options.getEvalScope();

        if (selection != null) {
            for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
                String propName = entry.getKey();
                if (ignorePropNames != null && ignorePropNames.contains(propName))
                    continue;

                FieldSelectionBean subSelection = entry.getValue();
                String srcName = propName;
                if (subSelection != null)
                    srcName = subSelection.getName();

                Object value = getFieldValue(srcModel, src, srcName, scope);
                setFieldValue(targetModel, target, propName, value, targetType, deep, subSelection, options);
            }
        } else {
            srcModel.forEachReadableProp(propModel -> {
                if (options.isOnlySerializableSource() && !propModel.isSerializable())
                    return;

                if (options.isOnlySecureSource() && propModel.isNotSecure())
                    return;

                String propName = propModel.getName();
                if (ignorePropNames != null && ignorePropNames.contains(propName))
                    return;

                Object value = getFieldValue(srcModel, src, propName, scope);
                setFieldValue(targetModel, target, propName, value, targetType, deep, null, options);
            });

            Set<String> extNames = srcModel.getExtPropertyNames(src);
            if (extNames != null) {
                for (String extName : extNames) {
                    Object value = srcModel.getExtProperty(src, extName, options.getEvalScope());
                    setFieldValue(targetModel, target, extName, value, targetType, deep, null, options);
                }
            }
        }
    }

    private Object getFieldValue(IBeanModel beanModel, Object bean, String propName, IEvalScope scope) {
        // @TODO 可以考虑支持方法调用，从而使用到selection.args
        return beanModel.getProperty(bean, propName, scope);
    }

    private IBeanDeserializer getDeserializer(IBeanPropertyModel propModel, BeanCopyOptions options) {
        IBeanDeserializerFactory deserializerFactory = options.getDeserializerFactory();
        if (deserializerFactory == null)
            return null;
        if (propModel.getDeserializer() != null) {
            IBeanDeserializer deserializer = deserializerFactory.getDeserializer(propModel.getDeserializer());
            // if (deserializer == null)
            // throw new NopException(ERR_JSON_UNKNOWN_SERIALIZER_FOR_PROP)
            // .param(ARG_CLASS_NAME, beanModel.getClassName())
            // .param(ARG_PROP_NAME, propModel.getName())
            // .param(ARG_SERIALIZER, propModel.getDeserializer());
            return deserializer;
        }

        return null;
    }

    private Object beanToType(boolean deep, Object src, IGenericType targetType, FieldSelectionBean selection,
                              BeanCopyOptions options) {
        if (src == null)
            return null;

        if (deep) {
            return _buildBean(src, targetType, selection, options);
        } else {
            return _castBeanToType(src, targetType, selection, options);
        }
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isEnum() || StdDataType.fromJavaClass(clazz).isSimpleType();
    }

    public Object buildBean(@Nonnull Object src, @Nonnull IGenericType targetType, BeanCopyOptions options) {
        Guard.notNull(src, "src");
        Guard.notNull(targetType, "targetType");
        if (options == null)
            options = BeanCopyOptions.DEFAULT;

        return _buildBean(src, targetType, options.getSelection(), options);
    }

    private Object _buildBean(Object src, IGenericType targetType, FieldSelectionBean selection,
                              BeanCopyOptions options) {
        if(src == null)
            return null;

        Object target = options.getMappedObj(src);
        if (target != null)
            return target;

        // 有可能是TypeVariable
        if (targetType.getRawClass() == Object.class) {
            Object ret = CloneHelper.deepClone(src);
            if (options.isAllowLoop()) {
                options.makeObjMap().put(src, ret);
            }
            return ret;
        }

        if (isSimpleType(targetType.getRawClass())) {
            return convert(src, targetType);
        }

        if (src instanceof String) {
            if (targetType == PredefinedGenericTypes.LIST_STRING_TYPE
                    || targetType == PredefinedGenericTypes.LIST_ANY_TYPE)
                return ConvertHelper.toCsvList(src.toString(), NopException::new);
            if (targetType == PredefinedGenericTypes.SET_STRING_TYPE
                    || targetType == PredefinedGenericTypes.SET_ANY_TYPE)
                return ConvertHelper.toCsvSet(src.toString());
            return convert(src, targetType);
        }

        if (isSimpleType(src.getClass())) {
            return convert(src, targetType);
        }

        Object ret;
        if (src instanceof Collection) {
            ret = buildCollection(BeanCollectionAdapter.INSTANCE, src, targetType, selection, options);
        } else if (src.getClass().isArray()) {
            ret = buildCollection(ArrayBeanCollectionAdapter.INSTANCE, src, targetType, selection, options);
        } else {
            ret = buildObject(src, targetType, selection, options);
        }
        return ret;
    }

    private Object convert(Object bean, IGenericType targetType) {
        return ReflectionManager.instance().getConverterForJavaType(targetType.getRawClass()).convert(bean,
                NopException::new);
    }

    // private IBeanDeserializer getDeserializer(IGenericType type, BeanCopyOptions options) {
    // IBeanDeserializerFactory factory = options.getDeserializerFactory();
    // if (factory == null)
    // return null;
    // return factory.getDeserializerForClass(type.getRawClass());
    // }

    private Object buildObject(Object src, IGenericType targetType, FieldSelectionBean selection,
                               BeanCopyOptions options) {
        IBeanModel targetModel = getTargetBeanModel(src, targetType, options);
        IBeanDeserializer deserializer = getDeserializer(targetModel, options);
        if (deserializer != null) {
            Object bean = deserializer.deserialize(src, targetType, selection, options.getObjMap());
            if (options.isAllowLoop()) {
                options.makeObjMap().put(src, bean);
            }
            return bean;
        }

        if (targetType == PredefinedGenericTypes.ANY_TYPE) {
            Object ret = CloneHelper.deepClone(src);
            if (options.isAllowLoop()) {
                options.makeObjMap().put(src, ret);
            }
            return ret;
        }

        if (selection == null && targetType.isInstance(src)) {
            if (src instanceof IDeepCloneable) {
                // Map<String,MyBean>这种泛型形式需要对值进行类型转换
                if (!targetType.isMapLike() && !targetType.isCollectionLike()) {
                    Object ret = ((IDeepCloneable) src).deepClone();
                    if (options.isAllowLoop()) {
                        options.makeObjMap().put(src, ret);
                    }
                    return ret;
                }
            }
            if (options.isReuseImmutable()) {
                if (isImmutable(src, options)) {
                    if (options.isAllowLoop()) {
                        options.makeObjMap().put(src, src);
                    }
                    return src;
                }
            }
        }

        if (!targetModel.isMapLike()) {
            // 如果是抽象类或者接口，则直接返回源对象
            if (!targetModel.hasConstructor()) {
                if (!targetType.isInstance(src))
                    throw new NopException(ERR_REFLECT_CAST_VALUE_TO_TARGET_TYPE_FAIL)
                            .loc(SourceLocation.getLocation(src)).param(ARG_SRC_TYPE, src.getClass())
                            .param(ARG_TARGET_TYPE, targetType);

                if (options.isAllowLoop()) {
                    options.makeObjMap().put(src, src);
                }
                return src;
            }
        }

        Object bean;
        Set<String> propNames = null;
        if (!targetModel.getConstructorPropNames().isEmpty()) {
            propNames = new HashSet<>(targetModel.getConstructorPropNames());
            bean = newTarget(src, targetModel, selection, options);
        } else {
            bean = targetModel.newInstance();
        }

        if (options.isAllowLoop()) {
            options.makeObjMap().put(src, bean);
        }

        // 对于ISchema这样的接口类型，实际返回的target对象是SchemaImpl
        if (targetModel.isAbstract()) {
            targetModel = options.getBeanModelManager().getBeanModelForClass(bean.getClass());
        }

        _copyObj(src, bean, targetModel, propNames, targetType, true, selection, options);
        return bean;
    }

    private boolean isImmutable(Object src, BeanCopyOptions options) {
        if (options.isReuseFrozen()) {
            if (src instanceof IFreezable && ((IFreezable) src).frozen()) {
                return true;
            }
        }
        IBeanModel srcModel = options.getBeanModelManager().getBeanModelForClass(src.getClass());
        return srcModel.isImmutable();
    }

    private IBeanModel getTargetBeanModel(Object src, IGenericType targetType, BeanCopyOptions options) {
        IBeanModelManager beanModelManager = options.getBeanModelManager();
        IBeanModel beanModel = beanModelManager.getBeanModelForType(targetType);
        String typeProp = beanModel.getSubTypeProp();
        if (typeProp != null) {
            String typeValue = StringHelper.toString(getFieldValue(src, typeProp, options), "");
            IGenericType concreteType = beanModel.determineSubType(typeValue);
            if (concreteType == null)
                throw new NopException(ERR_REFLECT_BEAN_NO_CLASS_FOR_TYPE)
                        .param(ARG_CLASS_NAME, beanModel.getClassName()).param(ARG_TYPE_VALUE, typeValue);
            beanModel = beanModelManager.getBeanModelForType(concreteType);
        }
        return beanModel;
    }

    private Object getFieldValue(Object obj, String propName, BeanCopyOptions options) {
        if (!options.isAllowMapExt() && obj instanceof Map) {
            return ((Map<?, ?>) obj).get(propName);
        }
        IBeanModel beanModel = options.getBeanModelManager().getBeanModelForClass(obj.getClass());
        return beanModel.getProperty(obj, propName, options.getEvalScope());
    }

    private IBeanDeserializer getDeserializer(IBeanModel beanModel, BeanCopyOptions options) {
        IBeanDeserializerFactory deserializerFactory = options.getDeserializerFactory();
        if (deserializerFactory == null)
            return null;
        if (beanModel.getDeserializer() != null) {
            IBeanDeserializer deserializer = deserializerFactory.getDeserializer(beanModel.getDeserializer());
            // if (deserializer == null)
            // throw new NopException(ERR_JSON_UNKNOWN_SERIALIZER_FOR_PROP)
            // .param(ARG_CLASS_NAME, beanModel.getClassName())
            // .param(ARG_SERIALIZER, beanModel.getDeserializer());
            return deserializer;
        }

        return null;
    }

    public void setFieldValue(IBeanModel beanModel, Object bean, String propName, Object value, IGenericType beanType,
                              boolean deep, FieldSelectionBean subSelection, BeanCopyOptions options) {
        if (!options.isAllowMapExt()) {
            if (beanModel.isMapLike()) {
                IGenericType propType = beanType.getMapValueType();
                value = beanToType(deep, value, propType, subSelection, options);
                ((Map<String, Object>) bean).put(propName, value);
                return;
            }
        }
        IBeanPropertyModel propModel = beanModel.getPropertyModel(propName);
        if (propModel != null) {
            if (propModel.getType().isBooleanType()) {
                value = ConvertHelper.toBoolean(value);
            }

            if (options.isOnlySecureTarget() && propModel.isNotSecure()) {
                LOG.debug("nop.reflect.bean.builder.ignore-prop-not-secure:className={},propName={}",
                        beanModel.getClassName(), propName);
                return;
            }

            if (options.isOnlySerializableTarget() && !propModel.isSerializable()) {
                if (beanModel.isAllowSetExtProperty()) {
                    beanModel.setExtProperty(bean, propName, value, options.getEvalScope());
                } else {
                    LOG.debug("nop.reflect.bean.builder.ignore-prop-not-serializable:className={},propName={}",
                            beanModel.getClassName(), propName);
                }
                return;
            }

            // 忽略不可写的属性
            if (!propModel.isWritable()) {
                if (beanModel.isAllowSetExtProperty()) {
                    beanModel.setExtProperty(bean, propName, value, options.getEvalScope());
                } else {
                    LOG.debug("nop.reflect.bean.builder.ignore-prop-not-writable:className={},propName={}",
                            beanModel.getClassName(), propName);
                }
                return;
            }
            if (value != null) {
                IGenericType propType = propModel.getType().refine(beanModel.getType(), beanType);
                IBeanDeserializer deserializer = getDeserializer(propModel, options);
                if (deserializer != null) {
                    value = deserializer.deserialize(value, propType, subSelection, options.getObjMap());
                } else {
                    value = beanToType(deep, value, propType, subSelection, options);
                }
            }
            propModel.setPropertyValue(bean, value);
        } else {
            IGenericType propType = null;
            if (options.isAllowBuilderMethod()) {
                propType = beanModel.getBuildPropertyType(propName);
            }
            if (propType != null) {
                value = beanToType(deep, value, propType, subSelection, options);
                beanModel.buildProperty(bean, propName, value);
            } else {
                if (beanModel.isAllowSetExtProperty()) {
                    if (options.isIgnoreUnknownProp()) {
                        // 判断是否允许扩展属性
                        if (!beanModel.isAllowExtProperty(bean, propName))
                            return;
                    }
                    beanModel.setExtProperty(bean, propName, value, options.getEvalScope());
                } else {
                    if (!options.isIgnoreUnknownProp()) {
                        // 忽略ISourceLocationGetter引入到的location属性
                        if (!propName.equals(CoreConstants.PROP_LOCATION) && !propName.startsWith("__")) {
                            throw new NopException(ERR_REFLECT_UNKNOWN_BEAN_PROP).param(ARG_CLASS_NAME, bean.getClass())
                                    .param(ARG_PROP_NAME, propName);
                        }
                    }
                }
            }
        }
    }

    private Object buildCollection(IBeanCollectionAdapter srcType, Object src, IGenericType targetType,
                                   FieldSelectionBean selection, BeanCopyOptions options) {
        int len = srcType.getSize(src);

        if (targetType.isArray()) {
            IGenericType componentType = targetType.getComponentType();
            Object ret = Array.newInstance(componentType.getRawClass(), len);
            if (options.isAllowLoop()) {
                options.addMappedObj(src, ret);
            }
            srcType.forEach(src, (item, i) -> {
                Object value = _buildBean(item, componentType, selection, options);
                Array.set(ret, i, value);
            });
            return ret;
        } else if (targetType.isCollectionLike()) {
            IGenericType componentType = targetType.getComponentType();
            if (componentType == PredefinedGenericTypes.STRING_TYPE
                    || componentType == PredefinedGenericTypes.ANY_TYPE) {
                if (src instanceof String) {
                    if (targetType.isSetLike()) {
                        return ConvertHelper.toCsvSet(src, NopException::new);
                    }
                    return ConvertHelper.toCsvList(src, NopException::new);
                }
            }
            Class<?> clazz = targetType.getRawClass();

            Collection<Object> ret = ClassHelper.newCollection(clazz);
            if (options.isAllowLoop()) {
                options.addMappedObj(src, ret);
            }
            srcType.forEach(src, (item, i) -> {
                Object value = _buildBean(item, componentType, selection, options);
                ret.add(value);
            });
            return ret;
        } else {
            throw new NopException(ERR_REFLECT_NOT_COLLECTION_TYPE).param(ARG_CLASS_NAME, targetType.getClassName());
        }
    }

    @Override
    public Object castBeanToType(@Nonnull Object src, @Nonnull IGenericType targetType, BeanCopyOptions options) {
        Guard.notNull(src, "src");
        Guard.notNull(targetType, "targetType");
        if (options == null) {
            options = BeanCopyOptions.DEFAULT;
        }

        return _castBeanToType(src, targetType, options.getSelection(), options);
    }

    private Object _castBeanToType(Object src, IGenericType targetType, FieldSelectionBean selection,
                                   BeanCopyOptions options) {

        if (targetType.getStdDataType().isSimpleType())
            return convert(src, targetType);

        if (targetType.isInstance(src)) {
            if (targetType.isCollectionLike()) {
                if (isCollectionType(src, targetType))
                    return src;
            } else if (targetType.isMapLike()) {
                if (isMapType(src, targetType))
                    return src;
            } else if (targetType.isAssignableTo(ApiRequest.class)) {
                ApiRequest msg = (ApiRequest) src;
                Object data = msg.getData();
                if (data != null) {
                    data = _castBeanToType(data, targetType.getTypeParameters().get(0), selection.getField("data"),
                            options);
                    msg.setData(data);
                }
                return msg;
            } else if (targetType.isAssignableTo(ApiResponse.class)) {
                ApiResponse msg = (ApiResponse) src;
                Object data = msg.getData();
                if (data != null) {
                    data = _castBeanToType(data, targetType.getTypeParameters().get(0), selection.getField("data"),
                            options);
                    msg.setData(data);
                }
                return msg;
            } else {
                return src;
            }
        }

        if (StdDataType.fromJavaClass(src.getClass()).isSimpleType()) {
            // 空字符串强制转型到bean，可以直接返回null。前台可能会提交空串作为参数
            if (StringHelper.isEmptyObject(src))
                return null;
            return convert(src, targetType);
        }

        Object target;
        IBeanModel targetModel = options.getBeanModelManager().getBeanModelForType(targetType);
        Set<String> propNames = null;
        if (targetType.isArray()) {
            IBeanCollectionAdapter adapter = getCollectionAdapter(src);
            target = Array.newInstance(targetType.getComponentType().getRawClass(), adapter.getSize(src));
        } else if (targetType.isMapLike() || targetType.isCollectionLike()) {
            target = targetModel.newInstance();
        } else {
//            if (src instanceof IEvalFunction)
//                return convert(src, targetType);

            if (!(src instanceof Map))
                throw new NopException(ERR_REFLECT_CAST_VALUE_TO_TARGET_TYPE_FAIL).param(ARG_SRC_TYPE, src.getClass())
                        .param(ARG_TARGET_TYPE, targetType);

            if (!targetModel.getConstructorPropNames().isEmpty()) {
                propNames = new HashSet<>(targetModel.getConstructorPropNames());
                target = newTarget(src, targetModel, selection, options);
                _copyObj(src, target, targetModel, propNames, targetType, false, selection, options);
                return target;
            } else {
                target = targetModel.newInstance();
            }
        }
        _copyBean(src, target, targetType, false, selection, options);
        return target;
    }

    private Object newTarget(Object src, IBeanModel targetModel, FieldSelectionBean selection,
                             BeanCopyOptions options) {
        List<String> propNames = targetModel.getConstructorPropNames();
        Object[] values = new Object[propNames.size()];
        IBeanModel srcModel = options.getBeanModelManager().getBeanModelForClass(src.getClass());
        int index = 0;
        for (String propName : targetModel.getConstructorPropNames()) {
            if (selection != null && !selection.hasField(propName)) {
                values[index++] = null;
                continue;
            }

            Object value = srcModel.getProperty(src, propName, options.getEvalScope());
            values[index++] = value;
        }
        return targetModel.newInstance(values);
    }

    private boolean isCollectionType(Object src, IGenericType targetType) {
        IGenericType valueType = targetType.getComponentType();
        if (valueType == PredefinedGenericTypes.ANY_TYPE)
            return true;

        Collection<Object> coll = (Collection<Object>) src;
        for (Object value : coll) {
            if (value == null)
                continue;
            if (!valueType.isInstance(value))
                return false;
        }
        return true;
    }

    private boolean isMapType(Object src, IGenericType targetType) {
        IGenericType valueType = targetType.getMapValueType();
        if (valueType == PredefinedGenericTypes.ANY_TYPE)
            return true;

        Map<Object, Object> map = (Map<Object, Object>) src;
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null)
                continue;
            if (!valueType.isInstance(value))
                return false;
        }
        return true;
    }
}