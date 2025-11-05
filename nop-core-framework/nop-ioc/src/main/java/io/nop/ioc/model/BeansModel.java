/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.model;

import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.CollectionHelper;
import io.nop.ioc.model._gen._BeansModel;

import java.util.Set;

public class BeansModel extends _BeansModel implements INeedInit {
    public BeansModel() {

    }

    @Override
    public void init() {
        boolean defaultLazyInit = isDefaultLazyInit();
        Set<String> secDomain = getIocSecurityDomain();

        for (BeanModel bean : getBeans()) {
            if (bean.getLazyInit() == null) {
                bean.setLazyInit(defaultLazyInit);
            }
            if (CollectionHelper.isEmpty(bean.getIocSecurityDomain())) {
                bean.setIocSecurityDomain(secDomain);
            }
        }

        for (BeanListModel bean : getUtilLists()) {
            if (bean.getLazyInit() == null) {
                bean.setLazyInit(defaultLazyInit);
            }
        }

        for (BeanSetModel bean : getUtilSets()) {
            if (bean.getLazyInit() == null) {
                bean.setLazyInit(defaultLazyInit);
            }
        }

        for (BeanMapModel bean : getUtilMaps()) {
            if (bean.getLazyInit() == null) {
                bean.setLazyInit(defaultLazyInit);
            }
        }
    }
}
