/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SyntaxMarker;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.MissingShardPolicy;
import io.nop.dao.shard.ShardSelection;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.impl.BaseDataSet;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.SqlOrderByOrdinal;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.sql.GenSqlTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static io.nop.orm.OrmErrors.ARG_CONSTRUCT;
import static io.nop.orm.OrmErrors.ARG_COUNT;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_LIMIT;
import static io.nop.orm.OrmErrors.ARG_ORDER_KEY;
import static io.nop.orm.OrmErrors.ARG_POLICY;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_REASON;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_CONDITION_MISSING;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_FANOUT_NOT_SUPPORT;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED;

/**
 * 分片执行引擎（plan 2321，执行引擎设计 §3.2/§3.3/§3.4）：missing-shard-policy=fanout 的
 * useShard 实体在缺分片值时，SELECT 查询按分片全集串行执行 N 份物理 SQL 并归并。
 *
 * <p>接管点在执行层且必须在 transform 之前（泄漏进 transform 的 FANOUT 策略由
 * GenSqlTransformer 兜底分支响亮失败，Phase 2 纵深防御）。每分片经 {@link ForcedShardSelector}
 * 骑既有 default-shard 分支产出物理 SQL（transform 路由链零改动）；v1 串行（事务注册表是
 * ThreadLocal 域，并行分片拿不到调用方事务，设计 §3.2）；连接经既有事务组/读写分离路径
 * 获取，引擎不碰连接池；N 份物理 SQL 逐份显式 readOnly(true)。</p>
 *
 * <p>归并分级见 {@link FanoutRowMerger}；不可归并构造与资格越界一律
 * ERR_ORM_SHARD_FANOUT_NOT_SUPPORT 显式失败；归并行数受
 * {@code nop.orm.shard.fanout-max-rows} 逐分片增量计数兜底（达限立即中断、无部分结果、
 * 异常传播）；全局开关 {@code nop.orm.shard.fanout-enabled} 关闭时运行期退化为 fail。</p>
 */
public class ShardFanoutEngine {
    private static final Logger LOG = LoggerFactory.getLogger(ShardFanoutEngine.class);

    /**
     * 引擎级汇总日志（执行引擎设计 §3.8 可观测性）：逻辑 SQL 名、分片数、归并行数、
     * 归并耗时、是否达限中断。
     */
    private static final Logger FANOUT_LOG = LoggerFactory.getLogger("nop.orm.shard.fanout");

    private final JdbcQueryExecutor host;

    public ShardFanoutEngine(JdbcQueryExecutor host) {
        this.host = host;
    }

