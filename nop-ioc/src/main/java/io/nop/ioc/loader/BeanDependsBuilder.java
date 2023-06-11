/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.ioc.impl.BeanDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class BeanDependsBuilder {
    static final Logger LOG = LoggerFactory.getLogger(BeanDependsBuilder.class);

    public void buildAll(Map<String, BeanDefinition> beans) {
        for (BeanDefinition beanDef : beans.values()) {
            if (beanDef.getBeanModel().getIocBefore() != null) {
                for (String beforeId : beanDef.getBeanModel().getIocBefore()) {
                    BeanDefinition beforeBean = beans.get(beforeId);
                    if (beforeBean == null) {
                        LOG.debug("nop.ioc.unknown-before-ref:bean={},before={}", beanDef, beforeId);
                        continue;
                    }
                    beforeBean.getBeanModel().addDepend(beanDef.getId());
                }
            }

            if (beanDef.getBeanModel().getIocAfter() != null) {
                for (String afterId : beanDef.getBeanModel().getIocAfter()) {
                    BeanDefinition afterBean = beans.get(afterId);
                    if (afterBean == null) {
                        LOG.debug("nop.ioc.unknown-after-ref:bean={},after={}", beanDef, afterId);
                        continue;
                    }
                    beanDef.getBeanModel().addDepend(afterId);
                }
            }
        }
    }
}
