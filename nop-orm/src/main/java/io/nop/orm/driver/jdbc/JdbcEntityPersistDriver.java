/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.driver.jdbc;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.collections.IntArray;
import io.nop.core.lang.sql.SQL;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.IDataRow;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.lock.LockOption;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.JdbcBatcher;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmErrors;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.eql.binder.OrmBinderHelper;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.persister.OrmAssembly;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.sql.EntitySQL;
import io.nop.orm.sql.GenSqlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;

public class JdbcEntityPersistDriver implements IEntityPersistDriver {
    static final Logger LOG = LoggerFactory.getLogger(JdbcEntityPersistDriver.class);

    private IEntityModel entityModel;
    private IPersistEnv env;
    private IJdbcTemplate jdbcTemplate;

    private EntitySQL findLatestSql;
    private EntitySQL insertSql;
    private EntitySQL deleteSql;
    private EntitySQL loadSql;
    private EntitySQL lockSql;

    private EntitySQL batchLoadSqlPart;
    private String querySpace;
    private IDialect dialect;

    private IDataParameterBinder[] binders;

    private volatile EntitySQL lastUpdateSql; //NOSONAR

    IJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    @Override
    public void init(IEntityModel entityModel, IPersistEnv env) {
        this.entityModel = entityModel;
        this.env = env;
        this.jdbcTemplate = env.jdbc();
        this.querySpace = DaoHelper.normalizeQuerySpace(entityModel.getQuerySpace());
        this.dialect = env.getDialectForQuerySpace(querySpace);
        this.binders = OrmBinderHelper.buildBinders(dialect, entityModel, env.getColumnBinderEnhancer());

        this.findLatestSql = entityModel.getNopRevEndVarPropId() > 0
                ? GenSqlHelper.genFindLatestSql(dialect, entityModel, binders, entityModel.getEagerLoadProps()) : null;

        this.insertSql = GenSqlHelper.genInsertSql(dialect, entityModel, binders);
        this.deleteSql = GenSqlHelper.genDeleteSql(dialect, entityModel, binders);
        this.loadSql = GenSqlHelper.genLoadSql(dialect, entityModel, binders, entityModel.getEagerLoadProps());
        this.lockSql = GenSqlHelper.genLockSql(dialect, entityModel, binders, entityModel.getEagerLoadProps(),
                LockOption.PESSIMISTIC_WRITE);
        this.batchLoadSqlPart = GenSqlHelper.genLoadSqlPart(dialect, entityModel, entityModel.getEagerLoadProps());
    }

    @Override
    public <T> T getExtension(Class<T> clazz) {
        return null;
    }

    @Override
    public IOrmEntity findLatest(ShardSelection shard, IOrmEntity entity, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        IntArray propIds = entityModel.getEagerLoadProps();
        EntitySQL loadSql = this.findLatestSql;
        if (this.dialect != dialect) {
            loadSql = GenSqlHelper.genFindLatestSql(dialect, entityModel, binders, propIds);
        }
        SQL sql = loadSql.useParamsFromEntity(dialect, shard, entity).end();

        return jdbc().executeQuery(sql, ds -> {
            if (ds.hasNext()) {
                IDataRow row = ds.next();
                return OrmAssembly.readEntity(row, entityModel, binders, propIds, session);
            } else {
                return null;
            }
        });
    }

    @Override
    public CompletionStage<Void> loadAsync(ShardSelection shard, IOrmEntity entity, IntArray propIds,
                                           FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        return FutureHelper.futureCall(() -> {
            IDialect dialect = getDialect(shard);
            EntitySQL loadSql = this.loadSql;
            if (dialect != this.dialect || !loadSql.propIds.equals(propIds)) {
                loadSql = GenSqlHelper.genLoadSql(dialect, entityModel, binders, propIds);
            }
            SQL sql = loadSql.useParamsFromEntity(dialect, shard, entity).end();
            return jdbc().executeQuery(sql, ds -> {
                if (ds.hasNext()) {
                    Object[] values = OrmAssembly.getPropValues(ds.next(), binders, propIds);
                    session.internalAssemble(entity, values, propIds);
                    return null;
                } else {
                    session.markMissing(entity);
                    return null;
                }
            });
        });
    }

