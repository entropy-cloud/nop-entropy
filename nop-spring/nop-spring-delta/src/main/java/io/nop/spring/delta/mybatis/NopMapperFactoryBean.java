/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.delta.mybatis;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

public class NopMapperFactoryBean implements FactoryBean {
    private SqlSessionTemplate sqlSessionTemplate;
    private Class<?> mapperInterface;
    private Class<?> mapperTypeEx;

    public Class<?> getMapperInterface() {
        return mapperInterface;
    }

    public void setMapperInterface(Class<?> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    public Class<?> getMapperTypeEx() {
        return mapperTypeEx;
    }

    public void setMapperTypeEx(Class<?> mapperTypeEx) {
        this.mapperTypeEx = mapperTypeEx;
    }

    public SqlSessionTemplate getSqlSessionTemplate() {
        return sqlSessionTemplate;
    }

    @Autowired
    public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    @Override
    public Object getObject() throws Exception {
        return new MapperProxyFactoryEx<>(mapperInterface, mapperTypeEx).newInstance(sqlSessionTemplate);
    }

    @Override
    public Class<?> getObjectType() {
        if (mapperTypeEx != null)
            return mapperTypeEx;
        return mapperInterface;
    }
}
