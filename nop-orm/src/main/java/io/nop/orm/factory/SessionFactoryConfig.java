/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.factory;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.ClassHelper;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.dao.shard.EmptyShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.orm.eql.binder.IOrmColumnBinderEnhancer;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.loader.IQueryExecutor;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.support.DynamicOrmEntity;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.api.core.ApiConstants.CONFIG_BEAN_CONTAINER;

/**
 * 与OrmModel配置无关的全局对象
 *
 * @author canonical_entropy@163.com
 */
public class SessionFactoryConfig {

    private String name;
    private boolean useMetrics = true;
    private boolean registerGlobalCache = true;

    private IJdbcTemplate jdbcTemplate;

    private IShardSelector shardSelector = EmptyShardSelector.INSTANCE;

    private ICacheProvider globalCache;

    private IQueryExecutor defaultQueryExecutor;

    private IBeanProvider beanProvider;

    private IClassLoader entityClassLoader = ClassHelper.getSafeClassLoader();

    private List<IOrmInterceptor> interceptors = Collections.emptyList();

    private Map<String, IQueryExecutor> queryExecutors = Collections.emptyMap();

    private IDialectProvider dialectProvider;

    private IOrmModel ormModel;

    private ISequenceGenerator sequenceGenerator = UuidSequenceGenerator.INSTANCE;

    private Class<?> defaultDynamicEntityClass = DynamicOrmEntity.class;

    private Set<String> dynamicEntityNames = Collections.emptySet();

    private IOrmColumnBinderEnhancer columnBinderEnhancer;

    private IEqlAstTransformer eqlAstTransformer;

    public boolean isRegisterGlobalCache() {
        return registerGlobalCache;
    }

    public void setRegisterGlobalCache(boolean registerGlobalCache) {
        this.registerGlobalCache = registerGlobalCache;
    }

    public IEqlAstTransformer getEqlAstTransformer() {
        return eqlAstTransformer;
    }

    public void setEqlAstTransformer(IEqlAstTransformer eqlAstTransformer) {
        this.eqlAstTransformer = eqlAstTransformer;
    }

    public IOrmColumnBinderEnhancer getColumnBinderEnhancer() {
        return columnBinderEnhancer;
    }

    @Inject
    public void setColumnBinderEnhancer(IOrmColumnBinderEnhancer columnBinderEnhancer) {
        this.columnBinderEnhancer = columnBinderEnhancer;
    }

    public Class<?> getDefaultDynamicEntityClass() {
        return defaultDynamicEntityClass;
    }

    public void setDefaultDynamicEntityClass(Class<?> defaultDynamicEntityClass) {
        this.defaultDynamicEntityClass = defaultDynamicEntityClass;
    }

    public Set<String> getDynamicEntityNames() {
        return dynamicEntityNames;
    }

    public void setDynamicEntityNames(Set<String> dynamicEntityNames) {
        this.dynamicEntityNames = dynamicEntityNames;
    }

    public void setQueryExecutors(Map<String, IQueryExecutor> queryExecutors) {
        this.queryExecutors = queryExecutors;
    }

    public Map<String, IQueryExecutor> getQueryExecutors() {
        return queryExecutors;
    }

    public IQueryExecutor getQueryExecutor(String querySpace) {
        IQueryExecutor executor = queryExecutors.get(querySpace);
        if (executor != null)
            return executor;
        return getDefaultQueryExecutor();
    }

    public ISequenceGenerator getSequenceGenerator() {
        return sequenceGenerator;
    }

    public void setSequenceGenerator(ISequenceGenerator sequenceGenerator) {
        this.sequenceGenerator = sequenceGenerator;
    }

    public IOrmModel getOrmModel() {
        return ormModel;
    }

    public void setOrmModel(IOrmModel ormModel) {
        this.ormModel = ormModel;
    }

    protected String buildFullName(String prefix) {
        if (this.name == null)
            return null;
        return prefix + '.' + name;
    }

    public IDialectProvider getDialectProvider() {
        return dialectProvider;
    }

    public void setDialectProvider(IDialectProvider dialectProvider) {
        this.dialectProvider = dialectProvider;
    }

    public boolean isUseMetrics() {
        return useMetrics;
    }

    public void setUseMetrics(boolean useMetrics) {
        this.useMetrics = useMetrics;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IJdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public IShardSelector getShardSelector() {
        return shardSelector;
    }

    @Inject
    public void setShardSelector(IShardSelector shardSelector) {
        this.shardSelector = shardSelector;
    }

    public ICacheProvider getGlobalCache() {
        return globalCache;
    }

    @Inject
    @Named("nopOrmGlobalCacheProvider")
    public void setGlobalCache(ICacheProvider globalCache) {
        this.globalCache = globalCache;
    }

    public IQueryExecutor getDefaultQueryExecutor() {
        return defaultQueryExecutor;
    }

    public void setDefaultQueryExecutor(IQueryExecutor defaultQueryExecutor) {
        this.defaultQueryExecutor = defaultQueryExecutor;
    }

    public IBeanProvider getBeanProvider() {
        return beanProvider;
    }

    @InjectValue(CONFIG_BEAN_CONTAINER)
    public void setBeanProvider(IBeanProvider beanProvider) {
        this.beanProvider = beanProvider;
    }

    public IClassLoader getEntityClassLoader() {
        return entityClassLoader;
    }

    public void setEntityClassLoader(IClassLoader entityClassLoader) {
        this.entityClassLoader = entityClassLoader;
    }

    public List<IOrmInterceptor> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<IOrmInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public void addInterceptor(IOrmInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public void removeInterceptor(IOrmInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

}