    /**
     * fanout 资格判定（接管点；判定依据=编译期构造标志 + TABLE marker 实体名 + 绑定策略裁决）。
     * 三执行入口共用：
     * <ul>
     * <li>SQL 不含缺分片值的 fanout 绑定实体 → 返回 null（非 fanout 路径，行为零变更，
     * 含"绑定表同分片值单分片 JOIN 保持原生"——有分片值即走既有值路由，引擎零参与）；</li>
     * <li>命中 fanout 但全局开关关闭 → ERR_ORM_SHARD_CONDITION_MISSING（携带 policy/原因
     * arg，运行期退化为 fail，不静默降级单分片）；</li>
     * <li>命中 fanout 但资格越界（非 SELECT / 多 fanout 实体 / 与带分片值实体混用 /
     * 不可归并构造 / 排序键未投影 / 分片全集为空）→ ERR_ORM_SHARD_FANOUT_NOT_SUPPORT。</li>
     * </ul>
     */
    public FanoutQueryPlan planQuery(ICompiledSql compiled, List<Object> params) {
        Detection detection = detectFanout(compiled, params);
        if (detection == null)
            return null;

        // 开关门控：fanout-enabled=false 时 FANOUT 策略运行期退化为 fail（设计 §3.8）
        if (!ConfigShardSelector.isFanoutEnabled()) {
            throw fanoutDisabled(detection.fanoutEntities.iterator().next());
        }

        // 仅 SELECT：跨分片非原子 DML 不进 v1 引擎（设计 §3.3，评审 M1）
        if (compiled.getStatementKind() != SqlStatementKind.SELECT) {
            throw notSupport(detection.fanoutEntities.iterator().next())
                    .param(ARG_CONSTRUCT, String.valueOf(compiled.getStatementKind()))
                    .param(ARG_REASON, "only SELECT is supported by the fanout execution engine in v1;"
                            + " cross-shard DML must go through per-shard batch jobs");
        }

        // 单 fanout 实体：fanout 实体数 >1（隐式跨片 JOIN 意图）显式失败（设计 §3.2 评审 B2）
        if (detection.fanoutEntities.size() > 1) {
            throw notSupport(detection.fanoutEntities.iterator().next())
                    .param(ARG_REASON, "at most one fanout entity per SQL is allowed, got: "
                            + detection.fanoutEntities);
        }

        // 不与带分片值的 useShard 实体混用（混合形态两种部署行为不一致，统一拒绝）
        if (!detection.routedEntities.isEmpty()) {
            throw notSupport(detection.fanoutEntities.iterator().next())
                    .param(ARG_REASON, "fanout entity must not be mixed with shard-valued useShard entities: "
                            + detection.routedEntities);
        }

        String entityName = detection.fanoutEntities.iterator().next();

        // 不可归并构造：编译期结构标志判定（设计 §3.4；SQL 文本正则不可靠，此即编译期
        // 结构信息存在的理由）
        String construct = rejectableConstruct(compiled);
        if (construct != null) {
            throw notSupport(entityName)
                    .param(ARG_CONSTRUCT, construct)
                    .param(ARG_REASON, "construct cannot be merged across shards in v1;"
                            + " use shard-key routed single-shard query instead");
        }

        // 排序键未投影：归并器无从取值，显式失败（设计 §3.1 边界；出路=把排序键加入投影）
        int unmapped = firstUnprojectedOrderOrdinal(compiled);
        if (unmapped >= 0) {
            throw notSupport(entityName)
                    .param(ARG_ORDER_KEY, "orderBy#" + unmapped)
                    .param(ARG_REASON, "order-by key is not projected; merge sort cannot take values"
                            + " from merged rows, add the sort key to the projection");
        }

        // 分片全集：接口 default 返回空列表语义 =「该选择器不提供分片全集」→ 显式失败，
        // 禁止静默空结果（设计 §3.8 m1）
        List<ShardSelection> shards = host.env().getShardSelector().selectAllShards(entityName);
        if (shards == null || shards.isEmpty()) {
            throw notSupport(entityName)
                    .param(ARG_REASON, "shard selector does not provide a shard set for entity '" + entityName
                            + "' (IShardSelector.selectAllShards is empty; use the config-track"
                            + " ConfigShardSelector, or override selectAllShards on a custom selector)");
        }

        LOG.debug("nop.orm.shard.fanout.plan:entity={},shards={}", entityName, shards.size());
        return new FanoutQueryPlan(entityName, shards);
    }

    /**
     * 非查询入口（executeUpdateSql/executeStatementSql）的 fanout 接管兜底：v1 引擎仅在
     * 查询入口执行归并，fanout 形态经其他入口一律响亮失败（判定本身在 planQuery 内完成：
     * 非 SELECT 已显式失败，SELECT 返回计划则说明经错误入口进入）。
     */
    public void assertNotFanout(ICompiledSql compiled, List<Object> params) {
        FanoutQueryPlan plan = planQuery(compiled, params);
        if (plan != null) {
            throw new OrmException(ERR_ORM_SHARD_FANOUT_NOT_SUPPORT)
                    .param(ARG_ENTITY_NAME, plan.getEntityName())
                    .param(ARG_REASON, "fanout query reached a non-query execution entry;"
                            + " the fanout engine only takes over the query entry in v1");
        }
    }

    private Detection detectFanout(ICompiledSql compiled, List<Object> params) {
        Set<String> fanoutEntities = new LinkedHashSet<>();
        Set<String> routedEntities = new LinkedHashSet<>();

        compiled.getSql().getMarkers().forEach(marker -> {
            if (!(marker instanceof SyntaxMarker))
                return;
            SyntaxMarker syntaxMarker = (SyntaxMarker) marker;
            if (syntaxMarker.getType() != SyntaxMarker.SyntaxMarkerType.TABLE)
                return;
            if (syntaxMarker.getEntityName() == null)
                return;

            IEntityModel entityModel = host.env().getOrmModel().requireEntityModel(syntaxMarker.getEntityName());
            if (!entityModel.isUseShard() || entityModel.getShardColumn() == null)
                return;

            Object shardValue = resolveShardValue(syntaxMarker, params);
            if (shardValue != null) {
                // 带分片值的 useShard 实体：与 fanout 混用将被拒绝（跨分片配对静默缺行风险）
                routedEntities.add(entityModel.getName());
            } else if (host.env().getShardSelector().getMissingShardPolicy(entityModel.getName())
                    == MissingShardPolicy.FANOUT) {
                fanoutEntities.add(entityModel.getName());
            }
            // policy==FAIL/NONE 的缺值实体：维持既有路径（transform 内 FAIL 抛错 / NONE fall-through）
        });

        if (fanoutEntities.isEmpty())
            return null;
        return new Detection(fanoutEntities, routedEntities);
    }

