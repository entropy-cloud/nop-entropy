package io.nop.orm;

import io.nop.core.model.graph.TopoEntry;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.persister.ICollectionPersister;
import io.nop.orm.persister.IEntityPersister;
import io.nop.orm.sql.ISqlCompileTool;

import static io.nop.orm.OrmErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_COLLECTION_PERSISTER;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_ENTITY_PERSISTER;

public interface ILoadedOrmModel extends AutoCloseable, ISqlCompileTool {
    IOrmModel getOrmModel();

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

    default TopoEntry<? extends IEntityModel> getEntityModelTopoEntry(String entityName) {
        return getOrmModel().getTopoEntry(entityName);
    }

    default <T> T getExtension(String entityName, Class<T> extensionClass) {
        return requireEntityPersister(entityName).getExtension(extensionClass);
    }

    IOrmInterceptor getOrmInterceptor();

    void close();

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