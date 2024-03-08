/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.persister;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.cache.ICache;
import io.nop.core.model.graph.TopoEntry;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.bean.IBeanConstructor;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmComponent;
import io.nop.orm.IOrmDaoListener;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.driver.ICollectionPersistDriver;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.binder.IOrmColumnBinderEnhancer;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.loader.IQueryExecutor;
import io.nop.orm.metrics.IOrmMetrics;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.sql.IEntityFilterProvider;

public interface IPersistEnv extends IOrmSessionFactory {
    IOrmMetrics getOrmMetrics();

    IDaoMetrics getDaoMetrics();

    IClassModel getEntityClassModel(IEntityModel entityModel);

    EntityTableMeta resolveEntityTableMeta(String entityName, boolean allowUnderscoreName);

    IEntityPersister requireEntityPersister(String entityName);

    ICollectionPersister requireCollectionPersister(String collectionName);

    IQueryExecutor getQueryExecutor(String querySpace);

    IEntityFilterProvider getEntityFilterProvider();

    TopoEntry<? extends IEntityModel> getEntityModelTopoEntry(String entityName);

    ICache<String, Object> getGlobalCache(String referenceName);

    IEntityPersistDriver createEntityPersistDriver(String driverName);

    ICollectionPersistDriver createCollectionPersistDriver(String driverName);

    IOrmComponent newComponent(String componentName);

    IBeanConstructor getEntityConstructor(IEntityModel entityModel);

    ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete);

    ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                            boolean allowUnderscoreName, boolean enableFilter);

    ITransactionTemplate txn();

    IJdbcTemplate jdbc();

    ISequenceGenerator getSequenceGenerator();

    IOrmColumnBinderEnhancer getColumnBinderEnhancer();

    IOrmDaoListener getDaoListener();

    long newSessionRevVer();

    IBeanProvider getBeanProvider();

    long newDeleteVersion();
}
