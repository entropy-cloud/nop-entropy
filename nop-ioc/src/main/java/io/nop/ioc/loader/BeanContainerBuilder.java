/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.IClassLoader;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.resource.IResource;
import io.nop.ioc.IocConstants;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.impl.BeanContainerDumper;
import io.nop.ioc.impl.BeanContainerImpl;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.impl.IBeanClassIntrospection;
import io.nop.ioc.model.BeanAliasModel;
import io.nop.ioc.model.BeanConditionModel;
import io.nop.ioc.model.BeanConstantModel;
import io.nop.ioc.model.BeanConstantValue;
import io.nop.ioc.model.BeanImportModel;
import io.nop.ioc.model.BeanListModel;
import io.nop.ioc.model.BeanListValue;
import io.nop.ioc.model.BeanMapModel;
import io.nop.ioc.model.BeanMapValue;
import io.nop.ioc.model.BeanModel;
import io.nop.ioc.model.BeanPropertyModel;
import io.nop.ioc.model.BeanSetModel;
import io.nop.ioc.model.BeanSetValue;
import io.nop.ioc.model.BeansModel;
import io.nop.ioc.model.IBeanPropValue;
import io.nop.ioc.support.UtilFactoryBean;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xmeta.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.nop.ioc.IocConfigs.CFG_IOC_AOP_ENABLED;

public class BeanContainerBuilder implements IBeanContainerBuilder {
    static final Logger LOG = LoggerFactory.getLogger(BeanContainerBuilder.class);

    private final BeansDefinition beans = new BeansDefinition();
    private final IClassLoader classLoader;
    private final IBeanClassIntrospection introspection;

    private final IBeanContainer parentContainer;

    private BeanContainerStartMode startMode;

    public BeanContainerBuilder(IClassLoader classLoader, IBeanClassIntrospection introspection,
                                IBeanContainer parentContainer) {
        this.classLoader = classLoader;
        this.introspection = introspection;
        this.parentContainer = parentContainer;
    }

    @Override
    public IBeanContainerBuilder startMode(BeanContainerStartMode startMode) {
        this.startMode = startMode;
        return this;
    }

    @Override
    public IBeanContainerBuilder addResource(IResource resource) {
        BeansModel beans = (BeansModel) new DslModelParser(IocConstants.XDEF_BEANS).parseFromResource(resource);
        this.beans.merge(buildDefinition(beans, null));
        return this;
    }

    @Override
    public IBeanContainerBuilder addBeans(XNode beansNode) {
        IXDefinition xdef = SchemaLoader.loadXDefinition(IocConstants.XDEF_BEANS);
        BeansModel beans = (BeansModel) new DslModelParser(IocConstants.XDEF_BEANS).parseWithXDef(xdef, beansNode);
        this.beans.merge(buildDefinition(beans, null));
        return this;
    }

    @Override
    public <T> IBeanContainerBuilder registerBean(String beanName, Class<T> beanClass,
                                                  Function<IBeanContainerImplementor, T> supplier, Consumer<BeanModel> customizer) {
        BeanModel beanModel = new BeanModel();
        beanModel.setId(beanName);
        beanModel.setPrimary(true);

        if (customizer != null) {
            customizer.accept(beanModel);
        }

        BeanDefinition beanDef = new BeanDefinition(beanModel);
        beanDef.setBeanClass(beanClass);
        beanDef.setSupplier(supplier);
        beans.replaceBean(beanDef);
        return this;
    }

    BeansDefinition buildDefinition(BeansModel beansModel, String trace) {
        BeansDefinition beans = new BeansDefinition();
        for (BeanAliasModel aliasModel : beansModel.getAliases()) {
            for (String alias : aliasModel.getAlias()) {
                beans.addAlias(aliasModel.getLocation(), aliasModel.getName(), alias, trace);
            }
        }

        String childTrace = beansModel.resourcePath();
        if (trace != null) {
            childTrace = beansModel.resourcePath() + "<==" + trace;
        }

        for (BeanImportModel importModel : beansModel.getImports()) {
            String path = importModel.getResource();

            // 已经处理过的import被忽略
            if (!this.beans.addImported(path))
                continue;

            BeansModel imported = (BeansModel) new DslModelParser(IocConstants.XDEF_BEANS).parseFromVirtualPath(path);
            beans.merge(buildDefinition(imported, childTrace));
        }

        for (BeanModel beanModel : beansModel.getBeans()) {
            BeanDefinition bean = new BeanDefinition(beanModel);
            bean.setTrace(trace);

            normalizeDefaultBean(beanModel);
            beans.addBean(bean);
        }

        for (BeanConstantModel constModel : beansModel.getUtilConstants()) {
            BeanConstantValue value = new BeanConstantValue();
            value.setLocation(constModel.getLocation());
            value.setStaticField(constModel.getStaticField());
            BeanDefinition bean = newUtilFactoryBean(constModel.getLocation(), constModel.getId(),
                    IocConstants.DEFAULT_INIT_ORDER, constModel.isIocDefault(), value);
            bean.setTrace(trace);

            Class<?> fieldType = tryGetStaticFieldType(bean, value);
            if (fieldType != null)
                bean.getBeanModel().setIocType(Collections.singleton(fieldType.getCanonicalName()));

            beans.addBean(bean);
        }

        for (BeanListModel utilModel : beansModel.getUtilLists()) {
            BeanListValue value = new BeanListValue();
            value.setLocation(utilModel.getLocation());
            value.setBody(utilModel.getBody());
            value.setListClass(utilModel.getListClass());
            value.setMerge(utilModel.isMerge());
            BeanDefinition bean = newUtilFactoryBean(utilModel.getLocation(), utilModel.getId(),
                    utilModel.getIocInitOrder(), utilModel.isIocDefault(), value);
            if (utilModel.getListClass() != null)
                bean.getBeanModel().setIocType(Collections.singleton(utilModel.getListClass()));
            bean.setTrace(trace);
            beans.addBean(bean);
        }

        for (BeanSetModel utilModel : beansModel.getUtilSets()) {
            BeanSetValue value = new BeanSetValue();
            value.setLocation(utilModel.getLocation());
            value.setBody(utilModel.getBody());
            value.setSetClass(utilModel.getSetClass());
            value.setMerge(utilModel.isMerge());
            BeanDefinition bean = newUtilFactoryBean(utilModel.getLocation(), utilModel.getId(),
                    utilModel.getIocInitOrder(), utilModel.isIocDefault(), value);

            if (utilModel.getSetClass() != null)
                bean.getBeanModel().setIocType(Collections.singleton(utilModel.getSetClass()));
            bean.setTrace(trace);
            beans.addBean(bean);
        }

        for (BeanMapModel utilModel : beansModel.getUtilMaps()) {
            BeanMapValue value = new BeanMapValue();
            value.setLocation(utilModel.getLocation());
            value.setBody(utilModel.getBody());
            value.setMapClass(utilModel.getMapClass());
            value.setMerge(utilModel.isMerge());
            BeanDefinition bean = newUtilFactoryBean(utilModel.getLocation(), utilModel.getId(),
                    utilModel.getIocInitOrder(), utilModel.isIocDefault(), value);

            if (utilModel.getMapClass() != null)
                bean.getBeanModel().setIocType(Collections.singleton(utilModel.getMapClass()));

            bean.setTrace(trace);
            beans.addBean(bean);
        }

        return beans;
    }

