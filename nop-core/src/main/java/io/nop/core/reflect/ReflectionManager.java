/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.convert.IdentityTypeConverter;
import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.IClosable;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.collections.ListFunctions;
import io.nop.commons.collections.SetFunctions;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.functions.EvalFunctionalAdapter;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.selection.FieldSelectionBeanConverter;
import io.nop.core.reflect.bean.BeanModel;
import io.nop.core.reflect.bean.BeanPropertyModel;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanModelManager;
import io.nop.core.reflect.converter.*;
import io.nop.core.reflect.enhancer.StringBeanModelEnhancer;
import io.nop.core.reflect.impl.ClassExtension;
import io.nop.core.reflect.impl.ClassModelBuilder;
import io.nop.core.reflect.impl.HelperMethodsBuilder;
import io.nop.core.reflect.impl.MethodInvokers;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IGenericTypeBuilder;
import io.nop.core.type.IRawType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericRawTypeImpl;
import io.nop.core.type.impl.PredefinedGenericType;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.core.CoreConfigs.CFG_REFLECT_MODEL_CACHE_SIZE;
import static io.nop.core.CoreErrors.*;

/**
 * 获取反射模型的Facade入口
 */
@GlobalInstance
public class ReflectionManager implements IBeanModelManager, IGenericTypeBuilder, IClassModelLoader {
    // private static final Logger LOG = LoggerFactory.getLogger(ReflectionManager.class);

    private static final ReflectionManager _instance = new ReflectionManager();

    static {
        _instance.registerHelperMethods(List.class, ListFunctions.class, null);
        _instance.registerHelperMethods(Set.class, SetFunctions.class, null);
        _instance.registerHelperMethods(String.class, StringHelper.class, "$");
        _instance.registerHelperMethods(LocalDate.class, DateHelper.class, "$");
        _instance.registerBeanModelEnhancer(new StringBeanModelEnhancer());
        _instance.registerTypeConverter(FieldSelectionBean.class,new FieldSelectionBeanConverter());
    }

    public static ReflectionManager instance() {
        return _instance;
    }

    /**
     * 可以跳过反射机制，直接注册类模型
     */
    private final Map<Class<?>, IClassModel> registeredClassModels = CollectionHelper.newConcurrentWeakMap();

    private final List<IBeanModelEnhancer> beanModelEnhancers = new CopyOnWriteArrayList<>();

    /**
     * 为类型增加扩展方法
     */
    private final Map<Class<?>, ClassExtension> classExtensions = CollectionHelper.newConcurrentWeakMap();

    // Caffeine缓存内部使用ConcurrentHashMap实现。而CHM的computeIfAbsent函数不允许在执行的过程中修改Map本身。
    private ICache<Class<?>, IClassModel> introspectCache;

    // 创建ClassModel过程中所使用的临时Map，在并发创建时起到互斥锁的作用。
    private final Map<Class<?>, ClassModelLoader> tempLoaders = new ConcurrentHashMap<>();

    private final Map<Type, ITypeConverter> converters = CollectionHelper.newConcurrentWeakMap();

    private final Map<Class<?>, IRawType> rawTypes = CollectionHelper.newConcurrentWeakMap();

    private final Map<Class<?>, MethodInvokers> invokersMap = CollectionHelper.newConcurrentWeakMap();

    private boolean recordForNativeImage = false;

    private final Set<Class<?>> reflectClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<List<String>> reflectProxies = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ReflectionManager() {
        initCache();
    }


    public boolean isRecordForNativeImage() {
        return recordForNativeImage;
    }

    public void setRecordForNativeImage(boolean recordForNativeImage) {
        this.recordForNativeImage = recordForNativeImage;
        if (!recordForNativeImage) {
            reflectClasses.clear();
            reflectProxies.clear();
        }
    }

    public void registerBeanModelEnhancer(IBeanModelEnhancer enhancer) {
        beanModelEnhancers.add(enhancer);
    }

    public void unregisterBeanModelEnhancer(IBeanModelEnhancer enhancer) {
        beanModelEnhancers.remove(enhancer);
    }

    public void enhanceBeanModel(BeanModel beanModel, IClassModel classModel,
                                 Map<String, BeanPropertyModel> props, Map<String, String> propAliases) {
        for (IBeanModelEnhancer enhancer : beanModelEnhancers) {
            if (enhancer.isForClass(classModel.getRawClass()))
                enhancer.enhance(beanModel, classModel, props, propAliases);
        }
    }

    /**
     * 得到所有曾经用过反射机制的类
     */
    public Set<Class<?>> getReflectClasses() {
        return reflectClasses;
    }

