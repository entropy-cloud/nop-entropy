/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.model;

import io.nop.ioc.model._gen._BeanConfigModel;

import java.util.HashSet;
import java.util.Set;

public class BeanConfigModel extends _BeanConfigModel implements IBeanModel {
    public BeanConfigModel() {

    }

    public void addName(String name) {
        Set<String> names = getName();
        if (names == null) {
            names = new HashSet<>();
            setName(names);
        }
        names.add(name);
    }
}
