/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.loader;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.reflect.*;
import io.nop.core.reflect.accessor.FunctionSpecializedPropertySetter;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.type.IGenericType;
import io.nop.ioc.IocConstants;
import io.nop.ioc.impl.*;
import io.nop.ioc.impl.resolvers.*;
import io.nop.ioc.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static io.nop.ioc.IocErrors.*;
import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ARG_METHOD_NAME;

/**
 * 解析BeanModel，填充BeanDefinition
 */
public class BeanDefinitionBuilder {
    static final Logger LOG = LoggerFactory.getLogger(BeanDefinitionBuilder.class);

    static final IFunctionModel[] EMPTY_METHODS = new IFunctionModel[0];
    private final IClassLoader classLoader;
    private final IBeanClassIntrospection introspection;

    private Map<String, BeanDefinition> allBeans;
    private Map<String, BeanDefinition> uniqueBeans;
    private Map<String, AliasName> aliases;

    private final Map<Class<?>, BeanTypeMapping> beanTypeMappings = new HashMap<>();
    private final Map<Class<? extends Annotation>, List<BeanDefinition>> annMappings = new HashMap<>();

    private final IBeanContainer parentContainer;

    public BeanDefinitionBuilder(IClassLoader classLoader, IBeanClassIntrospection introspection,
                                 IBeanContainer parentContainer) {
        this.classLoader = classLoader;
        this.introspection = introspection;
        this.parentContainer = parentContainer;
    }

    public BeanDefinitionBuilder useBeans(Map<String, BeanDefinition> beans) {
        this.allBeans = beans;
        this.uniqueBeans = toUniqueMap(beans);
        return this;
    }

    public void buildAll(Map<String, BeanDefinition> beans, Map<String, AliasName> aliases) {
        this.allBeans = beans;
        this.uniqueBeans = toUniqueMap(beans);
        this.aliases = aliases;

        initBeanTypes();
        initFactoryBeans();

        for (BeanDefinition bean : uniqueBeans.values()) {
            if (bean.isAbstract() || bean.isDisabled())
                continue;

            BeanValue beanModel = bean.getBeanModel();
            if (beanModel.getFactoryBean() != null) {
                IClassModel classModel = ReflectionManager.instance()
                        .getClassModelForType(bean.getFactoryMethod().getReturnType());
                initMethods(bean, classModel);
            } else {
                IClassModel classModel = initClassModel(bean);
                initMethods(bean, classModel);
            }
        }

        for (BeanDefinition bean : beans.values()) {
            if (bean.isAbstract() || bean.isDisabled())
                continue;
            build(bean);
        }
    }

    private Map<String, BeanDefinition> toUniqueMap(Map<String, BeanDefinition> beans) {
        TreeMap<String, BeanDefinition> map = new TreeMap<>();
        for (BeanDefinition bean : beans.values()) {
            // beans中可能存在多个名称指向同一个bean，只保留id
            map.put(bean.getId(), bean);
        }
        return map;
    }

    private void initBeanTypes() {
        for (BeanDefinition bean : uniqueBeans.values()) {
            if (bean.isAbstract() || bean.isDisabled())
                continue;

            checkConstructorArg(bean);

            normalizeIocType(bean);

            BeanValue beanModel = bean.getBeanModel();

            Class<?> clazz = loadBeanClass(bean);
            bean.setBeanClass(clazz);
            if (clazz == null) {
                beanModel.setIocAop(false);
            }

            if (!CollectionHelper.isEmpty(beanModel.getIocType())) {
                List<Class<?>> types = new ArrayList<>();
                for (String typeName : beanModel.getIocType()) {
                    Class<?> beanClass = loadBeanClass(bean, bean.getLocation(), typeName);
                    types.add(beanClass);
                }
                bean.setBeanTypes(types);
            } else if (clazz != null && beanModel.getFactoryMethod() == null && beanModel.getIocBeanMethod() == null) {
                bean.setBeanTypes(Collections.singletonList(clazz));
            }
        }
    }

    void normalizeIocType(BeanDefinition bean) {
        BeanValue beanModel = bean.getBeanModel();
        Set<String> types = beanModel.getIocType();
        if (types != null && types.contains(IocConstants.CONFIG_BEAN_ID)) {
            types = types.stream().map(type -> {
                if (IocConstants.CONFIG_BEAN_ID.equals(type)) {
                    return ConfigPropHelper.getNormalizedId(bean.getId());
                }
                return type;
            }).collect(Collectors.toSet());
            beanModel.setIocType(types);
        }
    }

    private void initFactoryBeans() {
        for (BeanDefinition bean : uniqueBeans.values()) {
            if (bean.isAbstract() || bean.isDisabled())
                continue;

            String factoryBean = bean.getBeanModel().getFactoryBean();
            if (factoryBean == null)
                continue;

            String factoryMethod = bean.getBeanModel().getFactoryMethod();
            if (factoryMethod == null)
                throw new NopException(ERR_IOC_FACTORY_BEAN_MUST_BE_USED_WITH_FACTORY_METHOD).source(bean)
                        .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN, bean).param(ARG_FACTORY_BEAN, factoryBean);

            BeanDefinition factoryBeanDef = allBeans.get(factoryBean);
            if (factoryBeanDef == null || factoryBeanDef.isAbstract())
                throw new NopException(ERR_IOC_UNKNOWN_CONCRETE_BEAN_REF).source(bean).param(ARG_BEAN_REF, factoryBean)
                        .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN, bean);

