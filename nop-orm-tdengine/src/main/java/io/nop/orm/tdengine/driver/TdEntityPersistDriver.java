/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.tdengine.driver;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.collections.IntArray;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.utils.DaoHelper;
import io.nop.dataset.IDataRow;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.eql.binder.OrmBinderHelper;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.persister.OrmAssembly;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.tdengine.model.TdSqlHelper;
import io.nop.orm.tdengine.model.TdTableMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class TdEntityPersistDriver implements IEntityPersistDriver {
    static final Logger LOG = LoggerFactory.getLogger(TdEntityPersistDriver.class);
    private TdTableMeta tableMeta;
    private IJdbcTemplate jdbcTemplate;
    private IEntityModel entityModel;
    private IPersistEnv env;
    private String querySpace;
    private IDialect dialect;

    private IDataParameterBinder[] binders;

    @Override
    public void init(IEntityModel entityModel, IPersistEnv env) {
        this.tableMeta = new TdTableMeta(entityModel);
        this.entityModel = entityModel;
        this.jdbcTemplate = env.jdbc();
        this.env = env;
        this.querySpace = DaoHelper.normalizeQuerySpace(entityModel.getQuerySpace());
        this.dialect = env.getDialectForQuerySpace(querySpace);
        this.binders = OrmBinderHelper.buildBinders(dialect, entityModel, env.getColumnBinderEnhancer());
    }

    protected IJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    @Override
    public <T> T getExtension(Class<T> clazz) {
        return null;
    }

    @Override
    public IOrmEntity findLatest(ShardSelection selection, IOrmEntity entity, IOrmSessionImplementor session) {
        return null;
    }

    @Override
    public CompletionStage<Void> loadAsync(ShardSelection shard, IOrmEntity entity, IntArray propIds,
                                           FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        SQL.SqlBuilder sb = TdSqlHelper.genLoadSql(tableMeta, entity, propIds);
        SQL sql = sb.querySpace(getQuerySpace(shard)).end();

        return FutureHelper.futureCall(() -> {
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

    private String getQuerySpace(ShardSelection shard) {
        if (shard != null) {
            return DaoHelper.normalizeQuerySpace(shard.getQuerySpace());
        }
        return DaoHelper.normalizeQuerySpace(tableMeta.getEntityModel().getQuerySpace());
    }

    private IDialect getDialect(ShardSelection shard) {
        if (shard != null) {
            String querySpace = DaoHelper.normalizeQuerySpace(shard.getQuerySpace());
            if (!Objects.equals(querySpace, this.querySpace))
                return env.getDialectForQuerySpace(querySpace);
        }
        return dialect;
    }

    @Override
    public boolean lock(ShardSelection shard, IOrmEntity entity, IntArray propIds,
                        Runnable unlockCallback, IOrmSessionImplementor session) {
        return false;
    }

    @Override
    public CompletionStage<Void> batchExecuteAsync(boolean topoAsc, String querySpace,
                                                   List<IBatchAction.EntitySaveAction> saveActions,
                                                   List<IBatchAction.EntityUpdateAction> updateActions,
                                                   List<IBatchAction.EntityDeleteAction> deleteActions,
                                                   IOrmSessionImplementor session) {
        if (topoAsc) {
            if (saveActions != null) {
                Map<String, List<IOrmEntity>> map = splitSubTables(saveActions);
                SQL.SqlBuilder sb = SQL.begin().insertInfo();
                for (List<IOrmEntity> list : map.values()) {
                    TdSqlHelper.genBatchInsertSubTableSql(sb, tableMeta, list);
                }
                sb.querySpace(querySpace);
                sb.name("insert:" + tableMeta.getSuperTableName());
                jdbc().executeUpdate(sb.end());
            }
        } else {
            if (deleteActions != null) {
                LOG.debug("orm.driver_execute_delete:{}", entityModel.getName());
                List<IOrmEntity> entities = deleteActions.stream().map(IBatchAction.EntityDeleteAction::getEntity)
                        .collect(Collectors.toList());
                SQL sql = TdSqlHelper.genBatchDelete(dialect, entityModel, binders, entities).querySpace(querySpace).end();
                jdbc().executeUpdate(sql);
            }
        }
        return null;
    }

    private Map<String, List<IOrmEntity>> splitSubTables(List<? extends IBatchAction.EntityBatchAction> actions) {
        Map<String, List<IOrmEntity>> map = new TreeMap<>();
        for (IBatchAction.EntityBatchAction action : actions) {
            IOrmEntity entity = action.getEntity();
            String subTableName = tableMeta.getSubTableName(entity);
            map.computeIfAbsent(subTableName, k -> new ArrayList<>()).add(entity);
        }
        return map;
    }

    @Override
    public CompletionStage<Void> batchLoadAsync(ShardSelection shard, Collection<IOrmEntity> entities,
                                                IntArray propIds, FieldSelectionBean subSelection, IOrmSessionImplementor session) {

        IEntityModel entityModel = tableMeta.getEntityModel();

        return FutureHelper.futureCall(() -> {
            IDialect dialect = getDialect(shard);
            SQL.SqlBuilder sb = TdSqlHelper.genLoadSqlPart(dialect, entityModel, propIds);
            TdSqlHelper.appendBatchLoadEq(sb, dialect, entityModel, binders, entities);
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
    public <T extends IOrmEntity> List<T> findPageByExample(ShardSelection shard, T example,
                                                            List<OrderFieldBean> orderBy, long offset, int limit,
                                                            IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = TdSqlHelper.genFindByExample(dialect, entityModel, binders, example, orderBy);

        SQL sql = sb.querySpace(getQuerySpace(shard)).end();
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
        SQL.SqlBuilder sb = TdSqlHelper.genFindByExample(dialect, entityModel, binders, example, orderBy);
        // GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.querySpace(getQuerySpace(shard)).end();
        return jdbc().findAll(sql, (rs, rowNumber, colMapper) -> {
            IOrmEntity entity = OrmAssembly.readEntity(rs, entityModel, binders, entityModel.getEagerLoadProps(),
                    session);
            return (T) entity;
        });
    }

    @Override
    public long deleteByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = TdSqlHelper.genDeleteByExample(dialect, entityModel, binders, null, example);
        //GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.end();
        return jdbc().executeUpdate(sql);
    }

    @Override
    public IOrmEntity findFirstByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = TdSqlHelper.genFindByExample(dialect, entityModel, binders, example, null);
        //GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.querySpace(getQuerySpace(shard)).end();
        return jdbc().findFirst(sql, (rs, rowNumber, colMapper) -> {
            IOrmEntity entity = OrmAssembly.readEntity(rs, entityModel, binders, entityModel.getEagerLoadProps(),
                    session);
            return entity;
        });
    }

    @Override
    public long countByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = TdSqlHelper.genCountByExample(dialect, entityModel, binders, example);
        // GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.end();
        return jdbc().findLong(sql, 0L);
    }

    @Override
    public long updateByExample(ShardSelection shard, IOrmEntity example, IOrmEntity updated, IOrmSessionImplementor session) {
        return 0;
    }
}