    Class<?> tryGetStaticFieldType(BeanDefinition bean, BeanConstantValue model) {
        try {
            IFieldModel field = BeanDefinitionBuilder.resolveStaticField(classLoader, bean, model);
            return field.getRawClass();
        } catch (Exception e) {
            if (bean.getCondition() == null)
                throw e;
            // 如果具有条件，则可以先忽略，后续后再对类型进行处理
            return null;
        }
    }

    void normalizeDefaultBean(BeanModel beanModel) {
        if (!beanModel.isIocDefault())
            return;

        beanModel.setIocDefault(false);
        String id = beanModel.getId();
        if (!id.startsWith(IocConstants.DEFAULT_ID_PREFIX)) {
            beanModel.setId(IocConstants.DEFAULT_ID_PREFIX + id);
            beanModel.addName(id);

            BeanConditionModel condition = beanModel.getIocCondition();
            if (condition == null) {
                condition = new BeanConditionModel();
                beanModel.setIocCondition(condition);
            }
            condition.addMissingBean(id);
        }
    }

    BeanDefinition newUtilFactoryBean(SourceLocation loc, String id, int initOrder, boolean iocDefault,
                                      IBeanPropValue value) {
        BeanModel beanModel = new BeanModel();
        beanModel.setIocInitOrder(initOrder);
        beanModel.setLocation(loc);
        beanModel.setIocDefault(iocDefault);
        beanModel.setId(id);
        beanModel.setClassName(UtilFactoryBean.class.getName());
        beanModel.setIocBeanMethod("getValue");
        BeanPropertyModel propModel = new BeanPropertyModel();
        propModel.setLocation(loc);
        propModel.setName("value");
        propModel.setBody(value);
        beanModel.addProperty(propModel);
        beanModel.setIocAop(false);

        normalizeDefaultBean(beanModel);
        return new BeanDefinition(beanModel);
    }

    @Override
    public IBeanContainerImplementor build(String containerId) {
        Map<String, BeanDefinition> enabledBeans = new HashMap<>();
        Set<BeanDefinition> optionalBeans = new HashSet<>();

        // 至此，所有的bean都有唯一的id，但是它们的name有可能有重复，按照条件过滤后应该只会保留唯一的一个

        // 1. 检查ioc:condition，处理alias映射，返回所有enabledBeans
        new BeanConditionEvaluator(beans, classLoader, parentContainer).evaluate(enabledBeans, optionalBeans);

        if (LOG.isTraceEnabled()) {
            XNode config = new BeanContainerDumper().toConfigNode(enabledBeans.values());
            config.dump();
        }

        // 2. 将没有命名的内部bean的定义提升到顶层
        new EmbeddedBeanCollector().collect(enabledBeans);

        // 3. 合并parent的属性
        new BeanParentResolver(enabledBeans).resolve();

        // 4. 初始化BeanDefinition中的valueResolver
        new BeanDefinitionBuilder(classLoader, introspection, parentContainer).buildAll(enabledBeans);

        if (CFG_IOC_AOP_ENABLED.get()) {
            // 5. 根据ioc:pointcut配置和ioc:interceptor配置，发现需要进行aop处理的类
            new AopBeanProcessor(classLoader).process(enabledBeans, optionalBeans, parentContainer);
        } else {
            LOG.info(CFG_IOC_AOP_ENABLED.getName() + "=false");
        }

        // 6. 根据ioc:before设置初始化depends-on属性
        new BeanDependsBuilder().buildAll(enabledBeans);

        BeanContainerImpl container = new BeanContainerImpl(containerId, enabledBeans, optionalBeans, parentContainer);
        if (startMode != null)
            container.setStartMode(startMode);
        return container;
    }
}