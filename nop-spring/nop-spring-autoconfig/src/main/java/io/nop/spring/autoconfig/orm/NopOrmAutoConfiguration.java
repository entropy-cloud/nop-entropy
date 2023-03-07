/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.autoconfig.orm;

import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.dao.shard.EmptyShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.orm.IOrmColumnBinderEnhancer;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.orm.sql_lib.SqlLibManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NopOrmConfig.class)
@ConditionalOnClass(OrmSessionFactoryBean.class)
public class NopOrmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ISqlLibManager.class)
    public ISqlLibManager nopSqlLibManager() {
        return new SqlLibManager();
    }

    @Bean("nopOrmGlobalCacheProvider")
    public ICacheProvider nopOrmGlobalCacheProvider(NopOrmConfig config) {
        return new LocalCacheProvider("orm-global-cache", config.getGlobalCache());
    }

    @Bean
    @ConditionalOnMissingBean(IShardSelector.class)
    public IShardSelector nopShardSelector() {
        return EmptyShardSelector.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(ISequenceGenerator.class)
    public ISequenceGenerator nopSequenceGenerator() {
        return new UuidSequenceGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(IOrmColumnBinderEnhancer.class)
    public IOrmColumnBinderEnhancer nopOrmColumnBinderEnhancer() {
        return new DefaultOrmColumnBinderEnhancer();
    }

    @Bean
    @ConditionalOnMissingBean(OrmSessionFactoryBean.class)
    public OrmSessionFactoryBean nopOrmSessionFactoryBean(ApplicationContext context) {
        List<IOrmInterceptor> interceptors = new ArrayList<>(context.getBeansOfType(IOrmInterceptor.class).values());
        Collections.sort(interceptors, Comparator.comparing(IOrmInterceptor::order));

        OrmSessionFactoryBean factory = new OrmSessionFactoryBean();
        factory.setInterceptors(interceptors);
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean(IOrmSessionFactory.class)
    public IOrmSessionFactory nopOrmSessionFactory(OrmSessionFactoryBean sessionFactoryBean) {
        return sessionFactoryBean.getObject();
    }

    @Bean
    @ConditionalOnMissingBean(IOrmTemplate.class)
    public IOrmTemplate nopOrmTemplate() {
        return new OrmTemplateImpl();
    }

    @Bean
    @ConditionalOnMissingBean(IDaoProvider.class)
    public IDaoProvider nopDaoProvider(IOrmTemplate ormTemplate) {
        return new OrmDaoProvider(ormTemplate);
    }
}