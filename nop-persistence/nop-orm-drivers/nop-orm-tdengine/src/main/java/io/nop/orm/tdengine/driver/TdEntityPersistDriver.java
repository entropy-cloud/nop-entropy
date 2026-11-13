/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.driver;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
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
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.persister.OrmAssembly;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.tdengine.TdEngineConfigs;
import io.nop.orm.tdengine.model.TdSqlHelper;
import io.nop.orm.tdengine.model.TdTableMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static io.nop.orm.tdengine.TdEngineErrors.ARG_ENTITY_NAME;
import static io.nop.orm.tdengine.TdEngineErrors.ERR_TDENGINE_UPDATE_NOT_SUPPORTED;

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

    private IDialect getDialect(String querySpace) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);
        if (!Objects.equals(querySpace, this.querySpace))
            return env.getDialectForQuerySpace(querySpace);
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
            if (updateActions != null && !updateActions.isEmpty())
                throw new NopException(ERR_TDENGINE_UPDATE_NOT_SUPPORTED)
                        .param(ARG_ENTITY_NAME, entityModel.getName());

            if (saveActions != null && !saveActions.isEmpty()) {
                // 2.5-MA-11（plan 2554 裁定 D）：按「行数 × 子表声明数 × 估算字节」三因子切分，
                // 避免整批字面量内联展开成单条巨型 SQL。各 chunk 顺序同步执行，返回 future
                // 同步完成（2553 守卫不变式：BatchActionQueueImpl 两阶段 flush 对未完成 future fail-fast，
                // 不得造真异步分块路径）。
                executeChunkedInsert(saveActions, querySpace);
            }
        } else {
            if (deleteActions != null) {
                LOG.debug("orm.driver_execute_delete:{}", entityModel.getName());
                List<IOrmEntity> entities = deleteActions.stream().map(IBatchAction.EntityDeleteAction::getEntity)
                        .collect(Collectors.toList());
                // 与 JdbcEntityPersistDriver 一致，按传入 querySpace 解析方言
                IDialect dialect = getDialect(querySpace);
                SQL sql = TdSqlHelper.genBatchDelete(dialect, entityModel, entities).querySpace(querySpace).end();
                jdbc().executeUpdate(sql);
            }
        }
        return FutureHelper.voidPromise();
    }

    /**
     * 2.5-MA-11（plan 2554 裁定 D）：批写三因子 chunk 化。切分仅发生在子表组内行边界，
     * 单个 chunk 内同一子表只出现一个 USING/TAGS 声明。字节估算为保守近似：
     * 行开销 = 固定 VALUES 括号/分隔符开销 + 数据列字面量长度 + 该子表 USING/TAGS 声明
     * 一次性摊入组首行。估算偏保守（未计入 SQL 名称/查询空间等头部）不影响正确性。
     */
    private void executeChunkedInsert(List<IBatchAction.EntitySaveAction> saveActions, String querySpace) {
        Map<String, List<IOrmEntity>> map = splitSubTables(saveActions);
        int maxRows = Math.max(1, TdEngineConfigs.CFG_TD_BATCH_MAX_ROWS.get());
        int maxBytes = Math.max(1, TdEngineConfigs.CFG_TD_BATCH_MAX_BYTES.get());

        ChunkAccumulator acc = new ChunkAccumulator();
        for (Map.Entry<String, List<IOrmEntity>> entry : map.entrySet()) {
            String subTableName = entry.getKey();
            List<IOrmEntity> group = entry.getValue();
            checkTagDivergence(subTableName, group);

            for (int i = 0, n = group.size(); i < n; i++) {
                // 同子表的 USING/TAGS 声明字节只在该组首行计入（组跨 chunk 时每 chunk 重声明，估算偏保守无碍）
                int rowBytes = estimateRowBytes(group.get(i));
                if (i == 0)
                    rowBytes += estimateDeclBytes(subTableName, group.get(i));
                acc.add(subTableName, group.get(i), rowBytes, maxRows, maxBytes);
            }
        }

        acc.flushTail();

        for (List<Map.Entry<String, List<IOrmEntity>>> chunk : acc.chunks) {
            SQL.SqlBuilder sb = SQL.begin().insertInfo();
            for (Map.Entry<String, List<IOrmEntity>> entry : chunk) {
                TdSqlHelper.genBatchInsertSubTableSql(sb, tableMeta, entry.getValue());
            }
            sb.querySpace(querySpace);
            sb.name("insert:" + tableMeta.getSuperTableName());
            jdbc().executeUpdate(sb.end());
        }
    }

    /**
     * chunk 累加器：超任一上界（行数/估算字节）即封 chunk；单行即超上界时保底独占一个
     * chunk（至少提交一行，避免死循环）。chunk 内同一子表只出现一个条目（保证 USING/TAGS
     * 声明唯一），跨子表可共存；子表组按 splitSubTables 的有序 Map 连续处理，非连续重入
     * （理论面）防御性封 chunk 兜底。
     */
    static class ChunkAccumulator {
        final List<List<Map.Entry<String, List<IOrmEntity>>>> chunks = new ArrayList<>();
        private List<Map.Entry<String, List<IOrmEntity>>> current;
        private int rows;
        private int bytes;

        void add(String subTableName, IOrmEntity entity, int rowBytes, int maxRows, int maxBytes) {
            // 非连续重入（当前 chunk 已含该子表但上一条目是其他子表）：防御性封 chunk，
            // 保证 chunk 内 USING/TAGS 声明唯一。正常分组流不会触发。
            boolean nonContiguousReentry = current != null
                    && !current.get(current.size() - 1).getKey().equals(subTableName)
                    && containsSubTable(subTableName);
            if (current != null && (nonContiguousReentry || rows + 1 > maxRows || bytes + rowBytes > maxBytes))
                flush();

            if (current == null)
                current = new ArrayList<>();

            boolean merged = false;
            for (Map.Entry<String, List<IOrmEntity>> entry : current) {
                if (entry.getKey().equals(subTableName)) {
                    entry.getValue().add(entity);
                    merged = true;
                    break;
                }
            }
            if (!merged)
                current.add(Map.entry(subTableName, new ArrayList<>(List.of(entity))));
            rows++;
            bytes += rowBytes;
        }

        private boolean containsSubTable(String subTableName) {
            if (current == null)
                return false;
            for (Map.Entry<String, List<IOrmEntity>> entry : current) {
                if (entry.getKey().equals(subTableName))
                    return true;
            }
            return false;
        }

        private void flush() {
            chunks.add(current);
            current = null;
            rows = 0;
            bytes = 0;
        }

        void flushTail() {
            if (current != null)
                flush();
        }
    }

    /**
     * 2.5-MA-11 round2（plan 2554 裁定 D 扩展面）：同子表各行 TAGS 值分歧检测。
     * 缺陷态：genBatchInsertSubTableSql 只取组首行生成 USING/TAGS 声明，后续行 tag 值
     * 被静默丢弃（TDengine 语义下后续行会被写入带首行 tag 的子表）。行为保持取首行
     * 不变（可见性要求而非行为变更），但必须 WARN 留痕。
     *
     * @return true 表示检测到分歧
     */
    boolean checkTagDivergence(String subTableName, List<IOrmEntity> group) {
        List<IColumnModel> tagCols = tableMeta.getTagCols();
        if (tagCols.isEmpty() || group.size() <= 1)
            return false;

        IOrmEntity first = group.get(0);
        boolean diverged = false;
        for (int i = 1, n = group.size(); i < n; i++) {
            IOrmEntity entity = group.get(i);
            for (IColumnModel tagCol : tagCols) {
                Object firstValue = first.orm_propValue(tagCol.getPropId());
                Object value = entity.orm_propValue(tagCol.getPropId());
                if (!Objects.equals(firstValue, value)) {
                    diverged = true;
                    LOG.warn("nop.orm.td-batch-tag-divergence:subTable={},tagCol={},firstValue={},divergentValue={}",
                            subTableName, tagCol.getName(), firstValue, value);
                }
            }
        }
        return diverged;
    }

    /**
     * 估算单行 VALUES(...) 部分的 SQL 字节数：固定括号/分隔符开销 + 各数据列字面量长度
     */
    private int estimateRowBytes(IOrmEntity entity) {
        int bytes = 8; // VALUES( ) 与列间分隔符固定开销
        for (IColumnModel col : tableMeta.getDataCols()) {
            bytes += estimateValueBytes(entity.orm_propValue(col.getPropId())) + 1;
        }
        return bytes;
    }

    /**
     * 估算子表 USING superTable(tagCols) TAGS(...) 声明部分的 SQL 字节数
     */
    private int estimateDeclBytes(String subTableName, IOrmEntity firstRow) {
        int bytes = subTableName.length() + tableMeta.getSuperTableName().length() + 32;
        for (IColumnModel tagCol : tableMeta.getTagCols()) {
            bytes += tagCol.getCode().length() + estimateValueBytes(firstRow.orm_propValue(tagCol.getPropId())) + 2;
        }
        return bytes;
    }

    private static int estimateValueBytes(Object value) {
        if (value == null)
            return 4; // "null"
        if (value instanceof Number)
            return value.toString().length();
        return value.toString().length() + 2; // 引号包裹
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
            TdSqlHelper.appendBatchLoadEq(sb, dialect, entityModel, entities);
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
        SQL.SqlBuilder sb = TdSqlHelper.genFindByExample(dialect, entityModel, example, orderBy);

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
        SQL.SqlBuilder sb = TdSqlHelper.genFindByExample(dialect, entityModel, example, orderBy);
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
        SQL.SqlBuilder sb = TdSqlHelper.genDeleteByExample(dialect, entityModel, null, example);
        //GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.querySpace(getQuerySpace(shard)).end();
        return jdbc().executeUpdate(sql);
    }

    @Override
    public IOrmEntity findFirstByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        IDialect dialect = getDialect(shard);
        SQL.SqlBuilder sb = TdSqlHelper.genFindByExample(dialect, entityModel, example, null);
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
        SQL.SqlBuilder sb = TdSqlHelper.genCountByExample(dialect, entityModel, example);
        // GenSqlHelper.transformShard(sb, dialect, shard);

        SQL sql = sb.querySpace(getQuerySpace(shard)).end();
        return jdbc().findLong(sql, 0L);
    }

    @Override
    public long updateByExample(ShardSelection shard, IOrmEntity example, IOrmEntity updated, IOrmSessionImplementor session) {
        throw new NopException(ERR_TDENGINE_UPDATE_NOT_SUPPORTED)
                .param(ARG_ENTITY_NAME, entityModel.getName());
    }
}
