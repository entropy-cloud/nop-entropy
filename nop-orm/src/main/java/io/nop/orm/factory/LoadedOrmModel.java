package io.nop.orm.factory;

import io.nop.commons.util.IoHelper;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmCachedQueryPlan;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.QueryPlanCacheKey;
import io.nop.orm.compile.EqlCompileContext;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.compile.EqlCompiler;
import io.nop.orm.eql.compile.ISqlCompileContext;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.SqlExprMetaCache;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.persister.ICollectionPersister;
import io.nop.orm.persister.IEntityPersister;
import io.nop.orm.persister.IPersistEnv;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadedOrmModel implements ILoadedOrmModel {
    private final IPersistEnv env;
    private final IOrmModel ormModel;
    private final Map<String, IEntityPersister> entityPersisters;
    private final Map<String, ICollectionPersister> collectionPersisters;
    private final SqlExprMetaCache sqlExprMetaCache;
    private IOrmInterceptor ormInterceptor;

    private volatile boolean needClose;
    private final AtomicInteger refCount = new AtomicInteger();

    public LoadedOrmModel(IPersistEnv env, IOrmModel ormModel) {
        this.env = env;
        PersistEnvBuilder builder = new PersistEnvBuilder(ormModel, env.getSequenceGenerator(), env);
        builder.build();
        Map<String, IEntityPersister> entityPersisters = builder.getEntityPersisters();
        Map<String, ICollectionPersister> collectionPersisters = builder.getCollectionPersisters();

        SqlExprMetaCache sqlExprMetaCache = new SqlExprMetaCache(env.getColumnBinderEnhancer(),
                env.getDialectProvider(), ormModel);

        this.ormModel = ormModel;
        this.entityPersisters = entityPersisters;
        this.collectionPersisters = collectionPersisters;
        this.sqlExprMetaCache = sqlExprMetaCache;
    }

    public void setOrmInterceptor(IOrmInterceptor ormInterceptor) {
        this.ormInterceptor = ormInterceptor;
    }

    @Override
    public IPersistEnv getEnv() {
        return env;
    }

    @Override
    public IOrmModel getOrmModel() {
        return ormModel;
    }

    @Override
    public IEntityPersister getEntityPersister(String entityName) {
        return entityPersisters.get(entityName);
    }

    @Override
    public ICollectionPersister getCollectionPersister(String collectionName) {
        return collectionPersisters.get(collectionName);
    }

    @Override
    public EntityTableMeta resolveEntityTableMeta(String entityName, boolean allowUnderscoreName) {
        return sqlExprMetaCache.getEntityTableMeta(entityName, allowUnderscoreName);
    }

    @Override
    public IOrmInterceptor getOrmInterceptor() {
        return ormInterceptor;
    }

    @Override
    public ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                                   IEqlAstTransformer astTransformer, boolean useCache,
                                   boolean allowUnderscoreName, boolean enableFilter) {
        if (astTransformer == null)
            astTransformer = env.getDefaultAstTransformer();

        if (useCache) {
            QueryPlanCacheKey key = new QueryPlanCacheKey(name, sqlText, disableLogicalDelete, allowUnderscoreName);
            IOrmCachedQueryPlan result = env.getQueryPlanCache().get(key);
            if (result == null || result.getCompiledSql() == null) {
                ISqlCompileContext ctx = new EqlCompileContext(env, this, disableLogicalDelete,
                        astTransformer, allowUnderscoreName, enableFilter);
                ICompiledSql compiledSql = new EqlCompiler().compile(name, sqlText, ctx);
                if (compiledSql.isUseTenantModel()) {
                    TenantCachedQueryPlan tenantPlan;
                    if (result instanceof TenantCachedQueryPlan) {
                        tenantPlan = (TenantCachedQueryPlan) result;
                    } else {
                        tenantPlan = new TenantCachedQueryPlan();
                        result = tenantPlan;
                    }
                    tenantPlan.addCompiledSql(compiledSql);
                } else {
                    result = new SimpleCachedQueryPlan(compiledSql);
                }
                env.getQueryPlanCache().put(key, result);
            }
            return result.getCompiledSql();
        } else {
            ISqlCompileContext ctx = new EqlCompileContext(env, this, disableLogicalDelete,
                    astTransformer, allowUnderscoreName, enableFilter);
            return new EqlCompiler().compile(name, sqlText, ctx);
        }
    }

    @Override
    public String getIdText(String entityName, String alias) {
        IEntityModel entityModel = getOrmModel().requireEntityModel(entityName);
        IEntityPropModel prop = entityModel.getIdProp();
        IDialect dialect = env.getDialectForQuerySpace(entityModel.getQuerySpace());

        if (prop.isColumnModel()) {
            return alias + "." + dialect.escapeSQLName(((IColumnModel) prop).getCode());
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0, n = entityModel.getPkColumns().size(); i < n; i++) {
                String code = entityModel.getColumns().get(i).getCode();
                sb.append(alias).append(".").append(dialect.escapeSQLName(code));
                if (i != 0)
                    sb.append(',');
            }
            return sb.toString();
        }
    }

    @Override
    public void close() {
        this.needClose = true;
        // 如果仍有引用，则延迟销毁
        if (refCount.get() <= 0)
            doClose();
    }

    public void incRef() {
        refCount.incrementAndGet();
    }

    public void decRef() {
        if (refCount.decrementAndGet() == 0) {
            if (needClose) {
                doClose();
            }
        }
    }

    private void doClose() {
        for (IEntityPersister persister : this.entityPersisters.values()) {
            IoHelper.safeCloseObject(persister);
        }

        for (ICollectionPersister persister : this.collectionPersisters.values()) {
            IoHelper.safeCloseObject(persister);
        }
    }
}