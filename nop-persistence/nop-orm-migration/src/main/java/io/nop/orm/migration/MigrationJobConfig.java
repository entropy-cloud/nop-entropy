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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CONFIG_INVALID;
import static io.nop.orm.migration.MigrationErrors.ARG_REASON;

/**
 * {@code nop.orm.migration} JSON 配置串的解析结果（设计 §4.8）。
 *
 * <p>所有配置错误在解析期显式失败（ERR_MIG_CONFIG_INVALID），不允许静默缺省。
 * 字段语义：
 * <ul>
 * <li>{@code scenario=rescale}（分片扩容 N→M）：{@code target-strategy} 必填，为切换时原子翻转的
 *     新 {@code nop.orm.shard} 完整配置对象（含 strategies/default-strategy）；</li>
 * <li>{@code scenario=relocate}（同构机房迁移）：{@code target-query-space-mapping} 必填，1:1 表映射、
 *     无路由器；</li>
 * <li>{@code incremental.enabled=true} 时 {@code connector-name} 必填，且源数据库必须在 CDC 白名单
 *     （mysql/postgres）；{@code position-p0} 由 PREPARE 钉定后回填；</li>
 * <li>{@code write-gate} 仅声明作业启动时的写停止关初值，运行期真相源是平面键
 *     {@code nop.orm.migration.write-gate}（OrmConfigs）。</li>
 * </ul>
 */
public class MigrationJobConfig {
    public static final String SCENARIO_RESCALE = "rescale";
    public static final String SCENARIO_RELOCATE = "relocate";

    private final String scenario;
    private final String entityName;
    private final List<String> sourceQuerySpaces;
    /** rescale：切换时原子翻转的新 nop.orm.shard 配置对象（原样保留 JSON 结构）。 */
    private final Map<String, Object> targetStrategy;
    /** relocate：旧 querySpace → 新 querySpace 映射。 */
    private final Map<String, String> targetQuerySpaceMapping;
    private final String initialWriteGate;
    private final boolean incrementalEnabled;
    private final String incrementalConnectorName;
    private String incrementalPositionP0;
    private final long quiesceSeconds;
    /** CDC 连接参数（incremental.debezium：database-host/port/user/password/name、server-name 等）。 */
    private final Map<String, Object> incrementalDebezium;
    private final boolean verifyCount;
    private final double verifySampleRatio;
    private final boolean verifyFullChecksum;
    private final double throttleRateLimit;
    private final int throttleBatchSize;
    private final int throttleConcurrency;
    private final String batchTaskName;

    private MigrationJobConfig(Builder b) {
        this.scenario = b.scenario;
        this.entityName = b.entityName;
        this.sourceQuerySpaces = Collections.unmodifiableList(b.sourceQuerySpaces);
        this.targetStrategy = b.targetStrategy;
        this.targetQuerySpaceMapping = b.targetQuerySpaceMapping;
        this.initialWriteGate = b.initialWriteGate;
        this.incrementalEnabled = b.incrementalEnabled;
        this.incrementalConnectorName = b.incrementalConnectorName;
        this.incrementalPositionP0 = b.incrementalPositionP0;
        this.incrementalDebezium = b.incrementalDebezium == null
                ? Collections.emptyMap() : Collections.unmodifiableMap(b.incrementalDebezium);
        this.quiesceSeconds = b.quiesceSeconds;
        this.verifyCount = b.verifyCount;
        this.verifySampleRatio = b.verifySampleRatio;
        this.verifyFullChecksum = b.verifyFullChecksum;
        this.throttleRateLimit = b.throttleRateLimit;
        this.throttleBatchSize = b.throttleBatchSize;
        this.throttleConcurrency = b.throttleConcurrency;
        this.batchTaskName = b.batchTaskName;
    }

    public static MigrationJobConfig parse(String jsonText) {
        if (jsonText == null || jsonText.isEmpty())
            throw configInvalid("nop.orm.migration config is empty");
        Object root;
        try {
            root = JSON.parseNonStrict(jsonText);
        } catch (Exception e) {
            throw configInvalid("json parse error: " + e.getMessage());
        }
        if (!(root instanceof Map))
            throw configInvalid("config must be a JSON object");
        return fromMap((Map<String, Object>) root);
    }

