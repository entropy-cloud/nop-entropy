/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.shard;

import io.nop.api.core.exceptions.NopException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.dao.DaoErrors.ARG_REASON;
import static io.nop.dao.DaoErrors.ERR_DAO_SHARD_CONFIG_INVALID;

/**
 * 分片策略（plan 2320，设计文档 §3.1）：算法 + 分片命名/物理表名规则 + querySpace 映射 +
 * 缺分片值策略 + 绑定实体列表。由 {@link ConfigShardSelector} 从
 * {@code nop.orm.shard} JSON 配置解析构建（对齐 nop.dao.readwrite 的解析先例）。
 *
 * <p>策略是部署环境属性，不进 orm.xml 模型（设计裁定：dev/prod 分片数不同靠配置分叉而非模型分叉）。</p>
 */
public class ShardStrategy {
    public static final String TYPE_MOD = "mod";
    public static final String TYPE_HASH_MOD = "hash-mod";
    public static final String TYPE_RANGE = "range";
    public static final String TYPE_INTERVAL = "interval";
    public static final String TYPE_INLINE = "inline";

    public static final String DEFAULT_TABLE_PATTERN = "{table}_{shard}";

    private final String name;
    private final String type;
    private final IShardAlgorithm algorithm;
    private final String tablePattern;
    private final String querySpacePattern;
    private final MissingShardPolicy missingShardPolicy;
    private final String defaultShard;
    private final Set<String> entities;
    private final List<String> explicitShardNames;

