/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import java.util.Set;

public class BeanTypeMapping {
    private final Set<BeanDefinition> beans;
    private final BeanDefinition primaryBean;

    private final BeanDefinition otherPrimaryBean;

    public BeanTypeMapping(Set<BeanDefinition> beans, BeanDefinition primaryBean, BeanDefinition otherPrimaryBean) {
        this.beans = beans;
        this.primaryBean = primaryBean;
        this.otherPrimaryBean = otherPrimaryBean;
    }

    public BeanDefinition getOtherPrimaryBean() {
        return otherPrimaryBean;
    }

    public Set<BeanDefinition> getBeans() {
        return beans;
    }

    public BeanDefinition getPrimaryBean() {
        return primaryBean;
    }

    public boolean isEmpty() {
        return beans.isEmpty();
    }
}