    private static class Detection {
        final Set<String> fanoutEntities;
        final Set<String> routedEntities;

        Detection(Set<String> fanoutEntities, Set<String> routedEntities) {
            this.fanoutEntities = fanoutEntities;
            this.routedEntities = routedEntities;
        }
    }

    private io.nop.api.core.exceptions.NopException fanoutDisabled(String entityName) {
        IEntityModel entityModel = host.env().getOrmModel().requireEntityModel(entityName);
        String shardProp = entityModel.getShardColumn() == null ? null : entityModel.getShardColumn().getName();
        return new OrmException(ERR_ORM_SHARD_CONDITION_MISSING)
                .param(ARG_ENTITY_NAME, entityName)
                .param(ARG_PROP_NAME, shardProp)
                .param(ARG_POLICY, MissingShardPolicy.FANOUT.name().toLowerCase())
                .param(ARG_REASON, "，全局开关nop.orm.shard.fanout-enabled=false，fanout策略运行期退化为fail");
    }

    private static io.nop.api.core.exceptions.NopException notSupport(String entityName) {
        return new OrmException(ERR_ORM_SHARD_FANOUT_NOT_SUPPORT)
                .param(ARG_ENTITY_NAME, entityName);
    }

    /**
     * 不可归并构造判定（设计 §3.4 显式失败集）：HAVING/GROUP BY/DISTINCT/集合操作/FOR UPDATE/
     * 非 count 聚合。HAVING 先于 GROUP BY 检查（HAVING 语句必伴随 GROUP BY，报告最具体构造）。
     * 裸 count（isBareCount）是唯一放行的聚合形态（归并=各分片求和）。
     */
    private static String rejectableConstruct(ICompiledSql compiled) {
        if (compiled.hasHaving())
            return "HAVING";
        if (compiled.hasGroupBy())
            return "GROUP BY";
        if (compiled.hasDistinct())
            return "DISTINCT";
        if (compiled.hasSetOp())
            return "UNION";
        if (compiled.hasForUpdate())
            return "FOR UPDATE";
        if (compiled.hasAggregate() && !compiled.isBareCount())
            return "AGGREGATE";
        return null;
    }

    private static int firstUnprojectedOrderOrdinal(ICompiledSql compiled) {
        List<SqlOrderByOrdinal> ordinals = compiled.getOrderByOrdinals();
        for (int i = 0, n = ordinals.size(); i < n; i++) {
            if (!ordinals.get(i).isProjected())
                return i;
        }
        return -1;
    }

    /**
     * 执行 fanout 查询：串行逐分片 transform（强制 selector）→ 逐份 readOnly → 拉回 →
     * 逐分片增量计数（达限立即中断、无部分结果、异常传播）→ 归并（§3.4 分级）→ 回调输出。
     */
    public <T> T executeQuery(IOrmSessionImplementor session, ICompiledSql compiled, FanoutQueryPlan plan,
                              List<Object> params, LongRangeBean range, Function<? super IDataSet, T> callback) {
        int maxRows = ConfigShardSelector.getFanoutMaxRows();
        List<ShardSelection> shards = plan.getShards();
        long startMillis = System.currentTimeMillis();

        List<List<IDataRow>> shardRows = new ArrayList<>(shards.size());
        int totalRows = 0;

        // v1 串行：调用线程逐分片执行，不引入并行（设计 §3.2 三个依据，不预埋并行化）
        for (ShardSelection shard : shards) {
            SQL physicalSql = transformForShard(session, compiled, params, shard);

            // 浅分页：每分片下推 offset+limit 前缀（全局第 k 名必在每分片前 offset+limit 名内，
            // 正确性成立）；裸 count 不下推分页
            LongRangeBean shardRange = compiled.isBareCount() ? null : FanoutRowMerger.perShardRange(range);

            List<IDataRow> rows = host.jdbc().executeQuery(physicalSql, shardRange, ds -> {
                // 行组装走既有 transformRow 链路（fieldMetas 序数取值通道），归并排序键
                // 按编译期 orderBy→投影序数映射从组装行 values 取值
                List<IDataRow> list = new ArrayList<>();
                ds.forEach(row -> list.add(host.transformRow(row, compiled, session)));
                return list;
            });

            // 逐分片增量计数：每拉回一个分片立即累计，达限立即中断（不再向剩余分片发起
            // 物理 SQL）、已拉回数据不返回（无部分结果）、异常向调用方传播（设计 §3.2 评审 M4）
            totalRows += rows.size();
            if (totalRows > maxRows) {
                logSummary(compiled, shards.size(), totalRows, System.currentTimeMillis() - startMillis, true);
                throw new OrmException(ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED)
                        .param(ARG_COUNT, totalRows)
                        .param(ARG_LIMIT, maxRows);
            }
            shardRows.add(rows);
        }

        long mergeStart = System.currentTimeMillis();
        IDataSet merged = merge(compiled, shardRows, range);
        long cost = System.currentTimeMillis() - startMillis;
        if (LOG.isTraceEnabled())
            LOG.trace("nop.orm.shard.fanout.merge:costMillis={}", System.currentTimeMillis() - mergeStart);

        logSummary(compiled, shards.size(), totalRows, cost, false);
        return callback.apply(merged);
    }