    @SuppressWarnings("unchecked")
    public static MigrationJobConfig fromMap(Map<String, Object> root) {
        Builder b = new Builder();

        String scenario = requireString(root, "scenario");
        if (!SCENARIO_RESCALE.equals(scenario) && !SCENARIO_RELOCATE.equals(scenario))
            throw configInvalid("scenario must be 'rescale' or 'relocate', got: " + scenario);
        b.scenario = scenario;

        b.entityName = requireString(root, "entity");

        List<String> querySpaces = stringList(root.get("source-query-spaces"));
        if (querySpaces.isEmpty())
            throw configInvalid("missing required non-empty array property 'source-query-spaces'");
        b.sourceQuerySpaces = querySpaces;

        if (SCENARIO_RESCALE.equals(scenario)) {
            Object strategy = root.get("target-strategy");
            if (!(strategy instanceof Map) || ((Map<String, Object>) strategy).isEmpty())
                throw configInvalid("scenario rescale requires non-empty object property 'target-strategy'"
                        + " (the complete new nop.orm.shard config)");
            b.targetStrategy = (Map<String, Object>) strategy;
        } else {
            Object mapping = root.get("target-query-space-mapping");
            if (!(mapping instanceof Map) || ((Map<String, Object>) mapping).isEmpty())
                throw configInvalid("scenario relocate requires non-empty object property"
                        + " 'target-query-space-mapping'");
            Map<String, String> typed = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) mapping).entrySet()) {
                if (entry.getValue() == null)
                    throw configInvalid("'target-query-space-mapping' has null target for key: " + entry.getKey());
                typed.put(entry.getKey(), entry.getValue().toString());
            }
            b.targetQuerySpaceMapping = typed;
        }

        String writeGate = optionalString(root, "write-gate");
        if (writeGate != null && !"open".equals(writeGate) && !"closed".equals(writeGate))
            throw configInvalid("write-gate must be 'open' or 'closed', got: " + writeGate);
        b.initialWriteGate = writeGate;

        Map<String, Object> incremental = section(root, "incremental");
        b.incrementalEnabled = Boolean.TRUE.equals(incremental.get("enabled"));
        if (b.incrementalEnabled) {
            b.incrementalConnectorName = requireSectionString(incremental, "connector-name");
            if (b.incrementalConnectorName.isEmpty())
                throw configInvalid("incremental.connector-name must not be empty when incremental.enabled=true");
        }
        Object debezium = incremental.get("debezium");
        if (debezium != null) {
            if (!(debezium instanceof Map))
                throw configInvalid("incremental.debezium must be a JSON object");
            b.incrementalDebezium = (Map<String, Object>) debezium;
        }
        b.incrementalPositionP0 = optionalString(incremental, "position-p0");
        b.quiesceSeconds = requirePositiveLong(incremental, "quiesce-seconds", 30);

        Map<String, Object> verify = section(root, "verify");
        b.verifyCount = !Boolean.FALSE.equals(verify.get("count"));
        b.verifySampleRatio = ratio(verify.get("sample-ratio"));
        b.verifyFullChecksum = Boolean.TRUE.equals(verify.get("full-checksum"));

        Map<String, Object> throttle = section(root, "throttle");
        b.throttleRateLimit = nonNegativeDouble(throttle.get("rate-limit"));
        b.throttleBatchSize = positiveInt(throttle.get("batch-size"), 1000);
        b.throttleConcurrency = positiveInt(throttle.get("concurrency"), 1);

        String batchTaskName = optionalString(root, "batch-task-name");
        if (batchTaskName == null || batchTaskName.isEmpty()) {
            int dot = b.entityName.lastIndexOf('.');
            batchTaskName = "orm-migration-" + (dot < 0 ? b.entityName : b.entityName.substring(dot + 1));
        }
        b.batchTaskName = batchTaskName;

        return b.build();
    }

    private static double ratio(Object value) {
        if (value == null)
            return 0;
        double d;
        try {
            d = Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw configInvalid("verify.sample-ratio must be a number in [0,1], got: " + value);
        }
        if (d < 0 || d > 1)
            throw configInvalid("verify.sample-ratio must be in [0,1], got: " + d);
        return d;
    }

    private static double nonNegativeDouble(Object value) {
        if (value == null)
            return 0;
        double d;
        try {
            d = Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw configInvalid("throttle.rate-limit must be a non-negative number, got: " + value);
        }
        if (d < 0)
            throw configInvalid("throttle.rate-limit must be >= 0, got: " + d);
        return d;
    }

    private static int positiveInt(Object value, int defaultValue) {
        if (value == null)
            return defaultValue;
        int i;
        try {
            i = Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw configInvalid("throttle value must be a positive integer, got: " + value);
        }
        if (i <= 0)
            throw configInvalid("throttle value must be > 0, got: " + i);
        return i;
    }

    private static long requirePositiveLong(Map<String, Object> section, String key, long defaultValue) {
        Object value = section.get(key);
        if (value == null)
            return defaultValue;
        long l;
        try {
            l = Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw configInvalid(key + " must be a positive integer, got: " + value);
        }
        if (l <= 0)
            throw configInvalid(key + " must be > 0, got: " + l);
        return l;
    }

    private static Map<String, Object> section(Map<String, Object> root, String name) {
        Object value = root.get(name);
        if (value == null)
            return Collections.emptyMap();
        if (!(value instanceof Map))
            throw configInvalid("property '" + name + "' must be a JSON object");
        return (Map<String, Object>) value;
    }

    private static String requireString(Map<String, Object> root, String name) {
        String value = optionalString(root, name);
        if (value == null || value.isEmpty())
            throw configInvalid("missing required property '" + name + "'");
        return value;
    }

    private static String requireSectionString(Map<String, Object> section, String leafKey) {
        String value = optionalString(section, leafKey);
        if (value == null)
            throw configInvalid("missing required property 'incremental." + leafKey + "'");
        return value;
    }

    private static String optionalString(Map<String, Object> map, String name) {
        Object value = map.get(name);
        return value == null ? null : value.toString();
    }

    private static List<String> stringList(Object value) {
        if (value == null)
            return Collections.emptyList();
        if (!(value instanceof List))
            throw configInvalid("'source-query-spaces' must be a JSON array");
        return (List<String>) value;
    }

    private static NopException configInvalid(String reason) {
        return new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON, reason);
    }

    public static Builder newBuilder(String scenario, String entityName) {
        Builder b = new Builder();
        b.scenario = scenario;
        b.entityName = entityName;
        b.sourceQuerySpaces = Collections.emptyList();
        return b;
    }

    public String getScenario() {
        return scenario;
    }

    public boolean isRescale() {
        return SCENARIO_RESCALE.equals(scenario);
    }

    public String getEntityName() {
        return entityName;
    }

    public List<String> getSourceQuerySpaces() {
        return sourceQuerySpaces;
    }

    /** rescale 场景的新 nop.orm.shard 配置对象（SWITCH 时原样序列化翻转）。 */
    public Map<String, Object> getTargetStrategy() {
        return targetStrategy;
    }

    /** SWITCH 时写回 {@code nop.orm.shard} 的 JSON 文本（整体原子翻转）。 */
    public String getTargetShardConfigText() {
        return targetStrategy == null ? null : JSON.stringify(targetStrategy);
    }

    public Map<String, String> getTargetQuerySpaceMapping() {
        return targetQuerySpaceMapping == null ? Collections.emptyMap() : targetQuerySpaceMapping;
    }

    public String getInitialWriteGate() {
        return initialWriteGate;
    }

    public boolean isIncrementalEnabled() {
        return incrementalEnabled;
    }

    public String getIncrementalConnectorName() {
        return incrementalConnectorName;
    }

    public String getIncrementalPositionP0() {
        return incrementalPositionP0;
    }

    /** CDC 连接参数（incremental.debezium 的原样键值，由编排器构造 DebeziumConfig 消费）。 */
    public Map<String, Object> getIncrementalDebezium() {
        return incrementalDebezium;
    }

    /** PREPARE 钉定后回填（可变：回填发生在配置对象生命周期内，不回写全局配置）。 */
    public void setIncrementalPositionP0(String positionP0) {
        this.incrementalPositionP0 = positionP0;
    }

    public long getQuiesceSeconds() {
        return quiesceSeconds;
    }

    public boolean isVerifyCount() {
        return verifyCount;
    }

    public double getVerifySampleRatio() {
        return verifySampleRatio;
    }

    public boolean isVerifyFullChecksum() {
        return verifyFullChecksum;
    }

    public double getThrottleRateLimit() {
        return throttleRateLimit;
    }

    public int getThrottleBatchSize() {
        return throttleBatchSize;
    }

    public int getThrottleConcurrency() {
        return throttleConcurrency;
    }

    public String getBatchTaskName() {
        return batchTaskName;
    }

    public static final class Builder {
        private String scenario;
        private String entityName;
        private List<String> sourceQuerySpaces = Collections.emptyList();
        private Map<String, Object> targetStrategy;
        private Map<String, String> targetQuerySpaceMapping;
        private String initialWriteGate;
        private boolean incrementalEnabled;
        private String incrementalConnectorName;
        private String incrementalPositionP0;
        private Map<String, Object> incrementalDebezium;
        private long quiesceSeconds = 30;
        private boolean verifyCount = true;
        private double verifySampleRatio;
        private boolean verifyFullChecksum;
        private double throttleRateLimit;
        private int throttleBatchSize = 1000;
        private int throttleConcurrency = 1;
        private String batchTaskName;

        public Builder sourceQuerySpaces(List<String> v) {
            this.sourceQuerySpaces = v;
            return this;
        }

        public Builder targetStrategy(Map<String, Object> v) {
            this.targetStrategy = v;
            return this;
        }

        public Builder targetQuerySpaceMapping(Map<String, String> v) {
            this.targetQuerySpaceMapping = v;
            return this;
        }

        public Builder incremental(String connectorName) {
            this.incrementalEnabled = true;
            this.incrementalConnectorName = connectorName;
            return this;
        }

        public Builder batchTaskName(String v) {
            this.batchTaskName = v;
            return this;
        }

        public Builder throttle(double rateLimit, int batchSize, int concurrency) {
            this.throttleRateLimit = rateLimit;
            this.throttleBatchSize = batchSize;
            this.throttleConcurrency = concurrency;
            return this;
        }

        public Builder verify(boolean count, double sampleRatio, boolean fullChecksum) {
            this.verifyCount = count;
            this.verifySampleRatio = sampleRatio;
            this.verifyFullChecksum = fullChecksum;
            return this;
        }

        public Builder quiesceSeconds(long quiesceSeconds) {
            if (quiesceSeconds <= 0)
                throw configInvalid("quiesce-seconds must be > 0");
            this.quiesceSeconds = quiesceSeconds;
            return this;
        }

        public MigrationJobConfig build() {
            if (scenario == null || entityName == null)
                throw configInvalid("scenario and entity are required");
            if (SCENARIO_RESCALE.equals(scenario) && (targetStrategy == null || targetStrategy.isEmpty()))
                throw configInvalid("scenario rescale requires target-strategy");
            if (SCENARIO_RELOCATE.equals(scenario)
                    && (targetQuerySpaceMapping == null || targetQuerySpaceMapping.isEmpty()))
                throw configInvalid("scenario relocate requires target-query-space-mapping");
            if (batchTaskName == null || batchTaskName.isEmpty()) {
                int dot = entityName.lastIndexOf('.');
                batchTaskName = "orm-migration-"
                        + (dot < 0 ? entityName : entityName.substring(dot + 1));
            }
            return new MigrationJobConfig(this);
        }
    }
}
