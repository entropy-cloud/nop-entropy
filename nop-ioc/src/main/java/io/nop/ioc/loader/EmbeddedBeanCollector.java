/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.ioc.IocConstants;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.model.BeanValue;
import io.nop.ioc.model.IBeanPropValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmbeddedBeanCollector {
    private final List<BeanDefinition> embeddedBeans = new ArrayList<>();
    private int seq = 1;

    public void collect(Map<String, BeanDefinition> beans) {

        for (BeanDefinition bean : beans.values()) {
            if (bean.isDisabled())
                continue;

            BeanValue beanModel = bean.getBeanModel();
            beanModel.forEachChild(this::collectEmbeddedBean);
        }

        for (BeanDefinition bean : embeddedBeans) {
            beans.put(bean.getId(), bean);
        }
    }

    void collectEmbeddedBean(IBeanPropValue prop) {
        if (prop instanceof BeanValue) {
            BeanValue beanModel = (BeanValue) prop;
            String className = beanModel.getClassName();
            beanModel.setEmbeddedId(IocConstants.GEN_ID_PREFIX + (className == null ? "" : className) + "$" + (seq++));
            BeanDefinition bean = new BeanDefinition(beanModel);
            embeddedBeans.add(bean);
        }
        prop.forEachChild(this::collectEmbeddedBean);
    }

    /*
     * BeanModel buildEmbeddedBean(BeanValue value) { BeanModel bean = new BeanModel();
     * bean.setLocation(value.getLocation()); bean.setAutowireCandidate(false); bean.setAutowire(value.getAutowire());
     * bean.setId(IocConstants.GEN_ID_PREFIX + (seq++)); value.setEmbeddedId(bean.getId());
     *
     * bean.setIocBuild(value.getIocBuild()); bean.setIocInitOrder(value.getIocInitOrder());
     * bean.setIocInit(value.getIocInit()); bean.setIocBeanMethod(value.getIocBeanMethod());
     * bean.setIocRefreshConfig(value.getIocRefreshConfig()); bean.setIocOnConfigRefresh(value.getIocOnConfigRefresh());
     * bean.setIocDelayStart(value.getIocDelayStart()); bean.setIocDestroy(value.getIocDestroy());
     * bean.setIocRestart(value.getIocRestart()); bean.setIocSecurityDomain(value.getIocSecurityDomain());
     * bean.setIocDelayMethod(value.getIocDelayMethod()); bean.setProperties(value.getProperties());
     * bean.setConstructorArgs(value.getConstructorArgs());
     * bean.setIocRefreshConfigMethod(value.getIocRefreshConfigMethod());
     * bean.setFactoryMethod(value.getFactoryMethod()); bean.setInitMethod(value.getInitMethod());
     * bean.setDestroyMethod(value.getDestroyMethod()); bean.setScope(value.getScope());
     * bean.setDependsOn(value.getDependsOn()); bean.setClassName(value.getClassName());
     * bean.setLazyInit(value.getLazyInit()); bean.setParent(value.getParent()); return bean; }
     */
}