    /**
     * 每分片强制 selector 装饰 + transform：getMissingShardPolicy→DEFAULT_SHARD、
     * selectShardWhenMissing→该分片强制落点，骑 GenSqlTransformer 既有 default-shard
     * 分支（addShardSelection + setQuerySpace），transform 路由链零改动（设计 §3.2 评审 M5）。
     * 物理 SQL 逐份显式 readOnly(true)（与 executeQuerySql 既有 SELECT 规则一致，
     * 不存在"标记自然传播到多份 SQL"的机制）。
     */
    private SQL transformForShard(IOrmSessionImplementor session, ICompiledSql compiled, List<Object> params,
                                  ShardSelection shard) {
        GenSqlTransformer transformer = new GenSqlTransformer(
                new ForcedShardSelector(host.env().getShardSelector(), shard),
                host.env().getOrmModel(), host.env(), host.env().getLoadedOrmModel(),
                host.env().getEntityFilterProvider());
        SQL sql = transformer.transform(compiled.getSql(), params).end();

        if (compiled.getStatementKind() == SqlStatementKind.SELECT) {
            sql = SQL.begin(sql).readOnly(true).end();
        }
        return sql;
    }

    /**
     * 归并分级（设计 §3.4）：裸 count 求和 → ORDER BY 内存排序（排序键经编译期映射取值）
     * → 无序拼接；浅分页在归并后截断 [offset, offset+limit)。
     */
    private IDataSet merge(ICompiledSql compiled, List<List<IDataRow>> shardRows, LongRangeBean range) {
        IDataSetMeta meta = compiled.getDataSetMeta();
        if (compiled.isBareCount()) {
            // 裸 count 求和：单行结果（总数=各分片 count 求和）
            IDataRow countRow = FanoutRowMerger.mergeBareCount(shardRows, meta);
            return new BaseDataSet(new ArrayList<>(Collections.singletonList(countRow)), meta);
        }

        List<IDataRow> merged;
        if (compiled.getOrderByOrdinals().isEmpty()) {
            merged = FanoutRowMerger.mergeConcat(shardRows);
        } else {
            // 排序键未投影已在资格判定显式失败，此处映射必然完整
            merged = FanoutRowMerger.mergeSorted(shardRows, compiled.getOrderByOrdinals());
        }

        if (range != null)
            merged = FanoutRowMerger.truncateToRange(merged, range);

        return new BaseDataSet(merged, meta);
    }

    private static void logSummary(ICompiledSql compiled, int shardCount, int mergedRows, long costMillis,
                                   boolean limitHit) {
        if (FANOUT_LOG.isInfoEnabled()) {
            FANOUT_LOG.info("nop.orm.shard.fanout.execute:sqlName={},shardCount={},mergedRows={},costMillis={},"
                    + "limitHit={}", compiled.getName(), shardCount, mergedRows, costMillis, limitHit);
        }
    }

    /**
     * 从 TABLE marker 静态信息与执行期 paramValues 解析分片值（与 GenSqlTransformer 同一规则：
     * 编译期字面量优先，其次执行期参数下标）
     */
    private static Object resolveShardValue(SyntaxMarker syntaxMarker, List<Object> paramValues) {
        if (syntaxMarker.getShardValue() != null)
            return syntaxMarker.getShardValue();
        int index = syntaxMarker.getShardParamIndex();
        if (index >= 0 && paramValues != null && index < paramValues.size())
            return paramValues.get(index);
        return null;
    }
}