    public Set<List<String>> getReflectProxies() {
        return reflectProxies;
    }

    public void logReflectClass(Class<?> clazz) {
        if (recordForNativeImage && !clazz.isPrimitive()) {
            reflectClasses.add(clazz);
        }
    }

    private void initCache() {
        CacheConfig config = new CacheConfig();
        config.setMaximumSize(CFG_REFLECT_MODEL_CACHE_SIZE.get());
        config.setWeakKeys(true);
        this.introspectCache = LocalCache.newCache("reflection-class-model-cache", config, null);
        GlobalCacheRegistry.instance().register(this.introspectCache);
    }

    class ClassModelLoader {
        private final Class<?> clazz;

        ClassModelLoader(Class<?> clazz) {
            this.clazz = clazz;
        }

        public synchronized IClassModel load() {
            return buildClassModel(clazz);
        }
    }

    private IClassModel buildClassModel(Class<?> clazz) {
        if (clazz.isArray())
            throw new NopException(ERR_REFLECT_NOT_SUPPORT_ARRAY_CLASS_MODEL).param(ARG_CLASS_NAME, clazz.getName());

        if (recordForNativeImage) {
            reflectClasses.add(clazz);
        }

        return new ClassModelBuilder(this, clazz).build();
    }

    public void clearCache() {
        introspectCache.clear();
        converters.clear();
        rawTypes.clear();
    }

    public void clearInvokers() {
        invokersMap.clear();
    }

    public IClassModel getRegisteredClassModel(Class<?> clazz) {
        return registeredClassModels.get(clazz);
    }

    public Map<Class<?>, IClassModel> getCachedClassModels() {
        Map<Class<?>, IClassModel> map = new HashMap<>();
        introspectCache.forEachEntry((clazz, classModel) -> {
            if (isClosed(clazz.getClassLoader()))
                return;
            map.put(clazz, classModel);
        });
        return map;
    }

    // 判断classLoader是否已经关闭。如果父classLoader关闭，则子classLoader也认为已经关闭
    private boolean isClosed(ClassLoader classLoader) {
        if (classLoader == ClassLoader.getSystemClassLoader())
            return false;

        if (classLoader instanceof IClosable) {
            if (((IClosable) classLoader).isClosed())
                return true;
        }
        ClassLoader parent = classLoader.getParent();
        if (parent == null)
            return false;
        return isClosed(parent);
    }

    public void registerTypeConverter(Type clazz, ITypeConverter converter) {
        converters.put(clazz, converter);
    }

    public void unregisterTypeConverter(Type clazz, ITypeConverter converter) {
        converters.remove(clazz, converter);
    }

    public void registerClassModel(IClassModel classModel) {
        if (registeredClassModels.containsKey(classModel.getRawClass()))
            throw new NopException(ERR_REFLECT_CLASS_MODEL_ALREADY_REGISTERED).param(ARG_CLASS_NAME,
                    classModel.getClassName());
        registeredClassModels.put(classModel.getRawClass(), classModel);
    }

    public void unregisterClassModel(IClassModel classModel) {
        registeredClassModels.remove(classModel.getRawClass(), classModel);
    }

    public void registerInvokers(Class<?> clazz, MethodInvokers invokers) {
        this.invokersMap.put(clazz, invokers);
    }

    public void unregisterInvokers(Class<?> clazz, MethodInvokers invokers) {
        this.invokersMap.remove(clazz, invokers);
    }

    public MethodInvokers getInvokers(Class<?> clazz) {
        return invokersMap.get(clazz);
    }

    /**
     * 为已存在的类增加扩展函数。
     *
     * @param clazz        需要扩展的类
     * @param helperClass  帮助类，它的所有符合条件的静态函数（第一个参数的类型为className指定的类）都自动注册为扩展函数
     * @param methodPrefix 拼接到方法名前的前缀。可以指定方法前缀来区分类的原生方法和注册的扩展方法。
     */
    public ICancellable registerHelperMethods(Class<?> clazz, Class<?> helperClass, String methodPrefix) {
        List<IFunctionModel> methods = new HelperMethodsBuilder(clazz, getClassModel(helperClass), methodPrefix)
                .build();
        return registerHelperMethods(clazz, methods);
    }

    public ICancellable registerHelperMethods(Class<?> clazz, List<IFunctionModel> methods) {
        ClassExtension extension = classExtensions.computeIfAbsent(clazz, k -> new ClassExtension());
        extension.addHelperMethods(methods);
        return new Cancellable(r -> {
            extension.removeHelperMethods(methods);

            introspectCache.remove(clazz);
            for (Class<?> extended : extension.getExtendedClasses()) {
                introspectCache.remove(extended);
            }
        });
    }