    ShardStrategy(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.algorithm = builder.algorithm;
        this.tablePattern = builder.tablePattern;
        this.querySpacePattern = builder.querySpacePattern;
        this.missingShardPolicy = builder.missingShardPolicy;
        this.defaultShard = builder.defaultShard;
        this.entities = builder.entities;
        this.explicitShardNames = builder.explicitShardNames;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Set<String> getEntities() {
        return entities;
    }

    MissingShardPolicy getMissingShardPolicy() {
        return missingShardPolicy;
    }

    /**
     * 分片值路由：分片值非null时调用。算法内部对类型不兼容值显式抛错。
     */
    public ShardSelection selectShard(String shardProp, Object shardValue) {
        String shardName = algorithm.selectShard(shardProp, shardValue);
        return buildSelection(shardName);
    }

    /**
     * 缺分片值回落：仅当策略声明 default-shard 时返回非null。
     */
    public ShardSelection selectShardWhenMissing(String shardProp) {
        if (missingShardPolicy != MissingShardPolicy.DEFAULT_SHARD || defaultShard == null)
            return null;
        return buildSelection(defaultShard);
    }

    /**
     * 分片名全集：显式配置的 shard-names 优先（inline等不可枚举算法必须显式配置），
     * 否则取算法静态枚举结果。枚举结果为空时抛配置错误（DDL展开无从展开）。
     */
    public List<String> shardNames() {
        if (explicitShardNames != null)
            return explicitShardNames;
        List<String> names = algorithm.shardNames();
        if (names.isEmpty())
            throw new NopException(ERR_DAO_SHARD_CONFIG_INVALID).param(ARG_REASON, "strategy[" + name
                    + "]: cannot enumerate shard names for DDL expansion, config 'shard-names' explicitly");
        return names;
    }

    /**
     * 分片全集落点（plan 2321，执行引擎设计 §3.8 m1）：{@link #shardNames()} 每项推导为
     * {@link ShardSelection}（querySpace 由本策略 query-space-pattern 同源推导）。
     * 与 {@link #shardNames()} 严格同源（同一分片名序列），消费方为
     * {@link ConfigShardSelector#selectAllShards}。
     */
    public List<ShardSelection> shardSelections() {
        List<String> names = shardNames();
        List<ShardSelection> ret = new ArrayList<>(names.size());
        for (String shardName : names) {
            ret.add(buildSelection(shardName));
        }
        return ret;
    }

    /**
     * 物理表名规则。缺省 {@code {table}_{shard}}。
     */
    public String buildTableName(String baseTableName, String shardName) {
        if (DEFAULT_TABLE_PATTERN.equals(tablePattern))
            return baseTableName + '_' + shardName;
        return tablePattern.replace("{table}", baseTableName).replace("{shard}", shardName);
    }

    ShardSelection buildSelection(String shardName) {
        String querySpace = querySpacePattern == null ? null
                : querySpacePattern.replace("{shard}", shardName);
        return new ShardSelection(querySpace, shardName);
    }

    static ShardStrategy parse(String name, Map<String, Object> map) {
        Builder builder = new Builder(name);

        Object type = map.get("type");
        if (!(type instanceof String) || ((String) type).isEmpty())
            throw configInvalid("strategy[" + name + "]: missing required string property 'type'");
        builder.type((String) type);

        int shardCount = 0;
        Object count = map.get("shard-count");
        if (count != null) {
            Integer parsed = toInt(count);
            if (parsed == null || parsed <= 0)
                throw configInvalid("strategy[" + name + "]: 'shard-count' must be a positive number");
            shardCount = parsed;
        }

        if (TYPE_MOD.equals(type) || TYPE_HASH_MOD.equals(type)) {
            if (shardCount <= 0)
                throw configInvalid("strategy[" + name + "]: type '" + type + "' requires positive 'shard-count'");
            builder.algorithm(TYPE_MOD.equals(type) ? new ShardAlgorithms.ModShardAlgorithm(shardCount)
                    : new ShardAlgorithms.HashModShardAlgorithm(shardCount));
        } else if (TYPE_RANGE.equals(type)) {
            Object ranges = map.get("ranges");
            if (!(ranges instanceof Map))
                throw configInvalid("strategy[" + name + "]: type 'range' requires object property 'ranges'");
            builder.algorithm(new ShardAlgorithms.RangeShardAlgorithm((Map<String, Object>) ranges));
        } else if (TYPE_INTERVAL.equals(type)) {
            builder.algorithm(new ShardAlgorithms.IntervalShardAlgorithm(str(map.get("interval-step")),
                    str(map.get("datetime-lower")), str(map.get("datetime-upper")), str(map.get("format"))));
        } else if (TYPE_INLINE.equals(type)) {
            builder.algorithm(new ShardAlgorithms.InlineShardAlgorithm(str(map.get("algorithm-expression"))));
        } else {
            throw configInvalid("strategy[" + name + "]: unknown algorithm type '" + type
                    + "' (expected: mod | hash-mod | range | interval | inline)");
        }

        Object tablePattern = map.get("table-pattern");
        builder.tablePattern(tablePattern == null ? DEFAULT_TABLE_PATTERN : requireString(name, "table-pattern",
                tablePattern));

        Object querySpacePattern = map.get("query-space-pattern");
        builder.querySpacePattern(querySpacePattern == null ? null
                : requireString(name, "query-space-pattern", querySpacePattern));

        Object policy = map.get("missing-shard-policy");
        try {
            builder.missingShardPolicy(MissingShardPolicy.parse(policy == null ? null : policy.toString()));
        } catch (IllegalArgumentException e) {
            throw configInvalid("strategy[" + name + "]: " + e.getMessage());
        }
        if (policy == null && map.get("default-shard") != null) {
            // 配置了default-shard但未声明policy：视为default-shard策略的显式使用意图
            builder.missingShardPolicy(MissingShardPolicy.DEFAULT_SHARD);
        }

        Object defaultShard = map.get("default-shard");
        builder.defaultShard(defaultShard == null ? null : requireString(name, "default-shard", defaultShard));
        if (builder.missingShardPolicy == MissingShardPolicy.DEFAULT_SHARD && defaultShard == null)
            throw configInvalid("strategy[" + name + "]: missing-shard-policy 'default-shard' requires 'default-shard'");

        Object shardNames = map.get("shard-names");
        if (shardNames != null) {
            if (!(shardNames instanceof List) || ((List<?>) shardNames).isEmpty())
                throw configInvalid("strategy[" + name + "]: 'shard-names' must be a non-empty array");
            List<String> names = new ArrayList<>();
            for (Object item : (List<?>) shardNames) {
                if (!(item instanceof String) || ((String) item).isEmpty())
                    throw configInvalid("strategy[" + name + "]: 'shard-names' items must be non-empty strings");
                names.add((String) item);
            }
            builder.explicitShardNames(names);
        }

        Object entities = map.get("entities");
        if (entities != null) {
            if (!(entities instanceof List))
                throw configInvalid("strategy[" + name + "]: 'entities' must be an array of entity names");
            for (Object item : (List<?>) entities) {
                if (!(item instanceof String) || ((String) item).isEmpty())
                    throw configInvalid("strategy[" + name + "]: 'entities' items must be non-empty entity names");
                builder.bindEntity((String) item);
            }
        }
        return builder.build();
    }

    private static String requireString(String strategyName, String prop, Object value) {
        if (!(value instanceof String) || ((String) value).isEmpty())
            throw configInvalid("strategy[" + strategyName + "]: '" + prop + "' must be a non-empty string");
        return (String) value;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer toInt(Object value) {
        if (value instanceof Number)
            return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static NopException configInvalid(String reason) {
        return new NopException(ERR_DAO_SHARD_CONFIG_INVALID).param(ARG_REASON, reason);
    }

    static class Builder {
        final String name;
        String type;
        IShardAlgorithm algorithm;
        String tablePattern = DEFAULT_TABLE_PATTERN;
        String querySpacePattern;
        MissingShardPolicy missingShardPolicy = MissingShardPolicy.FAIL;
        String defaultShard;
        Set<String> entities = new java.util.LinkedHashSet<>();
        List<String> explicitShardNames;

        Builder(String name) {
            this.name = name;
        }

        Builder type(String type) {
            this.type = type;
            return this;
        }

        Builder algorithm(IShardAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        Builder tablePattern(String tablePattern) {
            this.tablePattern = tablePattern;
            return this;
        }

        Builder querySpacePattern(String querySpacePattern) {
            this.querySpacePattern = querySpacePattern;
            return this;
        }

        Builder missingShardPolicy(MissingShardPolicy policy) {
            this.missingShardPolicy = policy;
            return this;
        }

        Builder defaultShard(String defaultShard) {
            this.defaultShard = defaultShard;
            return this;
        }

        Builder explicitShardNames(List<String> names) {
            this.explicitShardNames = Collections.unmodifiableList(names);
            return this;
        }

        Builder bindEntity(String entityName) {
            this.entities.add(entityName);
            return this;
        }

        ShardStrategy build() {
            return new ShardStrategy(this);
        }
    }

    /**
     * 解析整个 nop.orm.shard 配置对象为策略表。供 ConfigShardSelector 与测试共用。
     * WI9：放宽为 public——数据搬迁编排器（nop-orm-migration）需要按切换后的新策略配置
     * 独立解析 ShardStrategy 以计算目标物理表，不触碰运行期路由（编排器只读解析，不 init）。
     */
    public static Map<String, ShardStrategy> parseStrategies(Map<String, Object> strategiesMap) {
        Map<String, ShardStrategy> ret = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : strategiesMap.entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() instanceof Map))
                throw configInvalid("strategies." + name + ": config value must be a JSON object");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry.getValue();
            if (ret.put(name, ShardStrategy.parse(name, map)) != null)
                throw configInvalid("duplicate strategy name: " + name);
        }
        return ret;
    }
}
