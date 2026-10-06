/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.dao.DaoConfigs;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.shard.ShardStrategy;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.nop.orm.migration.MigrationErrors.ARG_ENTITY_ID;
import static io.nop.orm.migration.MigrationErrors.ARG_ENTITY_NAME;
import static io.nop.orm.migration.MigrationErrors.ARG_REASON;
import static io.nop.orm.migration.MigrationErrors.ARG_SOURCE_TABLE;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CONFIG_INVALID;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_NULL_SHARD_KEY;

/**
 * 搬迁映射面（设计 §4.4/§4.5）：源物理表全集、目标物理表全集、分片路由。
 *
 * <ul>
 * <li>rescale：源侧按<b>当前生效</b>的 {@code nop.orm.shard}（旧策略）展开 N 张物理表（实体未绑定
 *     旧策略时退化为单表，即 1→M 扩容）；目标侧按作业配置的<b>新策略</b>展开 M 张物理表；路由 =
 *     行内分片键值经新策略 {@code selectShard} 计算落点。分片键为 null 的行无条件 fail-fast
 *     （设计 m3：不静默跳过也不猜测落片，即使策略声明 default-shard）。</li>
 * <li>relocate：1:1 表映射（表名不变），目标 querySpace 按 {@code target-query-space-mapping}
 *     平移；无路由器。</li>
 * </ul>
 *
 * <p>新策略解析用 {@link ShardStrategy#parseStrategies}（独立于运行期 {@code ConfigShardSelector}，
 * 只读解析、不触碰运行期路由，设计 §4.4 边界）。</p>
 */
public class TargetShardMapping {

    /**
     * 物理表引用（querySpace + 物理表名）。
     */
    public static final class PhysicalTable {
        private final String querySpace;
        private final String tableName;

        public PhysicalTable(String querySpace, String tableName) {
            this.querySpace = querySpace == null ? "" : querySpace;
            this.tableName = tableName;
        }

        public String getQuerySpace() {
            return querySpace;
        }

        public String getTableName() {
            return tableName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof PhysicalTable))
                return false;
            PhysicalTable that = (PhysicalTable) o;
            return querySpace.equals(that.querySpace) && tableName.equals(that.tableName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(querySpace, tableName);
        }

