/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.dao.DaoConfigs;
import io.nop.dao.jdbc.txn.JdbcTransactionFactory;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.dao.utils.DaoHelper;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.dao.DaoErrors.ARG_REASON;
import static io.nop.dao.DaoErrors.ERR_DAO_READWRITE_CONFIG_INVALID;

/**
 * 读写分离装配初始化器（plan 2319，Decision裁定见设计文档）：
 * 读取{@code nop.dao.readwrite} JSON配置，解析write/read物理DataSource bean
 * （bean名前缀{@code nopReadWriteDs_}，经dao-defaults.beans.xml的
 * {@code ioc:collect-beans name-prefix="nopReadWriteDs_"}收集——该前缀刻意区别于
 * {@code nopDataSource_}，避免被DefaultTransactionManager的collect-beans误收集为幻影querySpace），
 * 构造{@link ReadWriteRoutingDataSource}并注册进事务管理器：
 * 非default querySpace经{@code addQuerySpace}，default querySpace经{@code setDefaultFactory}替换。
 *
 * <p><b>未配置时零行为变更</b>：配置为空则本初始化器不注册任何东西，
 * 全部既有DataSource/事务工厂装配保持原样。</p>
 */
public class ReadWriteDataSourceInitializer {
    static final Logger LOG = LoggerFactory.getLogger(ReadWriteDataSourceInitializer.class);

    static final String PROP_WRITE_DS = "write-ds";
    static final String PROP_READ_DS = "read-ds";
    static final String PROP_LOAD_BALANCER = "load-balancer";
    static final String PROP_WEIGHTS = "weights";
    static final String PROP_FAIL_THRESHOLD = "replica-fail-threshold";
    static final String PROP_COOLDOWN_SECONDS = "replica-cooldown-seconds";

    static final int DEFAULT_FAIL_THRESHOLD = 3;
    static final int DEFAULT_COOLDOWN_SECONDS = 30;

    private DefaultTransactionManager transactionManager;
    private Map<String, DataSource> dataSources;

    @Inject
    public void setTransactionManager(DefaultTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public DefaultTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * 物理DataSource bean集合（bean名→DataSource），由IoC的collect-beans注入
     */
    public void setDataSources(Map<String, DataSource> dataSources) {
        this.dataSources = dataSources;
    }

    public Map<String, DataSource> getDataSources() {
        return dataSources;
    }

    @PostConstruct
    public void init() {
        String config = DaoConfigs.CFG_DAO_READWRITE.get();
        if (config == null || config.isEmpty()) {
            LOG.debug("nop.dao.readwrite.no-config:skip-initialization");
            return;
        }

        Map<String, Object> parsed = parseConfig(config);
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            String querySpace = entry.getKey();
            SpaceConfig spaceConfig = parseSpaceConfig(querySpace, entry.getValue());
            ReadWriteRoutingDataSource routing = buildRouting(querySpace, spaceConfig);

            if (DaoHelper.isDefaultQuerySpace(querySpace)) {
                // default querySpace：替换defaultFactory（setter在DefaultTransactionManager上已存在）
                transactionManager.setDefaultFactory(new JdbcTransactionFactory(routing));
                LOG.info("nop.dao.readwrite.register-default-query-space:querySpace={},replicas={}", querySpace,
                        spaceConfig.readDs.size());
            } else {
                transactionManager.addQuerySpace(querySpace, routing);
                LOG.info("nop.dao.readwrite.register-query-space:querySpace={},replicas={}", querySpace,
                        spaceConfig.readDs.size());
            }
        }
    }

    private ReadWriteRoutingDataSource buildRouting(String querySpace, SpaceConfig spaceConfig) {
        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setQuerySpace(querySpace);
        routing.setWriteDataSource(resolveDataSourceBean(querySpace, spaceConfig.writeDs));
        for (String readBean : spaceConfig.readDs) {
            routing.addReplica(readBean, resolveDataSourceBean(querySpace, readBean));
        }
        try {
            routing.setLoadBalancer(ReadWriteLoadBalancers.of(spaceConfig.loadBalancer, spaceConfig.weights));
        } catch (IllegalArgumentException e) {
            throw configInvalid("querySpace[" + querySpace + "]: " + e.getMessage());
        }
        routing.setReplicaFailThreshold(spaceConfig.failThreshold);
        routing.setReplicaCooldownSeconds(spaceConfig.cooldownSeconds);
        return routing;
    }

