/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.driver.jdbc;

import io.nop.api.core.beans.FieldSelectionBean;
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
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.driver.ICollectionPersistDriver;
import io.nop.orm.eql.binder.OrmBinderHelper;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.persister.OrmAssembly;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.sql.CollectionSQL;
import io.nop.orm.sql.GenSqlHelper;
import io.nop.orm.support.OrmEntityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class JdbcCollectionPersistDriver implements ICollectionPersistDriver {
    static final Logger LOG = LoggerFactory.getLogger(JdbcEntityPersistDriver.class);

    private IEntityRelationModel collectionModel;
    private IPersistEnv env;
    private IJdbcTemplate jdbcTemplate;

    private IEntityModel refEntityModel;

    private CollectionSQL loadSql;
    private CollectionSQL batchLoadSqlPart;
    private String querySpace;
    private IDialect dialect;
    private IDataParameterBinder[] binders;

    @Override
    public void init(IEntityRelationModel relation, IPersistEnv env) {
        this.collectionModel = relation;
        this.env = env;
        this.jdbcTemplate = env.jdbc();
        this.refEntityModel = relation.getRefEntityModel();
        this.querySpace = DaoHelper.normalizeQuerySpace(refEntityModel.getQuerySpace());
        this.dialect = env.getDialectForQuerySpace(querySpace);
        this.binders = OrmBinderHelper.buildBinders(dialect, refEntityModel, env.getColumnBinderEnhancer());
        this.loadSql = GenSqlHelper.genCollectionLoadSql(dialect, relation, binders,
                refEntityModel.getEagerLoadProps());
        this.batchLoadSqlPart = GenSqlHelper.genCollectionBatchLoadSqlPart(dialect, relation,
                refEntityModel.getEagerLoadProps(), binders);
    }

    IJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    @Override
    public void loadCollection(ShardSelection shard, IOrmEntitySet coll, IntArray propIds, FieldSelectionBean selection,
                               IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        CollectionSQL loadSql = this.loadSql;

        if (this.dialect != dialect || !loadSql.propIds.equals(propIds)) {
            loadSql = GenSqlHelper.genCollectionLoadSql(dialect, collectionModel, binders, propIds);
        }

        IntArray loadPropIds = loadSql.propIds;
        SQL sql = loadSql.useParamsFromOwner(dialect, shard, coll.orm_owner()).end();

        jdbc().executeQuery(sql, rs -> {
            coll.orm_beginLoad();
            while (rs.hasNext()) {
                IDataRow row = rs.next();
                Object[] values = OrmAssembly.getPropValues(row, binders, loadPropIds);
                Object id = OrmAssembly.readId(values, refEntityModel);
                IOrmEntity refEntity = session.internalLoad(refEntityModel.getName(), id);
                session.internalAssemble(refEntity, values, loadPropIds);
                coll.orm_internalAdd(refEntity);
            }
            coll.orm_endLoad();
            return null;
        });
    }

    @Override
    public CompletionStage<Void> batchLoadCollectionAsync(ShardSelection shard, Collection<IOrmEntitySet> collections,
                                                          IntArray propIds, FieldSelectionBean selection, IOrmSessionImplementor session) {
        return FutureHelper.futureCall(() -> {
            IDialect dialect = getDialect(shard);
            CollectionSQL loadSql = this.batchLoadSqlPart;

            if (this.dialect != dialect || !loadSql.propIds.equals(propIds)) {
                loadSql = GenSqlHelper.genCollectionBatchLoadSqlPart(dialect, collectionModel, propIds, binders);
            }

            IntArray loadPropIds = loadSql.propIds;
            SQL.SqlBuilder sb = loadSql.useParamsFromOwner(dialect, shard, null);
            GenSqlHelper.appendBatchCollectionIn(sb, dialect, collectionModel, binders, collections);
            GenSqlHelper.genOrderBy(sb, dialect, refEntityModel, null, collectionModel.getSort());
            sb.querySpace(querySpace);
            SQL sql = sb.end();

            Map<Object, IOrmEntitySet> map = OrmAssembly.toOwnerKeyMap(collectionModel, collections);

            for (IOrmEntitySet coll : collections) {
                coll.orm_beginLoad();
            }

            jdbc().executeQuery(sql, rs -> {
                while (rs.hasNext()) {
                    IDataRow row = rs.next();
                    Object[] values = OrmAssembly.getPropValues(row, binders, loadPropIds);
                    Object id = OrmAssembly.readId(values, refEntityModel);
                    IOrmEntity refEntity = session.internalLoad(refEntityModel.getName(), id);
                    session.internalAssemble(refEntity, values, loadPropIds);
                    Object ownerId = OrmEntityHelper.getOwnerKey(collectionModel, refEntity);
                    IOrmEntitySet coll = map.get(ownerId);
                    if (coll == null) {
                        LOG.warn("orm.err_batch_load_collection_missing:ownerId={}", ownerId);
                        continue;
                    }
                    coll.orm_internalAdd(refEntity);
                }
                return null;
            });

            for (IOrmEntitySet coll : collections) {
                coll.orm_endLoad();
            }

            return null;
        });
    }

    @Override
    public void flushCollectionChange(ShardSelection shard, IOrmEntitySet collection, IOrmSessionImplementor session) {

    }

    private IDialect getDialect(ShardSelection shard) {
        if (shard != null) {
            String querySpace = DaoHelper.normalizeQuerySpace(shard.getQuerySpace());
            if (!Objects.equals(querySpace, this.querySpace))
                return env.getDialectForQuerySpace(querySpace);
        }
        return dialect;
    }

}
