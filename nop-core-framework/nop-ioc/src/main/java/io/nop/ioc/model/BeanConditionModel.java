/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.model;

import io.nop.ioc.model._gen._BeanConditionModel;

import java.util.HashSet;
import java.util.Set;

public class BeanConditionModel extends _BeanConditionModel {
    private boolean disabled;

    public BeanConditionModel() {

    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public void addMissingBean(String beanId) {
        Set<String> ids = getMissingBean();
        if (ids == null) {
            ids = new HashSet<>();
            setMissingBean(ids);
        }
        ids.add(beanId);
    }
}
