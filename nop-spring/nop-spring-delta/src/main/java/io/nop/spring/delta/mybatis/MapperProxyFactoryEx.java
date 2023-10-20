/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.delta.mybatis;

import org.apache.ibatis.binding.MapperProxy;
import org.apache.ibatis.binding.MapperProxyFactory;

import java.lang.reflect.Proxy;

public class MapperProxyFactoryEx<T> extends MapperProxyFactory<T> {
    private final Class<?> mapperTypeEx;

    public MapperProxyFactoryEx(Class<T> mapperInterface, Class<?> mapperTypeEx) {
        super(mapperInterface);
        this.mapperTypeEx = mapperTypeEx;
    }

    @Override
    protected T newInstance(MapperProxy<T> mapperProxy) {
        Class<?> inf = mapperTypeEx;
        if (inf == null)
            inf = getMapperInterface();
        return (T) Proxy.newProxyInstance(getMapperInterface().getClassLoader(), new Class[]{inf}, mapperProxy);
    }
}
