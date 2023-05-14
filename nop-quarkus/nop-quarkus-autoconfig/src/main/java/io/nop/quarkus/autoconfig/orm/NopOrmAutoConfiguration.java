/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.autoconfig.orm;

import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.dao.shard.EmptyShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.orm.eql.binder.IOrmColumnBinderEnhancer;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.orm.sql_lib.SqlLibManager;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

@Dependent
public class NopOrmAutoConfiguration {

    @Produces
    public ISqlLibManager nopSqlLibManager() {
        return new SqlLibManager();
    }

    @Produces
    @Named("nopOrmGlobalCacheProvider")
    public ICacheProvider nopOrmGlobalCacheProvider(NopOrmConfig config) {
        return new LocalCacheProvider("orm-global-cache", config.getGlobalCache());
    }

    @Produces
    public IShardSelector nopShardSelector() {
        return EmptyShardSelector.INSTANCE;
    }

    @Produces
    public ISequenceGenerator nopSequenceGenerator() {
        return new UuidSequenceGenerator();
    }

    @Produces
    public IOrmColumnBinderEnhancer nopOrmColumnBinderEnhancer() {
        return new DefaultOrmColumnBinderEnhancer();
    }

    // @Produces
    // public OrmSessionFactoryBean nopOrmSessionFactoryBean(ApplicationContext context) {
    // List<IOrmInterceptor> interceptors = new ArrayList<>(context.getBeansOfType(IOrmInterceptor.class).values());
    // Collections.sort(interceptors, Comparator.comparing(IOrmInterceptor::priority));
    //
    // OrmSessionFactoryBean factory = new OrmSessionFactoryBean();
    // factory.setInterceptors(interceptors);
    // return factory;
    // }

    @Produces
    public IOrmSessionFactory nopOrmSessionFactory(OrmSessionFactoryBean sessionFactoryBean) {
        return sessionFactoryBean.getObject();
    }

    @Produces
    public IOrmTemplate nopOrmTemplate() {
        return new OrmTemplateImpl();
    }

    @Produces
    public IDaoProvider nopDaoProvider(IOrmTemplate ormTemplate) {
        return new OrmDaoProvider(ormTemplate);
    }
}