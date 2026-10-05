package io.nop.orm;

import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.persister.ICollectionPersister;
import io.nop.orm.persister.IEntityPersister;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.sql.ISqlCompileTool;

import java.util.Collection;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_COLLECTION_PERSISTER;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_ENTITY_PERSISTER;

public interface ILoadedOrmModel extends AutoCloseable, ISqlCompileTool {
    IOrmModel getOrmModel();

    IPersistEnv getEnv();

    void incRef();

    void decRef();

    default boolean isAnyEntityUseTenant() {
        return getOrmModel().isAnyEntityUseTenant();
    }

    IEntityPersister getEntityPersister(String entityName);

    ICollectionPersister getCollectionPersister(String collectionName);

    EntityTableMeta resolveEntityTableMeta(String entityName, boolean allowUnderscoreName);

    default IEntityPersister requireEntityPersister(String entityName) {
        IEntityPersister persister = getEntityPersister(entityName);
        if (persister == null)
            throw new OrmException(ERR_ORM_UNKNOWN_ENTITY_PERSISTER).param(ARG_ENTITY_NAME, entityName);
        return persister;
    }

    default ICollectionPersister requireCollectionPersister(String collectionName) {
        ICollectionPersister persister = getCollectionPersister(collectionName);
        if (persister == null)
            throw new OrmException(ERR_ORM_UNKNOWN_COLLECTION_PERSISTER).param(ARG_COLLECTION_NAME, collectionName);
        return persister;
    }

    default List<IEntityModel> getEntityModelsInTopoOrder(Collection<String> entityNames) {
        return getOrmModel().getEntityModelInTopoOrder(entityNames);
    }

    default <T> T getExtension(String entityName, Class<T> extensionClass) {
        return requireEntityPersister(entityName).getExtension(extensionClass);
    }

    IOrmInterceptor getOrmInterceptor();

    void close();

    /**
     * 编译EQL语句。useCache=true时以QueryPlanCacheKey作为语句缓存键。
     * astTransformer不参与缓存键，因此缓存中只存在默认transformer（或无transformer）编译的计划：
     * astTransformer为null时使用env.getDefaultAstTransformer()；显式传入与默认实例不同的transformer时，
     * useCache按false处理（不读不写查询计划缓存），始终返回该transformer的完整编译结果。
     * 生产代码不应per-call传入transformer，统一通过SessionFactoryConfig.eqlAstTransformer配置factory级默认实例。
     */
    ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                            IEqlAstTransformer astTransformer, boolean useCache,
                            boolean allowUnderscoreName, boolean enableFilter);

    default ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete) {
        return compileSql(name, sqlText, disableLogicalDelete, null, true, false, false);
    }

    default ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                                    boolean allowUnderscoreName, boolean enableFilter) {
        return compileSql(name, sqlText, disableLogicalDelete, null, true, allowUnderscoreName, enableFilter);
    }
}