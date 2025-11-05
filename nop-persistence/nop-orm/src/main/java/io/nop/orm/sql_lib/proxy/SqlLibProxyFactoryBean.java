/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib.proxy;

import io.nop.orm.sql_lib.SqlLibManager;

import jakarta.inject.Inject;

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
