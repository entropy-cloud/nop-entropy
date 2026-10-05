/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.shard;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.dao.DaoConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.dao.DaoErrors.ARG_REASON;
import static io.nop.dao.DaoErrors.ERR_DAO_SHARD_CONFIG_INVALID;

/**
 * 配置驱动的分片策略注册表（plan 2320，Decision裁定：配置承载形态为JSON配置串
 * {@code nop.orm.shard}，对齐 {@code nop.dao.readwrite} 的plan 2319解析先例；
 * IoC collect-beans仅作为自定义{@link IShardSelector} bean的装配通道，不作策略配置承载）。
 *
 * <p><b>未配置时零行为变更</b>：配置为空时所有方法表现与 {@link EmptyShardSelector} 一致
 * （selectShard返回null、策略NONE、无DDL展开）。</p>
 *
 * <p>配置契约（JSON文本，设计文档 §3.1）：</p>
 * <pre>
 * {
 *   "strategies": {
 *     "order-mod": {
 *       "type": "hash-mod",
 *       "shard-count": 16,
 *       "table-pattern": "{table}_{shard}",
 *       "query-space-pattern": "ds_{shard}",
 *       "missing-shard-policy": "fail",
 *       "default-shard": "0",
 *       "shard-names": ["0","1"],
 *       "entities": ["app.entity.NopOrder"]
 *     }
 *   },
 *   "default-strategy": "order-mod"
 * }
 * </pre>
 */
public class ConfigShardSelector implements IShardSelector {
    static final Logger LOG = LoggerFactory.getLogger(ConfigShardSelector.class);

    static final String PROP_STRATEGIES = "strategies";
    static final String PROP_DEFAULT_STRATEGY = "default-strategy";

    private Map<String, ShardStrategy> strategies = Collections.emptyMap();
    private String defaultStrategyName;
    private Map<String, ShardStrategy> entityBindings = Collections.emptyMap();
    private boolean hasConfig;

    /**
     * 解析 {@code nop.orm.shard} 配置并构建实体绑定表。所有配置错误启动即抛，
     * 不允许静默跳过（Minimum Rules #24）。
     */
    public void init() {
        // plan 2321：fanout 全局配置校验先于分片配置早退——配置错误不因未配置分片体系而静默
        validateFanoutGlobalConfig();

        String config = DaoConfigs.CFG_ORM_SHARD.get();
        if (config == null || config.isEmpty()) {
            LOG.debug("nop.orm.shard.no-config:skip-initialization");
            return;
        }

        Map<String, Object> root = parseConfig(config);
        Map<String, ShardStrategy> parsed = parseStrategies(root);
        String defaultStrategyName = parseDefaultStrategy(root, parsed);

        Map<String, ShardStrategy> bindings = new HashMap<>();
        for (ShardStrategy strategy : parsed.values()) {
            for (String entityName : strategy.getEntities()) {
                if (bindings.put(entityName, strategy) != null)
                    throw configInvalid("entity '" + entityName + "' is bound by multiple strategies");
            }
        }

        this.strategies = Collections.unmodifiableMap(parsed);
        this.defaultStrategyName = defaultStrategyName;
        this.entityBindings = Collections.unmodifiableMap(bindings);
        this.hasConfig = true;

        // plan 2321：启动期 WARN——fanout 策略已声明但全局开关关闭，运行期缺分片值查询退化为 fail。
        // 保留"一键关闭"应急刹车属性且不产生配置矛盾（执行引擎设计 §3.8）
        if (!isFanoutEnabled() && parsed.values().stream()
                .anyMatch(s -> s.getMissingShardPolicy() == MissingShardPolicy.FANOUT)) {
            LOG.warn("nop.orm.shard.fanout-strategy-declared-but-disabled:存在missing-shard-policy=fanout的策略"
                    + "，但全局开关nop.orm.shard.fanout-enabled=false，缺分片值查询将在运行期显式失败");
        }

        LOG.info("nop.orm.shard.init:strategies={},default-strategy={},entities={}", parsed.keySet(),
                defaultStrategyName, bindings.keySet());
    }

    /**
     * fanout 全局配置校验（plan 2321）：fanout-max-rows 非正数启动即抛
     * ERR_DAO_SHARD_CONFIG_INVALID（对齐 2320 配置错误启动即抛先例），
     * 且不因 nop.orm.shard 未配置而跳过。
     */
    private void validateFanoutGlobalConfig() {
        int maxRows = DaoConfigs.CFG_ORM_SHARD_FANOUT_MAX_ROWS.get();
        if (maxRows <= 0)
            throw new NopException(ERR_DAO_SHARD_CONFIG_INVALID).param(ARG_REASON,
                    "nop.orm.shard.fanout-max-rows must be positive, got: " + maxRows);
    }