    public ClassExtension getClassExtension(Class<?> clazz) {
        ClassExtension extension = classExtensions.get(clazz);
        return extension;
    }

    public IClassModel getClassModel(Class<?> clazz) {
        IClassModel model = registeredClassModels.get(clazz);
        if (model == null)
            model = getFromCache(clazz);
        return model;
    }

    IClassModel getFromCache(Class<?> clazz) {
        IClassModel model = introspectCache.get(clazz);
        if (model != null)
            return model;

        Class<?> paramClass = clazz;
        ClassModelLoader loader = tempLoaders.computeIfAbsent(clazz, cls -> new ClassModelLoader(paramClass));
        try {
            IClassModel classModel = loader.load();
            introspectCache.put(clazz, classModel);
            return classModel;
        } finally {
            tempLoaders.remove(clazz, loader);
        }
    }

    public IClassModel getClassModelForType(IGenericType type) {
        return getClassModel(type.getRawClass());
    }

    public IClassModel loadClassModel(String typeName) {
        PredefinedGenericType type = PredefinedGenericTypes.getPredefinedType(typeName);
        if (type != null) {
            return getClassModelForType(type);
        }
        try {
            Class<?> clazz = ClassHelper.getSafeClassLoader().loadClass(typeName);
            return getClassModel(clazz);
        } catch (ClassNotFoundException e) {
            throw NopException.adapt(e);
        }
    }

    public IBeanModel loadBeanModel(String typeName) {
        return loadClassModel(typeName).getBeanModel();
    }

    public void removeIntrospectCache(Class<?> clazz) {
        introspectCache.remove(clazz);
    }

    public void clearIntrospectCache() {
        introspectCache.clear();
    }

    @Override
    public IBeanModel getBeanModelForClass(Class<?> clazz) {
        IClassModel classModel = getClassModel(clazz);
        return classModel.getBeanModel();
    }

    @Override
    public IBeanModel getBeanModelForType(IGenericType type) {
        return getBeanModelForClass(type.getRawClass());
    }

    public IGenericType buildGenericType(Type type) {
        if (type instanceof IGenericType)
            return (IGenericType) type;
        return JavaGenericTypeBuilder.buildGenericType(type);
    }

    public IGenericType buildRawType(Class<?> clazz) {
        return JavaGenericTypeBuilder.buildRawType(clazz);
    }

    public IGenericType buildGenericClassType(Class<?> clazz) {
        return GenericTypeHelper.buildParameterizedType(JavaGenericTypeBuilder.buildRawType(Class.class),
                Collections.singletonList(buildRawType(clazz)));
    }

    public ITypeConverter getConverterForJavaType(Class<?> type) {
        ITypeConverter converter = SysConverterRegistry.instance().getConverterByType(type);
        if (converter == null)
            converter = converters.get(type);
        if (converter != null)
            return converter;

        return converters.computeIfAbsent(type, key -> {
            if (type.isEnum())
                return new EnumTypeConverter((Class) type);

            if (type == XNode.class) {
                return XNodeTypeConverter.INSTANCE;
            }

            if (type == TreeBean.class) {
                return TreeBeanTypeConverter.INSTANCE;
            }

            IGenericType typeInfo = buildGenericType(type);
            if (isFunctionalInterface(typeInfo.getRawClass())) {
                return FunctionalInterfaceConverter.INSTANCE;
            }

            if (typeInfo.getRawClass().isAnnotationPresent(DataBean.class))
                return new DataBeanTypeConverter(typeInfo);

            if (typeInfo.getRawClass().isArray())
                return new ArrayTypeConverter(typeInfo.getRawClass());

            return IdentityTypeConverter.INSTANCE;
        });
    }

    private boolean isFunctionalInterface(Class<?> clazz) {
        return EvalFunctionalAdapter.SUPPORTED_INTERFACES.contains(clazz);
    }

    public IRawType newRawType(Class<?> clazz) {
        IRawType type = rawTypes.get(clazz);
        if (type == null) {
            GenericRawTypeImpl rawType = new GenericRawTypeImpl(clazz);

            rawTypes.putIfAbsent(clazz, rawType);
            type = rawType;
        }
        return type;
    }

    public Object newProxyInstance(Class[] inf, InvocationHandler handler) {
        if (isRecordForNativeImage()) {
            List<String> classNames = new ArrayList<>(inf.length);
            for (Class<?> clazz : inf) {
                classNames.add(clazz.getName());
            }
            reflectProxies.add(classNames);
        }
        return Proxy.newProxyInstance(ClassHelper.getDefaultClassLoader(), inf, handler);
    }
}