        @Override
        public String toString() {
            return querySpace + ":" + tableName;
        }
    }

    private final String entityName;
    private final String baseTableName;
    /** 分片键物理列名（null = 实体未声明分片列，relocate 或无分片单表形态）。 */
    private final String shardColumnName;
    private final List<PhysicalTable> sourceTables;
    private final List<PhysicalTable> targetTables;
    private final ShardStrategy newStrategy;
    /** relocate 的旧 querySpace → 新 querySpace 映射（rescale 为 null）。 */
    private final Map<String, String> querySpaceMapping;

    private TargetShardMapping(String entityName, String baseTableName, String shardColumnName,
                               List<PhysicalTable> sourceTables, List<PhysicalTable> targetTables,
                               ShardStrategy newStrategy, Map<String, String> querySpaceMapping) {
        this.entityName = entityName;
        this.baseTableName = baseTableName;
        this.shardColumnName = shardColumnName;
        this.sourceTables = Collections.unmodifiableList(sourceTables);
        this.targetTables = Collections.unmodifiableList(targetTables);
        this.newStrategy = newStrategy;
        this.querySpaceMapping = querySpaceMapping;
    }

    public static TargetShardMapping of(IOrmSessionFactory sessionFactory, MigrationJobConfig config) {
        IEntityModel entityModel = sessionFactory.getOrmModel().requireEntityModel(config.getEntityName());
        String shardColumn = shardColumnOf(entityModel);
        if (config.isRescale()) {
            return rescale(entityModel, shardColumn, config);
        }
        return relocate(entityModel, shardColumn, config);
    }

    private static String shardColumnOf(IEntityModel entityModel) {
        IColumnModel shardColumn = entityModel.isUseShard() ? entityModel.getShardColumn() : null;
        return shardColumn == null ? null : shardColumn.getCode();
    }

    private static TargetShardMapping rescale(IEntityModel entityModel, String shardColumn,
                                              MigrationJobConfig config) {
        ShardStrategy newStrategy = requireStrategyForEntity(config.getTargetStrategy(),
                config.getEntityName());

        // 源侧：当前生效 nop.orm.shard 的旧策略展开；未绑定旧策略 = 单表源（1→M 扩容形态）
        List<PhysicalTable> sourceTables = new ArrayList<>();
        ShardStrategy oldStrategy = currentStrategyForEntity(entityModel);
        if (oldStrategy == null) {
            sourceTables.add(new PhysicalTable(entityModel.getQuerySpace(), entityModel.getTableName()));
        } else {
            for (ShardSelection sel : oldStrategy.shardSelections()) {
                sourceTables.add(new PhysicalTable(sel.getQuerySpace(),
                        oldStrategy.buildTableName(entityModel.getTableName(), sel.getShardName())));
            }
        }

        // 目标侧：新策略展开
        List<PhysicalTable> targetTables = new ArrayList<>();
        for (ShardSelection sel : newStrategy.shardSelections()) {
            targetTables.add(new PhysicalTable(sel.getQuerySpace(),
                    newStrategy.buildTableName(entityModel.getTableName(), sel.getShardName())));
        }
        return new TargetShardMapping(entityModel.getName(), entityModel.getTableName(), shardColumn,
                sourceTables, targetTables, newStrategy, null);
    }

    private static TargetShardMapping relocate(IEntityModel entityModel, String shardColumn,
                                               MigrationJobConfig config) {
        Map<String, String> mapping = config.getTargetQuerySpaceMapping();
        List<PhysicalTable> sourceTables = new ArrayList<>();
        List<PhysicalTable> targetTables = new ArrayList<>();
        for (String sourceQuerySpace : config.getSourceQuerySpaces()) {
            String targetQuerySpace = mapping.get(sourceQuerySpace);
            if (targetQuerySpace == null)
                throw new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON,
                        "source query space '" + sourceQuerySpace
                                + "' has no target-query-space-mapping entry");
            sourceTables.add(new PhysicalTable(sourceQuerySpace, entityModel.getTableName()));
            targetTables.add(new PhysicalTable(targetQuerySpace, entityModel.getTableName()));
        }
        return new TargetShardMapping(entityModel.getName(), entityModel.getTableName(), shardColumn,
                sourceTables, targetTables, null, mapping);
    }

    /**
     * 当前生效配置中绑定实体的旧策略（供源侧物理表展开）。未配置/未绑定返回 null（单表源形态）。
     */
    static ShardStrategy currentStrategyForEntity(IEntityModel entityModel) {
        String config = DaoConfigs.CFG_ORM_SHARD.get();
        if (config == null || config.isEmpty())
            return null;
        return strategyForEntity(parseShardConfigText(config), null, entityModel.getName());
    }

    private static Map<String, ShardStrategy> parseShardConfigMap(Map<String, Object> strategyConfig,
                                                                  String errorContext) {
        Object strategies = strategyConfig.get("strategies");
        if (!(strategies instanceof Map) || ((Map<?, ?>) strategies).isEmpty())
            throw new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON,
                    errorContext + " must carry the complete nop.orm.shard shape"
                            + " ({strategies:{...},default-strategy:...})");
        return ShardStrategy.parseStrategies((Map<String, Object>) strategies);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ShardStrategy> parseShardConfigText(String json) {
        Object parsed;
        try {
            parsed = JSON.parseNonStrict(json);
        } catch (Exception e) {
            throw new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON,
                    "shard config json parse error: " + e.getMessage());
        }
        if (!(parsed instanceof Map))
            throw new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON,
                    "shard config must be a JSON object");
        return parseShardConfigMap((Map<String, Object>) parsed, "nop.orm.shard");
    }

    private static ShardStrategy strategyForEntity(Map<String, ShardStrategy> strategies, String defaultName,
                                                   String entityName) {
        for (ShardStrategy strategy : strategies.values()) {
            if (strategy.getEntities().contains(entityName))
                return strategy;
        }
        return defaultName == null ? null : strategies.get(defaultName);
    }

    private static ShardStrategy requireStrategyForEntity(Map<String, Object> targetStrategy, String entityName) {
        Object defaultName = targetStrategy.get("default-strategy");
        ShardStrategy strategy = strategyForEntity(
                parseShardConfigMap(targetStrategy, "target-strategy"),
                defaultName == null ? null : defaultName.toString(), entityName);
        if (strategy == null)
            throw new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON,
                    "target-strategy does not bind entity '" + entityName
                            + "' (neither via entities list nor default-strategy)");
        return strategy;
    }

    /**
     * FULL 消费面路由：行值 → 目标物理表。分片键 null 无条件 fail-fast。
     *
     * @param row         行的物理列名 → 值映射（offset 分页读出的源行）
     * @param pkDisplay   主键展示值（错误定位信息）
     * @param sourceTable 源物理表名（错误定位信息）
     */
    public PhysicalTable routeRow(Map<String, Object> row, Object pkDisplay, String sourceTable) {
        if (isRelocate())
            return new PhysicalTable("", baseTableName);
        Object shardValue = caseInsensitiveGet(row, shardColumnName);
        if (shardValue == null) {
            throw new NopException(ERR_MIG_NULL_SHARD_KEY)
                    .param(ARG_ENTITY_NAME, entityName)
                    .param(ARG_ENTITY_ID, pkDisplay)
                    .param(ARG_SOURCE_TABLE, sourceTable);
        }
        return routeShardValue(shardValue);
    }

    /**
     * 增量路由：分片键值 → 目标物理表。null 值 fail-fast（与 FULL 同契约，设计 m3）。
     */
    public PhysicalTable routeShardValue(Object shardValue) {
        if (isRelocate())
            return new PhysicalTable("", baseTableName);
        if (shardValue == null)
            throw new NopException(ERR_MIG_NULL_SHARD_KEY)
                    .param(ARG_ENTITY_NAME, entityName)
                    .param(ARG_ENTITY_ID, null)
                    .param(ARG_SOURCE_TABLE, baseTableName);
        ShardSelection sel = newStrategy.selectShard(shardColumnName, shardValue);
        return new PhysicalTable(sel.getQuerySpace(),
                newStrategy.buildTableName(baseTableName, sel.getShardName()));
    }

    private static Object caseInsensitiveGet(Map<String, Object> row, String name) {
        if (name == null)
            return null;
        Object direct = row.get(name);
        if (direct != null)
            return direct;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
                return entry.getValue();
        }
        return null;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getBaseTableName() {
        return baseTableName;
    }

    public String getShardColumnName() {
        return shardColumnName;
    }

    public List<PhysicalTable> getSourceTables() {
        return sourceTables;
    }

    public List<PhysicalTable> getTargetTables() {
        return targetTables;
    }

    /** relocate 场景：源 querySpace → 目标 querySpace；rescale 原样返回。 */
    public String mapQuerySpace(String sourceQuerySpace) {
        if (!isRelocate())
            return sourceQuerySpace;
        String mapped = querySpaceMapping.get(sourceQuerySpace);
        if (mapped == null)
            throw new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON,
                    "source query space '" + sourceQuerySpace + "' has no target-query-space-mapping entry");
        return mapped;
    }

    public boolean isRelocate() {
        return querySpaceMapping != null;
    }

    ShardStrategy getNewStrategyForTest() {
        return newStrategy;
    }

    /** 供诊断/日志输出：映射摘要。 */
    public Map<String, Object> describe() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entity", entityName);
        map.put("baseTable", baseTableName);
        map.put("sourceTables", sourceTables.toString());
        map.put("targetTables", targetTables.toString());
        return map;
    }
}