            List<Class<?>> beanType = factoryBeanDef.getBeanTypes();
            if (CollectionHelper.isEmpty(beanType))
                throw new NopException(ERR_IOC_REF_FACTORY_BEAN_NO_BEAN_TYPE).source(bean)
                        .param(ARG_FACTORY_BEAN, factoryBeanDef).param(ARG_BEAN_REF, factoryBean)
                        .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN, bean);

            BeanValue beanModel = bean.getBeanModel();
            IClassModel classModel = ReflectionManager.instance().getClassModel(beanType.get(0));
            IFunctionModel funcModel = classModel.getMethod(factoryMethod, beanModel.getConstructorArgCount());
            checkMethodNotNull(funcModel, bean, classModel, factoryMethod);
            bean.setFactoryMethod(funcModel);
        }
    }

    private void build(BeanDefinition bean) {
        BeanValue beanModel = bean.getBeanModel();
        if (StringHelper.isEmpty(beanModel.getScope())) {
            beanModel.setScope(ApiConstants.BEAN_SCOPE_SINGLETON);
        }

        Set<String> dependsOn = beanModel.getDependsOn();
        if (dependsOn != null) {
            for (String beanName : dependsOn) {
                checkDependRef(bean, beanName);
            }
        }

        if (beanModel.getFactoryBean() != null) {
            IClassModel classModel = ReflectionManager.instance()
                    .getClassModelForType(bean.getFactoryMethod().getReturnType());
            initProps(bean, classModel);
        } else {
            IClassModel classModel = initClassModel(bean);
            initConstructor(bean, classModel);
            initProps(bean, classModel);
            initInterceptors(bean);
        }
    }

    public BeanDefinition buildForAutowire(Class<?> clazz) {
        BeanModel beanModel = new BeanModel();
        beanModel.setClassName(clazz.getName());
        beanModel.setId(clazz.getName());
        BeanDefinition beanDef = new BeanDefinition(beanModel);
        beanDef.setBeanClass(clazz);
        IClassModel classModel = initClassModel(beanDef);
        initMethods(beanDef, classModel);
        initProps(beanDef, classModel);
        return beanDef;
    }

    void checkDependRef(BeanDefinition bean, String ref) {
        if (!containsBean(ref)) {
            throw new NopException(ERR_IOC_UNKNOWN_DEPEND_REF).source(bean).param(ARG_BEAN_NAME, bean.getId())
                    .param(ARG_DEPEND, ref).param(ARG_BEAN, bean);
        }
    }

    boolean containsBean(String ref) {
        if (allBeans.containsKey(ref))
            return true;
        if (parentContainer != null && parentContainer.containsBean(ref))
            return true;
        return false;
    }

    void initConstructor(BeanDefinition bean, IClassModel classModel) {
        if (bean.getSupplier() != null)
            return;

        BeanValue beanModel = bean.getBeanModel();
        List<BeanConstructorArgModel> argsModel = beanModel.getConstructorArgs();
        if (argsModel == null)
            argsModel = Collections.emptyList();
        if (argsModel.isEmpty()) {
            autowireConstructorArgs(bean, classModel);
        }

        if (bean.getConstructor() != null)
            return;

        IFunctionModel constructor = classModel.getConstructor(argsModel.size());
        if (constructor == null)
            throw new NopException(ERR_IOC_MISSING_CONSTRUCTOR).source(bean).param(ARG_PARAM_COUNT, argsModel.size())
                    .param(ARG_BEAN_NAME, bean.getId()).param(ARG_TRACE, bean.getTrace());

        bean.setConstructor(constructor);

        buildConstructorArgs(bean, constructor);
    }

    void buildConstructorArgs(BeanDefinition bean, IFunctionModel constructor) {
        List<BeanConstructorArgModel> argsModel = bean.getBeanModel().getConstructorArgs();
        List<IBeanPropValueResolver> args = new ArrayList<>(argsModel.size());
        for (BeanConstructorArgModel argModel : argsModel) {
            IGenericType type = constructor.getArgs().get(argModel.getIndex()).getType();
            IBeanPropValueResolver resolver;
            if (!StringHelper.isEmpty(argModel.getRef())) {
                resolver = buildRefResolver(bean, argModel.getLocation(), String.valueOf(argModel.getIndex()),
                        argModel.getRef(), false, false);
            } else if (argModel.getBody() != null) {
                IBeanPropValue value = argModel.getBody();
                resolver = buildResolver(bean, String.valueOf(argModel.getIndex()), value, type);
            } else {
                resolver = buildValueResolver(bean, argModel.getLocation(), String.valueOf(argModel.getIndex()),
                        argModel.getValue(), type);
            }
            if (resolver == null)
                resolver = NullValueResolver.INSTANCE;
            args.add(resolver);
        }
        bean.setConstructorArgs(args);
    }

    Class<?> loadBeanClass(BeanDefinition bean) {
        if (bean.getBeanClass() != null)
            return bean.getBeanClass();

        BeanValue beanModel = bean.getBeanModel();
        String className = beanModel.getClassName();
        if (StringHelper.isEmpty(className)) {
            if (CollectionHelper.getSize(beanModel.getIocType()) == 1) {
                className = CollectionHelper.first(beanModel.getIocType());
            }
            if (StringHelper.isEmpty(className)) {
                if (beanModel.getFactoryBean() != null)
                    return null;
                throw new NopException(ERR_IOC_EMPTY_CLASS_NAME).param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN,
                        bean);
            }
        }

        Class<?> clazz = loadBeanClass(bean, bean.getLocation(), className);
        return clazz;
    }

    IClassModel initClassModel(BeanDefinition bean) {
        Class<?> clazz = bean.getBeanClass();
        return ReflectionManager.instance().getClassModel(clazz);
    }

    void initMethods(BeanDefinition bean, IClassModel classModel) {
        BeanValue beanModel = bean.getBeanModel();

        if (beanModel.getFactoryMethod() != null && beanModel.getFactoryBean() == null) {
            IFunctionModel factoryMethod = classModel.getStaticMethod(beanModel.getFactoryMethod(),
                    beanModel.getConstructorArgCount());
            checkMethodNotNull(factoryMethod, bean, classModel, beanModel.getFactoryMethod());
            bean.setFactoryMethod(factoryMethod);
        }

        if (beanModel.getInitMethod() != null) {
            IFunctionModel initMethod = classModel.getMethod(beanModel.getInitMethod(), 0);
            checkMethodNotNull(initMethod, bean, classModel, beanModel.getInitMethod());
            bean.setInitMethod(initMethod);
        } else {
            IFunctionModel initMethod = introspection.getInitMethod(classModel);
            bean.setInitMethod(initMethod);
        }

        if (beanModel.getDestroyMethod() != null) {
            IFunctionModel destroyMethod = classModel.getMethod(beanModel.getDestroyMethod(), 0);
            checkMethodNotNull(destroyMethod, bean, classModel, beanModel.getDestroyMethod());
            bean.setDestroyMethod(destroyMethod);
        } else {
            IFunctionModel destroyMethod = introspection.getDestroyMethod(classModel);
            bean.setDestroyMethod(destroyMethod);
        }

        if (beanModel.getIocDelayMethod() != null) {
            IFunctionModel delayMethod = classModel.getMethod(beanModel.getIocDelayMethod(), 0);
            checkMethodNotNull(delayMethod, bean, classModel, beanModel.getIocDelayMethod());
            bean.setDelayMethod(delayMethod);
        }

        if (beanModel.getIocRestartMethod() != null) {
            IFunctionModel restartMethod = classModel.getMethod(beanModel.getIocRestartMethod(), 0);
            checkMethodNotNull(restartMethod, bean, classModel, beanModel.getIocRestartMethod());
            bean.setRestartMethod(restartMethod);
        }

        if (beanModel.getIocRefreshConfigMethod() != null) {
            IFunctionModel refreshConfigMethod = classModel.getMethod(beanModel.getIocRefreshConfigMethod(), 0);
            checkMethodNotNull(refreshConfigMethod, bean, classModel, beanModel.getIocRefreshConfigMethod());
            bean.setRefreshConfigMethod(refreshConfigMethod);
        } else {
            IFunctionModel refreshConfigModel = introspection.getRefreshConfigMethod(classModel);
            if (refreshConfigModel != null)
                bean.setRefreshConfigMethod(refreshConfigModel);
        }

        if (beanModel.getIocBeanMethod() != null) {
            IFunctionModel beanMethod = classModel.getMethod(beanModel.getIocBeanMethod(), 0);
            checkMethodNotNull(beanMethod, bean, classModel, beanModel.getIocBeanMethod());
            bean.setBeanMethod(beanMethod);
        } else {
            IFunctionModel beanMethod = introspection.getBeanMethod(classModel);
            bean.setBeanMethod(beanMethod);
        }

        if (bean.getBeanMethod() != null && bean.getDelayMethod() != null)
            throw new NopException(ERR_IOC_NOT_ALLOW_BOTH_DELAY_METHOD_AND_BEAN_METHOD).source(bean)
                    .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN, bean);

        if (bean.getBeanMethod() != null && bean.getFactoryMethod() != null)
            throw new NopException(ERR_IOC_NOT_ALLOW_BOTH_FACTORY_METHOD_AND_BEAN_METHOD).source(bean)
                    .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN, bean);

        if (bean.getBeanTypes() == null || bean.getBeanTypes().isEmpty()) {
            Class<?> beanClass;
            if (bean.getBeanMethod() != null) {
                beanClass = bean.getBeanMethod().getReturnClass();
            } else if (bean.getFactoryMethod() != null) {
                beanClass = bean.getFactoryMethod().getReturnClass();
            } else if (bean.getBeanClass() == null) {
                // 通过factory-bean的factory-method创建，但是没有通过ioc:type来指定返回类型，则认为返回Object类型
                beanClass = Object.class;
            } else {
                beanClass = bean.getBeanClass();
            }
            bean.setBeanTypes(Collections.singletonList(beanClass));
        }
    }

    void checkMethodNotNull(IFunctionModel method, BeanDefinition bean, IClassModel classModel, String methodName) {
        if (method == null)
            throw new NopException(ERR_IOC_UNKNOWN_BEAN_METHOD).param(ARG_BEAN, bean)
                    .param(ARG_CLASS_NAME, classModel.getClassName()).param(ARG_METHOD_NAME, methodName);
    }

    void checkConstructorArg(BeanDefinition bean) {
        BeanValue beanModel = bean.getBeanModel();
        List<BeanConstructorArgModel> args = beanModel.getConstructorArgs();
        if (args == null || args.isEmpty())
            return;

        args.sort(Comparator.comparing(BeanConstructorArgModel::getIndex));
        for (int i = 0, n = args.size(); i < n; i++) {
            BeanConstructorArgModel arg = args.get(i);
            if (arg.getIndex() != i)
                throw new NopException(ERR_IOC_INVALID_CONSTRUCTOR_ARG_INDEX).source(arg)
                        .param(ARG_INDEX, arg.getIndex()).param(ARG_EXPECTED, i)
                        .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN, bean);
        }
    }

    Class<?> loadBeanClass(BeanDefinition bean, SourceLocation loc, String className) {
        return loadBeanClass(classLoader, bean, loc, className);
    }

    static Class<?> loadBeanClass(IClassLoader classLoader, BeanDefinition bean, SourceLocation loc, String className) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            return clazz;
        } catch (ClassNotFoundException e) {
            throw new NopException(ERR_IOC_CLASS_NOT_FOUND, e).loc(loc).param(ARG_CLASS_NAME, className)
                    .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN, bean);
        }
    }

    void autowireConstructorArgs(BeanDefinition bean, IClassModel classModel) {
        List<? extends IFunctionModel> constructors = classModel.getConstructors();
        if (!constructors.isEmpty()) {
            IFunctionModel[] array = constructors.toArray(EMPTY_METHODS);
            // 优先选择public且参数个数最少的constructor
            Arrays.sort(array, (o1, o2) -> {
                int result = Boolean.compare(Modifier.isPublic(o2.getModifiers()),
                        Modifier.isPublic(o1.getModifiers()));
                return result != 0 ? result : Integer.compare(o1.getArgCount(), o2.getArgCount());
            });
            IFunctionModel constructor = array[0];
            if (constructor.getArgCount() > 0) {
                List<IBeanPropValueResolver> args = new ArrayList<>(constructor.getArgCount());
                for (IFunctionArgument argModel : constructor.getArgs()) {
                    BeanInjectInfo injectInfo = introspection.getArgumentInject(argModel);
                    IBeanPropValueResolver resolver = buildInjectResolver(bean, bean.getLocation(), "constructor",
                            injectInfo, injectInfo.isIgnoreDepends());
                    if (resolver == null)
                        resolver = NullValueResolver.INSTANCE;
                    args.add(resolver);
                }
                bean.setConstructorArgs(args);
                bean.setConstructor(constructor);
                bean.setConstructorAutowired(true);
            }
        }
    }

    void initProps(BeanDefinition bean, IClassModel classModel) {
        BeanValue beanModel = bean.getBeanModel();
        for (BeanPropertyModel propModel : beanModel.getProperties()) {
            IBeanPropValue body = propModel.getBody();
            Pair<IPropertySetter, IGenericType> setter = getSetter(bean, classModel, propModel);
            IBeanPropValueResolver resolver;
            if (body == null) {
                String ref = propModel.getRef();
                if (!StringHelper.isEmpty(ref)) {
                    resolver = buildRefResolver(bean, propModel.getLocation(), propModel.getName(), ref, false, propModel.isIocIgnoreDepends());
                } else {
                    resolver = buildValueResolver(bean, propModel.getLocation(), propModel.getName(),
                            propModel.getValue(), setter.getRight());
                }
            } else {
                resolver = buildResolver(bean, propModel.getName(), body, setter.getRight());
            }

            if (resolver == null)
                continue;
            bean.addProp(propModel.getName(), new BeanProperty(propModel.getLocation(), setter.getLeft(),
                    setter.getRight().getRawClass(), resolver, false, propModel.isIocSkipIfEmpty()));
        }

        AutowireType autowireType = beanModel.getAutowire();
        if (autowireType == null)
            autowireType = AutowireType.byType;

        // 如果明确禁止自动装配，则不再查找注解
        if (autowireType == AutowireType.no)
            return;

        autowireProps(bean, classModel);
    }

    void autowireProps(BeanDefinition bean, IClassModel classModel) {
        for (IFunctionModel method : classModel.getMethods()) {
            if (method.getArgCount() == 1) {
                String propName = method.getName();
                boolean bSet = false;
                if (propName.startsWith("set")) {
                    propName = StringHelper.beanPropName(propName.substring("set".length()));
                    bSet = true;
                }

                // 如果已经明确指定注入属性，则忽略autowire配置
                if (bean.getProp(propName) != null)
                    continue;

                BeanInjectInfo injectInfo = introspection.getPropertyInject(propName, method);
                if (injectInfo != null) {
                    SourceLocation loc = bean.getLocation();
                    IBeanPropValueResolver resolver = buildInjectResolver(bean, loc, propName, injectInfo, injectInfo.isIgnoreDepends());

                    // optional的情况下有可能找不到autowire的bean
                    if (resolver != null) {
                        IPropertySetter setter = new FunctionSpecializedPropertySetter(method);
                        bean.addProp(propName, new BeanProperty(loc, setter, method.getArgs().get(0).getRawClass(), resolver, true, false));
                    }
                } else if (bSet) {
                    autowireConfigVar(bean, classModel, propName);
                }
            }
        }

        for (IFieldModel field : classModel.getFields().values()) {
            if (field.getSetter() == null)
                continue;

            if (bean.getProp(field.getName()) != null)
                continue;

            String propName = field.getName();
            BeanInjectInfo injectInfo = introspection.getFieldInject(field);
            if (injectInfo != null) {
                SourceLocation loc = bean.getLocation();
                IBeanPropValueResolver resolver = buildInjectResolver(bean, loc, propName, injectInfo, injectInfo.isIgnoreDepends());

                if (resolver != null) {
                    IPropertySetter setter = field.getSetter();
                    bean.addProp(propName, new BeanProperty(loc, setter, field.getRawClass(), resolver, true, false));
                } else {
                    LOG.info("nop.ioc.ignore-optional-inject:prop={},bean={}", propName, bean);
                }
            }
        }
    }

    private void autowireConfigVar(BeanDefinition bean, IClassModel classModel, String propName) {
        String configPrefix = bean.getBeanModel().getIocConfigPrefix();
        if (configPrefix != null) {
            IBeanPropertyModel propModel = classModel.getBeanModel().getPropertyModel(propName);
            if (propModel == null)
                return;
            IBeanPropValueResolver resolver = ConfigPropHelper.buildConfigVarResolver(propModel,
                    bean.getLocation(), configPrefix, introspection);
            if (resolver != null) {
                bean.addProp(propName, new BeanProperty(null, propModel.getSetter(),
                        propModel.getRawClass(), resolver, true, true));
            }
        }
    }

    Pair<IPropertySetter, IGenericType> getSetter(BeanDefinition bean, IClassModel classModel, BeanPropertyModel prop) {
        String propName = prop.getName();

        // 这里没有要求函数的返回值为null，因此可以兼容非JavaBean标准的属性设置
        String methodName = "set" + StringHelper.capitalize(propName);
        IFunctionModel func = classModel.getMethod(methodName, 1);
        if (func == null) {
            func = classModel.getMethod(propName, 1);
        }

        if (func != null)
            return Pair.of(new FunctionSpecializedPropertySetter(func), func.getArgs().get(0).getType());

        IFieldModel field = classModel.getField(propName);
        if (field != null) {
            IPropertySetter setter = field.getSetter();
            if (setter != null)
                return Pair.of(setter, field.getType());
        }

        throw new NopException(ERR_IOC_UNKNOWN_BEAN_PROP).source(prop).param(ARG_BEAN_NAME, bean.getId())
                .param(ARG_BEAN, bean).param(ARG_CLASS_NAME, classModel.getClassName()).param(ARG_PROP_NAME, propName);
    }

    IBeanPropValueResolver buildResolver(BeanDefinition bean, String propName, IBeanPropValue value,
                                         IGenericType expectedType) {
        if (value == null || value instanceof BeanNullValue)
            return NullValueResolver.INSTANCE;

        if (value instanceof BeanRefValue) {
            BeanRefValue model = (BeanRefValue) value;
            return buildRefResolver(bean, model.getLocation(), propName, model.getBean(), model.isIocOptional(),
                    model.isIocIgnoreDepends());
        }

        if (value instanceof BeanIdRefValue) {
            BeanIdRefValue model = (BeanIdRefValue) value;
            if (!containsBean(model.getBean())) {
                if (model.isIocOptional())
                    return NullValueResolver.INSTANCE;

                throw new NopException(ERR_IOC_UNKNOWN_BEAN_REF).source(model).param(ARG_BEAN_NAME, model.getBean())
                        .param(ARG_PROP_NAME, propName).param(ARG_BEAN, bean);
            }
            return new FixedValueResolver(model.getBean());
        }

        if (value instanceof BeanIocInjectValue) {
            BeanIocInjectValue model = (BeanIocInjectValue) value;
            IGenericType type = model.getType();
            String beanName = resolveBeanByType(bean, model.getLocation(), type, model.isIocOptional(), propName);
            if (beanName == null) {
                return NullValueResolver.INSTANCE;
            }
            return buildRefResolver(bean, model.getLocation(), propName, beanName, model.isIocOptional(),
                    model.isIocIgnoreDepends());
        }

        if (value instanceof BeanSimpleValue) {
            BeanSimpleValue model = (BeanSimpleValue) value;
            return buildValueResolver(bean, model.getLocation(), propName, model.getBody(), expectedType);
        }

        if (value instanceof BeanXplValue) {
            BeanXplValue model = (BeanXplValue) value;
            return new XplValueResolver(model.getSource());
        }

        if (value instanceof BeanCollectBeansValue) {
            BeanCollectBeansValue model = (BeanCollectBeansValue) value;
            return buildCollectBeansResolver(bean, model);
        }

        if (value instanceof BeanValue) {
            BeanValue model = (BeanValue) value;
            return buildRefResolver(bean, model.getLocation(), propName, model.getEmbeddedId(), false, false);
        }

        if (value instanceof BeanPropsValue) {
            BeanPropsValue model = (BeanPropsValue) value;
            Map<String, IBeanPropValueResolver> props = buildPropEntryResolvers(bean, propName, model.getBody());
            return new PropsValueResolver(props);
        }

        if (value instanceof BeanMapValue) {
            BeanMapValue model = (BeanMapValue) value;
            Map<String, IBeanPropValueResolver> props = buildEntryResolvers(bean, propName, model.getBody());
            Class<?> type = model.getMapClass() == null ? LinkedHashMap.class
                    : loadBeanClass(bean, model.getLocation(), model.getMapClass());
            return new MapValueResolver(type, props);
        }

        if (value instanceof BeanListValue) {
            BeanListValue model = (BeanListValue) value;
            List<IBeanPropValueResolver> items = buildItemsResolver(bean, propName, model.getBody());
            Class<?> type = model.getListClass() == null ? ArrayList.class
                    : loadBeanClass(bean, model.getLocation(), model.getListClass());
            return new ListValueResolver(type, items);
        }

        if (value instanceof BeanSetValue) {
            BeanSetValue model = (BeanSetValue) value;
            List<IBeanPropValueResolver> items = buildItemsResolver(bean, propName, model.getBody());
            Class<?> type = model.getSetClass() == null ? ArrayList.class
                    : loadBeanClass(bean, model.getLocation(), model.getSetClass());
            return new SetValueResolver(type, items);
        }

        if (value instanceof BeanConstantValue) {
            BeanConstantValue model = (BeanConstantValue) value;

            IFieldModel field = resolveStaticField(classLoader, bean, model);

            return new ConstantValueResolver(model.getStaticField(), field);
        }

        throw new IllegalArgumentException("nop.err.ioc.invalid-prop-value-type:" + value);
    }

    public static IFieldModel resolveStaticField(IClassLoader classLoader, BeanDefinition bean,
                                                 BeanConstantValue model) {
        String staticField = model.getStaticField();
        int pos = staticField.lastIndexOf('.');
        if (pos < 0)
            throw new NopException(ERR_IOC_INVALID_STATIC_FIELD).source(model).param(ARG_BEAN_NAME, bean.getId())
                    .param(ARG_BEAN, bean).param(ARG_STATIC_FIELD, staticField);

        String className = staticField.substring(0, pos);
        Class<?> clazz = loadBeanClass(classLoader, bean, model.getLocation(), className);
        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        IFieldModel field = classModel.getStaticField(staticField.substring(pos + 1));
        if (field == null)
            throw new NopException(ERR_IOC_CLASS_NO_FIELD).source(model).param(ARG_BEAN_NAME, bean.getId())
                    .param(ARG_BEAN, bean).param(ARG_CLASS_NAME, className)
                    .param(ARG_FIELD_NAME, staticField.substring(pos + 1)).param(ARG_STATIC_FIELD, staticField);
        return field;
    }

    Map<String, IBeanPropValueResolver> buildEntryResolvers(BeanDefinition bean, String propName,
                                                            List<BeanEntryValue> entries) {
        Map<String, IBeanPropValueResolver> map = new LinkedHashMap<>();
        if (entries != null) {
            for (BeanEntryValue entry : entries) {
                String ref = entry.getValueRef();
                String value = entry.getValue();
                IBeanPropValueResolver resolver;
                if (!StringHelper.isEmpty(ref)) {
                    resolver = buildRefResolver(bean, entry.getLocation(), propName, ref, false, false);
                } else if (!StringHelper.isEmpty(value)) {
                    resolver = buildValueResolver(bean, entry.getLocation(), propName, value, null);
                } else {
                    resolver = buildResolver(bean, propName, entry.getBody(), null);
                }

                if (resolver == null)
                    continue;
                map.put(entry.getKey(), resolver);
            }
        }
        return map;
    }

    Map<String, IBeanPropValueResolver> buildPropEntryResolvers(BeanDefinition bean, String propName,
                                                                List<BeanPropEntryValue> entries) {
        Map<String, IBeanPropValueResolver> map = new LinkedHashMap<>();
        if (entries != null) {
            for (BeanPropEntryValue entry : entries) {
                String value = entry.getBody();
                IBeanPropValueResolver resolver = buildValueResolver(bean, entry.getLocation(), propName, value, null);
                map.put(entry.getKey(), resolver);
            }
        }
        return map;
    }

    IBeanPropValueResolver buildCollectBeansResolver(BeanDefinition bean, BeanCollectBeansValue model) {
        String ann = model.getByAnnotation();
        String type = model.getByType();

        Class<? extends Annotation> annType = null;
        if (!StringHelper.isEmpty(ann)) {
            annType = (Class<? extends Annotation>) loadBeanClass(bean, model.getLocation(), ann);
        }

        Class<?> beanType = null;
        if (!StringHelper.isEmpty(type)) {
            beanType = loadBeanClass(bean, model.getLocation(), type);
        }

        List<BeanDefinition> matched = BeanFinder.collectBeans(beanTypeMappings, annMappings, uniqueBeans.values(), beanType, annType);
        Map<String, BeanDefinition> namedMatched = Collections.emptyMap();
        boolean useNamePrefix = !StringHelper.isEmpty(model.getNamePrefix());
        if (useNamePrefix) {
            namedMatched = new LinkedHashMap<>();
            for (Map.Entry<String, BeanDefinition> entry : allBeans.entrySet()) {
                if (entry.getKey().startsWith(model.getNamePrefix())) {
                    namedMatched.put(entry.getKey().substring(model.getNamePrefix().length()), entry.getValue());
                }
            }
        }

        if (model.isAsMap()) {
            Map<String, IBeanPropValueResolver> resolvers = new LinkedHashMap<>();
            if (useNamePrefix) {
                for (Map.Entry<String, BeanDefinition> entry : namedMatched.entrySet()) {
                    BeanDefinition matchedBean = entry.getValue();
                    if (!matchTag(matchedBean, model))
                        continue;
                    if (beanType != null || annType != null) {
                        if (!matched.contains(matchedBean))
                            continue;
                    }
                    String name = entry.getKey();
                    resolvers.put(name,
                            new InjectRefValueResolver(matchedBean.getId(), false,
                                    model.isIocIgnoreDepends(), matchedBean));
                }
            } else {
                for (BeanDefinition matchedBean : matched) {
                    if (!matchTag(matchedBean, model))
                        continue;
                    resolvers.put(matchedBean.getId(),
                            new InjectRefValueResolver(matchedBean.getId(), false,
                                    model.isIocIgnoreDepends(), matchedBean));
                }
            }
            return new MapValueResolver(LinkedHashMap.class, resolvers);
        } else {
            List<IBeanPropValueResolver> items = new ArrayList<>(matched.size());
            if (beanType != null || annType != null) {
                for (BeanDefinition matchedBean : matched) {
                    if (!matchTag(matchedBean, model))
                        continue;
                    if (useNamePrefix) {
                        if (!namedMatched.containsValue(matchedBean)) {
                            continue;
                        }
                    }
                    items.add(new InjectRefValueResolver(matchedBean.getId(), false, model.isIocIgnoreDepends(), matchedBean));
                }
            } else {
                // 没有类型过滤，则完全按照名称过滤
                if (useNamePrefix) {
                    LinkedHashSet<BeanDefinition> set = new LinkedHashSet<>(namedMatched.values());
                    for (BeanDefinition matchedBean : set) {
                        if (!matchTag(matchedBean, model))
                            continue;
                        items.add(new InjectRefValueResolver(matchedBean.getId(), false, model.isIocIgnoreDepends(), matchedBean));
                    }
                }
            }
            ListValueResolver resolver = new ListValueResolver(ArrayList.class, items);
            return resolver;
        }
    }

    boolean matchTag(BeanDefinition bean, BeanCollectBeansValue value) {
        if (value.isOnlyConcreteClasses()) {
            if (!bean.isConcreteClass())
                return false;
        }

        if (value.getIncludeTag() != null) {
            for (String tag : value.getIncludeTag()) {
                if (!bean.containsTag(tag))
                    return false;
            }
        }

        if (value.getExcludeTag() != null) {
            for (String tag : value.getExcludeTag()) {
                if (bean.containsTag(tag))
                    return false;
            }
        }
        return true;
    }

    List<IBeanPropValueResolver> buildItemsResolver(BeanDefinition bean, String propName, List<IBeanPropValue> items) {
        if (items == null)
            items = new ArrayList<>(0);

        List<IBeanPropValueResolver> ret = new ArrayList<>(items.size());
        for (IBeanPropValue item : items) {
            IBeanPropValueResolver resolver = buildResolver(bean, propName, item, null);
            if (resolver == null)
                continue;
            ret.add(resolver);
        }
        return ret;
    }

    IBeanPropValueResolver buildValueResolver(BeanDefinition bean, SourceLocation loc, String propName, String value,
                                              IGenericType expectedType) {
        if (StringHelper.isEmpty(value))
            return NullValueResolver.INSTANCE;

        if (value.startsWith(IocConstants.PREFIX_INJECT_REF)) {
            String ref = value.substring(IocConstants.PREFIX_INJECT_REF.length());
            boolean optional = false;
            boolean ignoreDepends = false;
            while (true) {
                if (ref.startsWith("?")) {
                    ref = ref.substring(1);
                    optional = true;
                    continue;
                }
                if (ref.startsWith("~")) {
                    ref = ref.substring(1);
                    ignoreDepends = true;
                    continue;
                } else {
                    break;
                }
            }
            return buildRefResolver(bean, loc, propName, ref, optional, ignoreDepends);
        } else if (value.startsWith(IocConstants.PREFIX_INJECT_TYPE)) {
            String type = value.substring(IocConstants.PREFIX_INJECT_TYPE.length());
            boolean optional = false;
            if (type.startsWith("?")) {
                type = type.substring(1);
                optional = true;
            }
            IClassModel classModel = ReflectionManager.instance().loadClassModel(type);
            String beanName = resolveBeanByType(bean, loc, classModel.getType(), optional, propName);
            if (beanName == null)
                return NullValueResolver.INSTANCE;
            return buildRefResolver(bean, loc, propName, beanName, optional, false);
        } else {
            return ConfigExpressionProcessor.INSTANCE.process(bean, loc, propName, value, expectedType);
        }
    }

    IBeanPropValueResolver buildInjectResolver(BeanDefinition bean, SourceLocation loc, String propName,
                                               BeanInjectInfo injectInfo, boolean ignoreDepends) {
        String name = injectInfo.getRef();
        if (!StringHelper.isEmpty(name)) {
            return buildRefResolver(bean, loc, propName, name, injectInfo.isOptional(), ignoreDepends);
        }

        if (injectInfo.getValue() != null) {
            return buildValueResolver(bean, loc, propName, injectInfo.getValue(), injectInfo.getType());
        }

        IGenericType beanType = injectInfo.getType();
        if (beanType != null) {
            name = resolveBeanByType(bean, loc, beanType, injectInfo.isOptional(), propName);
        }

        if (name == null)
            return null;

        return buildRefResolver(bean, loc, propName, name, injectInfo.isOptional(), ignoreDepends);
    }

    IBeanPropValueResolver buildRefResolver(BeanDefinition bean, SourceLocation loc, String propName, String ref,
                                            boolean optional, boolean ignoreDepends) {
        if (aliases != null) {
            AliasName aliasName = aliases.get(ref);
            if (aliasName != null) {
                ref = aliasName.getName();
            }
        }

        BeanDefinition resolvedBean = allBeans.get(ref);
        if (resolvedBean == null) {
            if (parentContainer != null && parentContainer.containsBean(ref))
                return new InjectRefValueResolver(ref, optional, ignoreDepends, null);

            if (optional) {
                LOG.info("nop.ioc.ignore-optional-ref-bean:ref={},propName={},bean={}", ref, propName, bean);
                return null;
            } else {
                throw new NopException(ERR_IOC_UNKNOWN_BEAN_REF).loc(loc).param(ARG_BEAN_NAME, bean.getId())
                        .param(ARG_BEAN_REF, ref).param(ARG_PROP_NAME, propName).param(ARG_BEAN, bean);
            }
        }

        if (resolvedBean.getBeanModel() instanceof BeanConfigModel) {
            resolvedBean.addConfigDependant(bean.getId());
        }

        return new InjectRefValueResolver(ref, optional, ignoreDepends, resolvedBean);
    }

    String resolveBeanByType(BeanDefinition bean, SourceLocation loc, IGenericType beanType, boolean optional,
                             String propName) {
        BeanTypeMapping mapping = BeanFinder.getBeansByType(beanTypeMappings, uniqueBeans.values(), beanType.getRawClass());
        if (mapping.isEmpty()) {
            if (parentContainer != null) {
                String beanId = parentContainer.findAutowireCandidate(beanType.getRawClass());
                if (beanId != null)
                    return beanId;
            }

            if (optional) {
                LOG.info("nop.ioc.ignore-optional-ref-bean-by-type:type={},propName={},bean={}", beanType, propName,
                        bean);
                return null;
            }
            throw new NopException(ERR_IOC_NOT_FIND_BEAN_WITH_TYPE).loc(loc).param(ARG_BEAN, bean)
                    .param(ARG_BEAN_NAME, bean.getId()).param(ARG_BEAN_TYPE, beanType).param(ARG_PROP_NAME, propName);
        } else if (mapping.getOtherPrimaryBean() != null) {
            throw new NopException(ERR_IOC_MULTIPLE_PRIMARY_BEAN).loc(loc).param(ARG_BEAN_NAME, bean.getId())
                    .param(ARG_BEAN_TYPE, beanType).param(ARG_PROP_NAME, propName)
                    .param(ARG_BEAN, mapping.getPrimaryBean()).param(ARG_OTHER_BEAN, mapping.getOtherPrimaryBean());
        } else if (mapping.getPrimaryBean() != null) {
            return mapping.getPrimaryBean().getId();
        } else {
            throw new NopException(ERR_IOC_MULTIPLE_BEAN_WITH_TYPE).loc(loc).param(ARG_BEAN_NAME, bean.getId())
                    .param(ARG_BEAN_TYPE, beanType).param(ARG_PROP_NAME, propName).param(ARG_BEANS, mapping.getBeans());
        }
    }

    void initInterceptors(BeanDefinition bean) {
        BeanValue beanModel = bean.getBeanModel();
        if (!beanModel.hasIocInterceptors())
            return;

        List<BeanInterceptorModel> interceptors = beanModel.getIocInterceptors();
        for (BeanInterceptorModel interceptor : interceptors) {
            String ref = interceptor.getBean();
            if (!containsBean(ref)) {
                throw new NopException(ERR_IOC_UNKNOWN_DEPEND_REF).source(interceptor)
                        .param(ARG_BEAN_NAME, bean.getId()).param(ARG_DEPEND, ref).param(ARG_BEAN, bean);
            }
        }
    }
}