    /**
     * fanout 全局开关。引擎（nop-orm）运行期读取同一配置做门控。
     */
    public static boolean isFanoutEnabled() {
        return Boolean.TRUE.equals(DaoConfigs.CFG_ORM_SHARD_FANOUT_ENABLED.get());
    }

    /**
     * 归并行数上限（逐分片增量计数）。引擎（nop-orm）运行期读取同一配置。
     */
    public static int getFanoutMaxRows() {
        return DaoConfigs.CFG_ORM_SHARD_FANOUT_MAX_ROWS.get();
    }

    private Map<String, Object> parseConfig(String text) {
        Object obj;
        try {
            obj = JSON.parseNonStrict(text);
        } catch (Exception e) {
            throw configInvalid("json parse error: " + e.getMessage());
        }
        if (!(obj instanceof Map))
            throw configInvalid("config must be a JSON object with 'strategies' and optional 'default-strategy'");
        return (Map<String, Object>) obj;
    }

    private Map<String, ShardStrategy> parseStrategies(Map<String, Object> root) {
        Object value = root.get(PROP_STRATEGIES);
        if (!(value instanceof Map) || ((Map<?, ?>) value).isEmpty())
            throw configInvalid("missing required non-empty object property '" + PROP_STRATEGIES + "'");
        return ShardStrategy.parseStrategies((Map<String, Object>) value);
    }

    private String parseDefaultStrategy(Map<String, Object> root, Map<String, ShardStrategy> parsed) {
        Object value = root.get(PROP_DEFAULT_STRATEGY);
        if (value == null)
            return null;
        String name = value.toString();
        if (!parsed.containsKey(name))
            throw configInvalid("'default-strategy' references unknown strategy: " + name);
        return name;
    }

    private NopException configInvalid(String reason) {
        return new NopException(ERR_DAO_SHARD_CONFIG_INVALID).param(ARG_REASON, reason);
    }

    ShardStrategy strategyFor(String entityName) {
        ShardStrategy strategy = entityBindings.get(entityName);
        if (strategy == null && defaultStrategyName != null)
            strategy = strategies.get(defaultStrategyName);
        return strategy;
    }

    @Override
    public ShardSelection selectShard(String entityName, String shardProp, Object shardValue) {
        if (!hasConfig)
            return null;
        ShardStrategy strategy = strategyFor(entityName);
        if (strategy == null)
            return null;
        return strategy.selectShard(shardProp, shardValue);
    }

    @Override
    public boolean hasStrategyConfig() {
        return hasConfig;
    }

    @Override
    public boolean isEntityBound(String entityName) {
        return hasConfig && strategyFor(entityName) != null;
    }

    @Override
    public MissingShardPolicy getMissingShardPolicy(String entityName) {
        if (!hasConfig)
            return MissingShardPolicy.NONE;
        ShardStrategy strategy = strategyFor(entityName);
        return strategy == null ? MissingShardPolicy.NONE : strategy.getMissingShardPolicy();
    }

    @Override
    public ShardSelection selectShardWhenMissing(String entityName, String shardProp) {
        if (!hasConfig)
            return null;
        ShardStrategy strategy = strategyFor(entityName);
        return strategy == null ? null : strategy.selectShardWhenMissing(shardProp);
    }

    @Override
    public List<String> getShardNames(String entityName) {
        if (!hasConfig)
            return Collections.emptyList();
        ShardStrategy strategy = strategyFor(entityName);
        return strategy == null ? Collections.emptyList() : strategy.shardNames();
    }

    /**
     * plan 2321：fanout 分片全集落点。与 {@link #getShardNames(String)} 同源——
     * 同一 {@link ShardStrategy#shardNames()} 序列，querySpace 由同策略
     * query-space-pattern 推导（设计 §3.8 m1 同源断言落点）。
     */
    @Override
    public List<ShardSelection> selectAllShards(String entityName) {
        if (!hasConfig)
            return Collections.emptyList();
        ShardStrategy strategy = strategyFor(entityName);
        return strategy == null ? Collections.emptyList() : strategy.shardSelections();
    }

    @Override
    public String getShardTableName(String entityName, String baseTableName, String shardName) {
        if (!hasConfig)
            return IShardSelector.super.getShardTableName(entityName, baseTableName, shardName);
        ShardStrategy strategy = strategyFor(entityName);
        if (strategy == null)
            return IShardSelector.super.getShardTableName(entityName, baseTableName, shardName);
        return strategy.buildTableName(baseTableName, shardName);
    }

    Map<String, ShardStrategy> getStrategies() {
        return strategies;
    }
}
