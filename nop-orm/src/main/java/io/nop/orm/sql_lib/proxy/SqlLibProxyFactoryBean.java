/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib.proxy;

import io.nop.orm.sql_lib.SqlLibManager;

import javax.inject.Inject;

public class SqlLibProxyFactoryBean {
    private Class<?> mapperClass;
    private SqlLibManager sqlLibManager;

    public Class<?> getMapperClass() {
        return mapperClass;
    }

    public void setMapperClass(Class<?> mapperClass) {
        this.mapperClass = mapperClass;
    }

    @Inject
    public void setSqlLibManager(SqlLibManager sqlLibManager) {
        this.sqlLibManager = sqlLibManager;
    }

    public Object build() {
        return sqlLibManager.createProxy(mapperClass);
    }
}
