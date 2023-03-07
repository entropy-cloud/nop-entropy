/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.ioc.impl.BeanDefinition;

import java.util.Map;

import static io.nop.ioc.IocErrors.ARG_AFTER;
import static io.nop.ioc.IocErrors.ARG_BEAN;
import static io.nop.ioc.IocErrors.ARG_BEFORE;
import static io.nop.ioc.IocErrors.ERR_IOC_UNKNOWN_IOC_AFTER;
import static io.nop.ioc.IocErrors.ERR_IOC_UNKNOWN_IOC_BEFORE;

public class BeanDependsBuilder {
    public void buildAll(Map<String, BeanDefinition> beans) {
        for (BeanDefinition beanDef : beans.values()) {
            if (beanDef.getBeanModel().getIocBefore() != null) {
                for (String beforeId : beanDef.getBeanModel().getIocBefore()) {
                    BeanDefinition beforeBean = beans.get(beforeId);
                    if (beforeBean == null)
                        throw new NopException(ERR_IOC_UNKNOWN_IOC_BEFORE).source(beanDef).param(ARG_BEAN, beanDef)
                                .param(ARG_BEFORE, beforeId);
                    beforeBean.getBeanModel().addDepend(beanDef.getId());
                }
            }

            if (beanDef.getBeanModel().getIocAfter() != null) {
                for (String afterId : beanDef.getBeanModel().getIocAfter()) {
                    BeanDefinition afterBean = beans.get(afterId);
                    if (afterBean == null)
                        throw new NopException(ERR_IOC_UNKNOWN_IOC_AFTER).source(beanDef).param(ARG_BEAN, beanDef)
                                .param(ARG_AFTER, afterId);
                    beanDef.getBeanModel().addDepend(afterId);
                }
            }
        }
    }
}