    @Override
    public boolean lock(ShardSelection shard, IOrmEntity entity, IntArray propIds, Runnable unlockCallback,
                        IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        EntitySQL lockSql = this.lockSql;
        if (dialect != this.dialect || !lockSql.propIds.equals(propIds)) {
            lockSql = GenSqlHelper.genLockSql(dialect, entityModel, binders, propIds, LockOption.PESSIMISTIC_WRITE);
        }
        SQL sql = lockSql.useParamsFromEntity(dialect, shard, entity).end();

        // lock通过select for update 语句实现，如果不在事务环境中执行则没有意义
        if (!jdbc().txn().isTransactionOpened(sql.getQuerySpace()))
            throw new OrmException(OrmErrors.ERR_ORM_LOCK_MUST_RUN_IN_TXN)
                    .param(ARG_ENTITY_NAME, entity.orm_entityName()).param(ARG_ENTITY_ID, entity.orm_id());

        return jdbc().executeQuery(sql, ds -> {
            if (ds.hasNext()) {
                Object[] values = OrmAssembly.getPropValues(ds.next(), binders, propIds);
                session.internalAssemble(entity, values, propIds);

                // 事务关闭则取消锁定
                jdbc().txn().addTransactionListener(sql.getQuerySpace(), new ITransactionListener() {
                    @Override
                    public void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable exception) {
                        unlockCallback.run();
                    }
                });
                return true;
            } else {
                session.markMissing(entity);
                return false;
            }
        });
    }

    @Override
    public CompletionStage<Void> batchLoadAsync(ShardSelection shard, Collection<IOrmEntity> entities, IntArray propIds,
                                                FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        return FutureHelper.futureCall(() -> {
            IDialect dialect = getDialect(shard);
            EntitySQL batchLoadSqlPart = this.batchLoadSqlPart;
            if (dialect != this.dialect || !batchLoadSqlPart.propIds.equals(propIds)) {
                batchLoadSqlPart = GenSqlHelper.genLoadSqlPart(dialect, entityModel, propIds);
            }
            SQL.SqlBuilder sb = SQL.begin().append(batchLoadSqlPart.sql);
            GenSqlHelper.appendBatchLoadEq(sb, dialect, entityModel, binders, entities);
            SQL sql = sb.end();

            final Map<Object, IOrmEntity> map = OrmAssembly.toIdMap(entities);

            jdbc().executeQuery(sql, rs -> {
                while (rs.hasNext()) {
                    IDataRow row = rs.next();
                    Object[] values = OrmAssembly.getPropValues(row, binders, propIds);
                    Object id = OrmAssembly.readId(values, entityModel);
                    IOrmEntity entity = map.remove(id);
                    if (entity != null) {
                        session.internalAssemble(entity, values, propIds);
                    } else {
                        LOG.warn("orm.warn_batch_load_entity_invalid_id:{}", id);
                    }
                }
                return null;
            });

            for (IOrmEntity entity : map.values()) {
                session.markMissing(entity);
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Void> batchExecuteAsync(boolean topoAsc, String querySpace,
                                                   List<IBatchAction.EntitySaveAction> saveActions, List<IBatchAction.EntityUpdateAction> updateActions,
                                                   List<IBatchAction.EntityDeleteAction> deleteActions, IOrmSessionImplementor session) {
        return FutureHelper.futureCall(() -> {
            IDialect dialect = getDialect(querySpace);

            if (topoAsc) {
                if (saveActions != null || updateActions != null) {
                    LOG.debug("orm.driver_execute_save_update:{}", entityModel.getName());
                    SQL sql = SQL.begin().name("batchExecute").querySpace(querySpace).end();
                    jdbc().runWithConnection(sql, conn -> {

                        batchExecuteCommand(conn, saveActions, action -> buildSaveSql(dialect, action));
                        batchExecuteCommand(conn, updateActions, action -> buildUpdateSql(dialect, action));
                        return null;
                    });
                }
            } else {
                if (deleteActions != null) {
                    LOG.debug("orm.driver_execute_delete:{}", entityModel.getName());
                    SQL sql = SQL.begin().name("batchExecute_delete").querySpace(querySpace).end();
                    jdbc().runWithConnection(sql, conn -> {
                        batchExecuteCommand(conn, deleteActions, action -> buildDeleteSql(dialect, action));
                        return null;
                    });
                }
            }
            return null;
        });
    }

    SQL buildSaveSql(IDialect dialect, IBatchAction.IEntityBatchAction action) {
        EntitySQL sql = this.insertSql;
        if (this.dialect != dialect) {
            sql = GenSqlHelper.genInsertSql(dialect, entityModel, binders);
        }
        return sql.useParamsFromEntity(dialect, action.getShardSelection(), action.getEntity()).end();
    }

    SQL buildUpdateSql(IDialect dialect, IBatchAction.IEntityBatchAction action) {
        IOrmEntity entity = action.getEntity();
        IntArray propIds = entity.orm_dirtyPropIds();
        if (propIds.isEmpty())
            return null;

        EntitySQL sql = this.lastUpdateSql;
        if (sql == null || dialect != this.dialect || !sql.propIds.equals(propIds)) {
            sql = GenSqlHelper.genUpdateSql(dialect, entityModel, binders, propIds);
            this.lastUpdateSql = sql;
        }
        return sql.useParamsFromEntity(dialect, action.getShardSelection(), entity).end();
    }

    SQL buildDeleteSql(IDialect dialect, IBatchAction.IEntityBatchAction action) {
        EntitySQL sql = this.deleteSql;
        if (this.dialect != dialect) {
            sql = GenSqlHelper.genDeleteSql(dialect, entityModel, binders);
        }
        return sql.useParamsFromEntity(dialect, action.getShardSelection(), action.getEntity()).end();
    }

    void batchExecuteCommand(Connection conn, List<? extends IBatchAction.IEntityBatchAction> actions,
                             Function<IBatchAction.IEntityBatchAction, SQL> sqlCreator) {
        if (actions == null || actions.isEmpty())
            return;

        JdbcBatcher batcher = new JdbcBatcher(conn, dialect, env.getDaoMetrics());

        for (int i = 0, n = actions.size(); i < n; i++) {
            IBatchAction.IEntityBatchAction action = actions.get(i);
            SQL sql = sqlCreator.apply(action);
            if (sql == null)
                continue;

            batcher.addCommand(sql, true, action.getCallback());
        }
        batcher.flush();
    }

    private IDialect getDialect(ShardSelection shard) {
        if (shard != null) {
            String querySpace = DaoHelper.normalizeQuerySpace(shard.getQuerySpace());
            if (!Objects.equals(querySpace, this.querySpace))
                return env.getDialectForQuerySpace(querySpace);
        }
        return dialect;
    }

    private IDialect getDialect(String querySpace) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);
        if (!Objects.equals(querySpace, this.querySpace))
            return env.getDialectForQuerySpace(querySpace);
        return dialect;
    }

    @Override
    public <T extends IOrmEntity> List<T> findPageByExample(ShardSelection shard, T example,
                                                            List<OrderFieldBean> orderBy, long offset, int limit, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = GenSqlHelper.genFindByExample(dialect, entityModel, binders, example, orderBy);
        GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.end();
        return jdbc().findPage(sql, offset, limit, (rs, rowNumber, colMapper) -> {
            IOrmEntity entity = OrmAssembly.readEntity(rs, entityModel, binders, entityModel.getEagerLoadProps(),
                    session);
            return (T) entity;
        });
    }

    @Override
    public <T extends IOrmEntity> List<T> findAllByExample(ShardSelection shard, T example,
                                                           List<OrderFieldBean> orderBy, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = GenSqlHelper.genFindByExample(dialect, entityModel, binders, example, orderBy);
        GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.end();
        return jdbc().findAll(sql, (rs, rowNumber, colMapper) -> {
            IOrmEntity entity = OrmAssembly.readEntity(rs, entityModel, binders, entityModel.getEagerLoadProps(),
                    session);
            return (T) entity;
        });
    }

    @Override
    public long deleteByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = GenSqlHelper.genDeleteByExample(dialect, entityModel, binders, null, example);
        GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.end();
        return jdbc().executeUpdate(sql);
    }

    @Override
    public IOrmEntity findFirstByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = GenSqlHelper.genFindByExample(dialect, entityModel, binders, example, null);
        GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.end();
        return jdbc().findFirst(sql, (rs, rowNumber, colMapper) -> {
            IOrmEntity entity = OrmAssembly.readEntity(rs, entityModel, binders, entityModel.getEagerLoadProps(),
                    session);
            return entity;
        });
    }

    @Override
    public long countByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = GenSqlHelper.genCountByExample(dialect, entityModel, binders, example);
        GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.end();
        return jdbc().findLong(sql, 0L);
    }

    @Override
    public long updateByExample(ShardSelection shard, IOrmEntity example, IOrmEntity updated,
                                IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = GenSqlHelper.genUpdateByExample(dialect, entityModel, binders, example, updated);
        GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.end();
        return jdbc().executeUpdate(sql);
    }
}