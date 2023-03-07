/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.model.BeanConstructorArgModel;
import io.nop.ioc.model.BeanPropertyModel;
import io.nop.ioc.model.BeanValue;

import java.util.Map;

import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ARG_LOOP_REF;
import static io.nop.ioc.IocErrors.ARG_PARENT;
import static io.nop.ioc.IocErrors.ARG_TRACE;
import static io.nop.ioc.IocErrors.ERR_IOC_PARENT_REF_CONTAINS_LOOP;
import static io.nop.ioc.IocErrors.ERR_IOC_UNKNOWN_PARENT_REF;

/**
 * 处理spring配置中的parent属性，合并bean的配置
 */
public class BeanParentResolver {
    private final Map<String, BeanDefinition> beans;

    public BeanParentResolver(Map<String, BeanDefinition> beans) {
        this.beans = beans;
    }

    public void resolve() {
        for (BeanDefinition bean : beans.values()) {
            if (bean.getStatus() == BeanDefinition.STATUS_UNRESOLVED) {
                resolveParent(bean);
            }
        }
    }

    void resolveParent(BeanDefinition bean) {
        BeanValue beanModel = bean.getBeanModel();
        String parent = beanModel.getParent();
        if (StringHelper.isEmpty(parent)) {
            bean.setStatus(BeanDefinition.STATUS_RESOLVED);
        } else {
            BeanDefinition parentBean = beans.get(parent);
            if (parentBean == null)
                throw new NopException(ERR_IOC_UNKNOWN_PARENT_REF).source(bean).param(ARG_BEAN_NAME, bean.getId())
                        .param(ARG_PARENT, parent).param(ARG_TRACE, bean.getTrace());

            if (parentBean.getStatus() == BeanDefinition.STATUS_UNRESOLVED) {
                resolveParent(parentBean);
            } else if (parentBean.getStatus() == BeanDefinition.STATUS_RESOLVING) {
                throw new NopException(ERR_IOC_PARENT_REF_CONTAINS_LOOP).source(bean).param(ARG_BEAN_NAME, bean.getId())
                        .param(ARG_PARENT, parent).param(ARG_TRACE, bean.getTrace())
                        .param(ARG_LOOP_REF, parentBean.getId());
            }

            mergeWithParent(bean, parentBean);
        }
    }

    void mergeWithParent(BeanDefinition bean, BeanDefinition parentBean) {
        BeanValue beanModel = bean.getBeanModel();
        BeanValue parentModel = parentBean.getBeanModel();
        if (beanModel.getClassName() == null)
            beanModel.setClassName(parentModel.getClassName());

        if (beanModel.getScope() == null)
            beanModel.setScope(parentModel.getScope());

        if (beanModel.getFactoryBean() == null)
            beanModel.setFactoryBean(parentModel.getFactoryBean());

        if (beanModel.getInitMethod() == null)
            beanModel.setInitMethod(parentModel.getInitMethod());

        if (beanModel.getDestroyMethod() == null)
            beanModel.setDestroyMethod(parentModel.getDestroyMethod());

        if (beanModel.getFactoryMethod() == null)
            beanModel.setFactoryMethod(parentModel.getFactoryMethod());

        if (beanModel.getIocDelayMethod() == null)
            beanModel.setIocDelayMethod(parentModel.getIocDelayMethod());

        if (beanModel.getIocRestartMethod() == null)
            beanModel.setIocRestartMethod(parentModel.getIocRestartMethod());

        if (beanModel.getIocSecurityDomain() == null)
            beanModel.setIocSecurityDomain(parentModel.getIocSecurityDomain());

        if (beanModel.getIocRestart() == null)
            beanModel.setIocRestart(parentModel.getIocRestart());

        if (beanModel.getAutowire() == null)
            beanModel.setAutowire(parentModel.getAutowire());

        if (beanModel.getIocBeanMethod() == null)
            beanModel.setIocBeanMethod(parentModel.getIocBeanMethod());

        if (beanModel.getIocInit() == null) {
            beanModel.setIocInit(parentModel.getIocInit());
        }

        if (beanModel.getIocDestroy() == null) {
            beanModel.setIocDestroy(parentModel.getIocDestroy());
        }

        if (beanModel.getIocDelayStart() == null) {
            beanModel.setIocDelayStart(parentModel.getIocDelayStart());
        }

        if (beanModel.getIocRefreshConfig() == null) {
            beanModel.setIocRefreshConfig(parentModel.getIocRefreshConfig());
        }

        if (beanModel.getIocOnConfigRefresh() == null) {
            beanModel.setIocOnConfigRefresh(parentModel.getIocOnConfigRefresh());
        }

        // if (beanModel.getIocInitOrder() != 100)
        // beanModel.setIocInitOrder(parentModel.getIocInitOrder());

        if (parentModel.getConstructorArgs() != null) {
            for (BeanConstructorArgModel arg : parentModel.getConstructorArgs()) {
                if (beanModel.getConstructorArg(ConvertHelper.toString(arg.getIndex())) == null) {
                    beanModel.addConstructorArg(arg);
                }
            }
        }

        if (beanModel.getIocBuild() == null) {
            beanModel.setIocBuild(parentModel.getIocBuild());
        }

        if (parentModel.getProperties() != null) {
            for (BeanPropertyModel prop : parentModel.getProperties()) {
                if (beanModel.getProperty(prop.getName()) == null) {
                    beanModel.addProperty(prop);
                }
            }
        }
    }
}