    private DataSource resolveDataSourceBean(String querySpace, String beanName) {
        DataSource ds = dataSources == null ? null : dataSources.get(beanName);
        if (ds == null) {
            throw configInvalid("querySpace[" + querySpace + "]: data source bean not found: " + beanName
                    + " (physical data source beans must be named with prefix nopReadWriteDs_)");
        }
        return ds;
    }

    private Map<String, Object> parseConfig(String text) {
        Object obj;
        try {
            obj = JSON.parseNonStrict(text);
        } catch (Exception e) {
            throw configInvalid("json parse error: " + e.getMessage());
        }
        if (!(obj instanceof Map))
            throw configInvalid("config must be a JSON object keyed by querySpace name");
        return (Map<String, Object>) obj;
    }

    private SpaceConfig parseSpaceConfig(String querySpace, Object value) {
        if (!(value instanceof Map))
            throw configInvalid("querySpace[" + querySpace + "]: config value must be a JSON object");

        Map<String, Object> map = (Map<String, Object>) value;
        SpaceConfig config = new SpaceConfig();

        Object writeDs = map.get(PROP_WRITE_DS);
        if (!(writeDs instanceof String) || ((String) writeDs).isEmpty())
            throw configInvalid("querySpace[" + querySpace + "]: missing required string property '" + PROP_WRITE_DS + "'");
        config.writeDs = (String) writeDs;

        Object readDs = map.get(PROP_READ_DS);
        if (!(readDs instanceof List) || ((List<?>) readDs).isEmpty())
            throw configInvalid("querySpace[" + querySpace + "]: missing required non-empty array property '" + PROP_READ_DS + "'");
        for (Object item : (List<?>) readDs) {
            if (!(item instanceof String) || ((String) item).isEmpty())
                throw configInvalid("querySpace[" + querySpace + "]: '" + PROP_READ_DS + "' items must be bean names");
            config.readDs.add((String) item);
        }

        Object loadBalancer = map.get(PROP_LOAD_BALANCER);
        config.loadBalancer = loadBalancer == null ? null : loadBalancer.toString();

        Object weights = map.get(PROP_WEIGHTS);
        if (weights != null) {
            if (!(weights instanceof Map))
                throw configInvalid("querySpace[" + querySpace + "]: '" + PROP_WEIGHTS + "' must be an object");
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) weights).entrySet()) {
                Integer weight = ConvertHelper.toInteger(entry.getValue(), err -> null);
                if (weight == null)
                    throw configInvalid("querySpace[" + querySpace + "]: weight for '" + entry.getKey()
                            + "' is not a number");
                config.weights.put(entry.getKey(), weight);
            }
        }

        config.failThreshold = readInt(map, PROP_FAIL_THRESHOLD, DEFAULT_FAIL_THRESHOLD, querySpace);
        config.cooldownSeconds = readInt(map, PROP_COOLDOWN_SECONDS, DEFAULT_COOLDOWN_SECONDS, querySpace);
        return config;
    }

    private int readInt(Map<String, Object> map, String key, int defaultValue, String querySpace) {
        Object value = map.get(key);
        if (value == null)
            return defaultValue;
        Integer parsed = ConvertHelper.toInteger(value, err -> null);
        if (parsed == null)
            throw configInvalid("querySpace[" + querySpace + "]: property '" + key + "' is not a number");
        return parsed;
    }

    private NopException configInvalid(String reason) {
        return new NopException(ERR_DAO_READWRITE_CONFIG_INVALID).param(ARG_REASON, reason);
    }

    static class SpaceConfig {
        String writeDs;
        List<String> readDs = new java.util.ArrayList<>();
        String loadBalancer;
        Map<String, Integer> weights = new HashMap<>();
        int failThreshold = DEFAULT_FAIL_THRESHOLD;
        int cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
    }
}
