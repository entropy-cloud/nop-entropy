
package io.nop.metadata.service.entity;


import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.credential.api.ICredentialMigrationSupport;
import io.nop.credential.api.ICredentialProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.metadata.biz.INopMetaDataSourceBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.api.dto.CollectCatalogResultDTO;
import io.nop.metadata.api.dto.CollectCatalogTableDTO;
import io.nop.metadata.api.dto.ErrorDTO;
import io.nop.metadata.api.dto.SyncExternalTablesResultDTO;
import io.nop.metadata.api.dto.TestConnectionResultDTO;
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.catalog.CatalogTableStats;
import io.nop.metadata.service.catalog.MetaCatalogCollector;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.event.MetaModelChangedEventPublisher;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.sync.ExternalColumnInfo;
import io.nop.metadata.service.sync.ExternalTableInfo;
import io.nop.metadata.service.sync.ExternalTableStructureReader;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.metadata.service.NopMetadataException;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@BizModel("NopMetaDataSource")
public class NopMetaDataSourceBizModel extends CrudBizModel<NopMetaDataSource> implements INopMetaDataSourceBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaDataSourceBizModel.class);

    /** 外部表归属的系统模块 moduleId（架构基线 §2.5.1 方案 A）。由 syncExternalTables 惰性初始化。 */
    static final String EXTERNAL_MODULE_ID = "nop/meta-external";
    static final String EXTERNAL_MODULE_NAME = "meta-external";

    /** AR-14 / 维度09-11：collectCatalogForTable 找不到 metaTableId 时用专属 ErrorCode（原误用 NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND）。 */

    @Inject
    protected IMetaDataSourceConnectionProcessor connectionService;

    /** 元数据变更事件发布 helper（架构基线 §2.8 D2，IoC bean）。 */
    @Inject
    protected MetaModelChangedEventPublisher eventPublisher;

    /**
     * 凭证消费 SPI（W16-impl，可选装配，{@code @Nullable} → NopIoC optional）：部署不含
     * nop-credential 时为 null——bind/unbind/迁移动作此时显式拒绝（管理动作要求凭证库在场），
     * 读路径（buildDataSource）由 processor 侧 fail-closed 兜底。
     */
    protected ICredentialProvider credentialProvider;

    /**
     * 迁移支持 SPI（W16-impl SPI 增量裁定 2，可选装配）：批量迁移的反查/创建通道。
     */
    protected ICredentialMigrationSupport credentialMigrationSupport;

    /**
     * W16-impl Decision（draft review F6）：admin 判定 = 运行时角色校验（IUserContext roles 比对，
     * 角色集自持配置——复用 {@code nop.credential.admin-roles} 惯例的等价最小实现；
     * 禁止为 admin 判定引入 nop-auth 依赖边）。无登录态（后台/内部调用）放行——生产 GraphQL 入口
     * 由 action-auth 管角色，此处为第二层（与 nop-credential 写分级"无登录态内部调用放行"同口径）。
     */
    @InjectValue(value = "@cfg:nop.metadata.credential-admin-roles|admin,nop-admin")
    protected String credentialAdminRolesCsv = "admin,nop-admin";

    @Inject
    public void setCredentialProvider(@Nullable ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    @Inject
    public void setCredentialMigrationSupport(@Nullable ICredentialMigrationSupport credentialMigrationSupport) {
        this.credentialMigrationSupport = credentialMigrationSupport;
    }

    /** 事件 entityType（架构基线 §2.8 D3）。 */
    static final String EVENT_ENTITY_TYPE = "NopMetaDataSource";

    /** 外部表结构读取器（无状态，直接持有，与 OrmModelImporter 的持有方式一致）。 */
    private final ExternalTableStructureReader structureReader = new ExternalTableStructureReader();

    /** Catalog 运行时统计收集器（无状态，在 callback 内调用，不自建连接）。 */
    private final MetaCatalogCollector catalogCollector = new MetaCatalogCollector();

    /** 共享 table-reference 解析器（架构基线 §4.4.3 D3）。 */
    private final MetaTableReferenceResolver tableRefResolver = new MetaTableReferenceResolver(
            new MetaDataSourceResolver(), new MetaTableFieldResolver());

    /** 按 table-reference 形态分派 Connection 获取（§4.4.3 D1/D2）。延迟初始化（需 orm()）。 */
    private TableReferenceExecutor tableRefExecutor;

    public NopMetaDataSourceBizModel() {
        setEntityName(NopMetaDataSource.class.getName());
    }

    /**
     * 连通性验证：按 dataSourceId 加载 → 校验非 DISABLED → 调连接服务建连并读取 DatabaseMetaData。
     *
     * <p>设计决策 D1：
     * <ul>
     *   <li>实体不存在抛 {@code metadata.datasource-not-found}（不 NPE）</li>
     *   <li>DISABLED 抛 {@code metadata.datasource-disabled}（不静默通过）</li>
     *   <li>非 jdbc 类型抛 {@link NopException}（不静默返回成功）</li>
     *   <li>connectionConfig 缺必填字段抛 {@code metadata.datasource-config-invalid}（快速失败）</li>
     *   <li>建连失败（SQLException）catch 后返回 {@code {connected:false, error}}，不向上抛，
     *       使 GraphQL 调用方拿到结构化失败结果</li>
     * </ul>
     *
     * <p>设计决策 D3：成功时从 DatabaseMetaData 识别的产品名放入返回 Map，不写回任何 ORM 列。
     */
    @BizMutation
    public TestConnectionResultDTO testConnection(@Name("dataSourceId") String dataSourceId, IServiceContext context) {
        NopMetaDataSource dataSource = requireEntity(dataSourceId, "testConnection", context);

        String status = dataSource.getStatus();
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(status)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_DISABLED).param("dataSourceId", dataSourceId);
        }

        Map<String, Object> raw = connectionService.testConnect(dataSource.getDatasourceType(), dataSource.getConnectionConfig());
        TestConnectionResultDTO dto = new TestConnectionResultDTO();
        Object connected = raw.get("connected");
        dto.setConnected(connected instanceof Boolean && (Boolean) connected);
        Object productName = raw.get("databaseProductName");
        if (productName instanceof String) {
            dto.setDatabaseProductName((String) productName);
        }
        Object productVersion = raw.get("databaseProductVersion");
        if (productVersion instanceof String) {
            dto.setDatabaseProductVersion((String) productVersion);
        }
        Object error = raw.get("error");
        if (error instanceof String) {
            dto.setError((String) error);
        }
        return dto;
    }

    // ==================== W16-impl：数据源凭证管理动作对（bind/unbind/批量迁移/delete 钩子） ====================

    /** consumerRef 前缀（设计 §6.5：metadata:NopMetaDataSource:<dataSourceId>）。 */
    static final String CREDENTIAL_CONSUMER_REF_PREFIX = "metadata:NopMetaDataSource:";

    /** 迁移凭证类型（设计 §4.3：username 必填/password 可空 sensitive）。 */
    static final String MIGRATION_CREDENTIAL_TYPE = "jdbc-datasource";

    /** NopCredential.name 列宽（orm.xml CREDENTIAL_NAME precision=100）。 */
    static final int CREDENTIAL_NAME_MAX_LENGTH = 100;

    /**
     * admin 判定（Decision 项）：登录用户须命中 {@code nop.metadata.credential-admin-roles} 任一角色；
     * 无用户上下文（内部调用）放行（与 nop-credential 写分级同口径，生产入口另有 action-auth 层）。
     */
    void assertCredentialAdmin() {
        IUserContext userContext = IUserContext.get();
        if (userContext == null) {
            return; // 内部调用（无登录态）放行——action-auth 为生产第一层
        }
        java.util.Set<String> roles = new java.util.LinkedHashSet<>();
        if (credentialAdminRolesCsv != null) {
            for (String role : credentialAdminRolesCsv.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    roles.add(trimmed);
                }
            }
        }
        if (!roles.isEmpty() && userContext.isUserInAnyRole(roles)) {
            return;
        }
        throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_ADMIN_REQUIRED)
                .param("requiredRoles", String.join(",", roles));
    }

    /** 该数据源行的凭证 consumerRef（设计 §6.5 引用计数键规范）。 */
    static String credentialConsumerRef(String dataSourceId) {
        return CREDENTIAL_CONSUMER_REF_PREFIX + dataSourceId;
    }

    /**
     * 解析 connectionConfig JSON 为可变 map（非法 JSON = config-invalid，fail-closed 不静默）。
     */
    private Map<String, Object> parseConnectionConfigMap(NopMetaDataSource dataSource) {
        String config = dataSource.getConnectionConfig();
        if (config == null || config.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> map;
        try {
            map = JsonTool.parseMap(config);
        } catch (Exception e) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID, e)
                    .param("datasourceType", dataSource.getDatasourceType())
                    .param("reason", "connectionConfig is not valid JSON");
        }
        return map != null ? map : new LinkedHashMap<>();
    }

    /** 从 JSON map 取 credentialId（trim；空串/空白 → null）。 */
    private static String credentialIdOf(Map<String, Object> cfg) {
        Object value = cfg.get("credentialId");
        if (value == null) {
            return null;
        }
        String credentialId = value.toString().trim();
        return credentialId.isEmpty() ? null : credentialId;
    }

    /**
     * 确认凭证 SPI 装配在场（bind/unbind/迁移是管理动作，部署无凭证库时显式拒绝而非静默降级）。
     */
    private void requireCredentialSpi(String action) {
        if (credentialProvider == null || credentialMigrationSupport == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_PROVIDER_NOT_AVAILABLE)
                    .param("datasourceType", "jdbc")
                    .param("credentialId", action);
        }
    }

    /**
     * 绑定数据源凭证（admin）：connectionConfig 置 credentialId 键 + 清除 username/password 明文 +
     * registerUsage——<b>同一行级事务</b>（BizMutation 事务内，registerUsage 先行校验凭证存在/未软删，
     * 失败则整事务回滚、明文不清除——无"已引用但仍留明文"中间落盘态）。换绑 A→B = bind 内 unregister
     * 旧 + register 新；重复 bind 同一凭证幂等（registerUsage 幂等 + JSON 重写等值）。
     */
    @Description("绑定数据源凭证（置 credentialId 键 + 清除明文 + 登记引用，同一行级事务）")
    @BizMutation
    public Map<String, Object> bindCredential(@Name("dataSourceId") String dataSourceId,
                                              @Name("credentialId") String credentialId,
                                              IServiceContext context) {
        assertCredentialAdmin();
        requireCredentialSpi("bindCredential");
        if (StringHelper.isBlank(credentialId)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID)
                    .param("datasourceType", "jdbc").param("reason", "credentialId must not be blank");
        }
        String normalizedCredentialId = credentialId.trim();

        NopMetaDataSource dataSource = requireEntity(dataSourceId, "bindCredential", context);
        String consumerRef = credentialConsumerRef(dataSourceId);

        // 先行 registerUsage（幂等；前置校验凭证存在/未软删）——失败即中止，行未被触碰
        credentialProvider.registerUsage(normalizedCredentialId, consumerRef);

        Map<String, Object> cfg = parseConnectionConfigMap(dataSource);
        String oldCredentialId = credentialIdOf(cfg);
        if (oldCredentialId != null && !oldCredentialId.equals(normalizedCredentialId)) {
            // 换绑 A→B：unregister 旧 + register 新（§6.5）
            credentialProvider.unregisterUsage(oldCredentialId, consumerRef);
        }

        String beforeSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSourceId);
        cfg.put("credentialId", normalizedCredentialId);
        cfg.remove("username");   // 明文清除（unbind 不自动复活死值——需另行重录）
        cfg.remove("password");
        dataSource.setConnectionConfig(JsonTool.stringify(cfg));
        dao().updateEntity(dataSource);
        publishCredentialEvent(dataSource, beforeSnapshot, context);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataSourceId", dataSourceId);
        result.put("credentialId", normalizedCredentialId);
        return result;
    }

    /**
     * 解绑数据源凭证（admin）：清除 credentialId 键（回滚 = 引用级清除回静态路径，§6.2）+
     * unregisterUsage。明文不自动复活（死值不回填，需另行重录）——幂等（无键时 no-op）。
     */
    @Description("解绑数据源凭证（清除 credentialId 键 + 注销引用；明文需另行重录）")
    @BizMutation
    public Map<String, Object> unbindCredential(@Name("dataSourceId") String dataSourceId, IServiceContext context) {
        assertCredentialAdmin();
        requireCredentialSpi("unbindCredential");

        NopMetaDataSource dataSource = requireEntity(dataSourceId, "unbindCredential", context);
        Map<String, Object> cfg = parseConnectionConfigMap(dataSource);
        String oldCredentialId = credentialIdOf(cfg);
        if (oldCredentialId == null) {
            Map<String, Object> result = new LinkedHashMap<>(); // 幂等 no-op
            result.put("dataSourceId", dataSourceId);
            result.put("credentialId", null);
            return result;
        }

        String beforeSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSourceId);
        cfg.remove("credentialId");
        dataSource.setConnectionConfig(JsonTool.stringify(cfg));
        dao().updateEntity(dataSource);
        credentialProvider.unregisterUsage(oldCredentialId, credentialConsumerRef(dataSourceId));
        publishCredentialEvent(dataSource, beforeSnapshot, context);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataSourceId", dataSourceId);
        result.put("credentialId", null);
        return result;
    }

    /**
     * 实体变更事件发布（W16-impl Decision：审计载体 = MetaModelChangedEventPublisher 行级事件 +
     * 敏感列快照脱敏自动覆盖 connectionConfig + 凭证侧审计（registerUsage/unregisterUsage），
     * 不引入 IAuditService——nop-metadata 无该基建，设计 §6.4 审计行未要求）。
     */
    private void publishCredentialEvent(NopMetaDataSource dataSource, String beforeSnapshot,
                                        IServiceContext context) {
        String afterSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSource.getDataSourceId());
        eventPublisher.publishEventWithSnapshots(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED,
                EVENT_ENTITY_TYPE, dataSource.getDataSourceId(), dataSource.getName(),
                "credential-bind",
                beforeSnapshot, afterSnapshot,
                MetaModelChangedEventPublisher.newTransactionId(), context);
    }

    /**
     * 迁移用确定性凭证名：{@code jdbc-datasource:{querySpace}/{name}}；超 {@link #CREDENTIAL_NAME_MAX_LENGTH}
     * 时截断 + 短哈希后缀适配 VARCHAR(100)（name 无唯一约束可接受——身份由 consumerRef 唯一约束承载，
     * 哈希后缀仅为同名区分辅助）。
     */
    public static String deterministicCredentialName(String querySpace, String name) {
        String fullName = MIGRATION_CREDENTIAL_TYPE + ":" + (querySpace == null ? "" : querySpace)
                + "/" + (name == null ? "" : name);
        if (fullName.length() <= CREDENTIAL_NAME_MAX_LENGTH) {
            return fullName;
        }
        String hash = StringHelper.sha256Hash(fullName, "");
        if (hash == null || hash.length() < 8) {
            hash = String.valueOf(fullName.hashCode());
        }
        String prefix = fullName.substring(0, CREDENTIAL_NAME_MAX_LENGTH - 9);
        return prefix + "-" + hash.substring(0, 8);
    }

    /**
     * 批量迁移存量明文数据源行到凭证库（admin mutation，设计 §6.4 迁移工具面）：
     * 逐行 = 幂等反查（<b>主源 = findCredentialIdByConsumerRef（NopCredentialUsage 唯一约束）</b>；
     * 名称辅助 = 确定性名反查；反查命中软删凭证 → 该行计入失败清单供人工处置，不跳过不重建）→
     * 缺失则经迁移支持 SPI 创建凭证（username/password 取自 JSON；scope=system；加密复用
     * saveCredential 语义）→ registerUsage → JSON 置键并清除明文——<b>四步同一行级事务</b>
     * （REQUIRES_NEW per-row，{@link #upsertExternalTableGuarded} 先例；中断重跑经反查收敛，
     * 无孤儿/无重复凭证）。{@code orderBy dataSourceId} 确定性排序；单行失败收集到 failures
     * 不中断整批（该行事务独立回滚）。返回摘要（migrated/skipped/failed 计数 + 失败清单）。
     */
    @Description("批量迁移存量明文数据源凭证到凭证库（逐行幂等反查 + per-row 事务同事务清明文）")
    @BizMutation
    public Map<String, Object> migrateDataSourcesCredential(IServiceContext context) {
        assertCredentialAdmin();
        requireCredentialSpi("migrateDataSourcesCredential");

        QueryBean query = new QueryBean();
        query.addOrderField(NopMetaDataSource.PROP_NAME_dataSourceId, true); // 确定性排序（断点续跑收敛）
        List<NopMetaDataSource> rows = dao().findAllByQuery(query);

        int migrated = 0;
        int skipped = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (NopMetaDataSource row : rows) {
            try {
                if (Boolean.TRUE.equals(migrateOneRow(row, context))) {
                    migrated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                LOG.error("migrateDataSourcesCredential failed for dataSourceId={}", row.getDataSourceId(), e);
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("dataSourceId", row.getDataSourceId());
                failure.put("error", NopMetadataHelper.toErrorMessage(e));
                failures.add(failure);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("migratedCount", migrated);
        summary.put("skippedCount", skipped);
        summary.put("failedCount", failures.size());
        summary.put("failures", failures);
        return summary;
    }

    /**
     * 单行迁移（per-row REQUIRES_NEW 事务内四步：反查→创建→registerUsage→置键清明文）。
     *
     * <p>行级事务内<b>重读行</b>（外层加载的实体 attached 到外层会话；重读副本随本事务提交，
     * {@link #upsertExternalTableGuarded} 先例的 per-row 独立提交语义）。
     *
     * @return true = 本行已迁移；false = 跳过（已迁移/无明文可迁移/行已不存在）
     */
    private Boolean migrateOneRow(NopMetaDataSource row, IServiceContext context) {
        Map<String, Object> cfgOuter = parseConnectionConfigMap(row);
        if (credentialIdOf(cfgOuter) != null) {
            return Boolean.FALSE; // 已迁移（幂等跳过）
        }
        if (!cfgOuter.containsKey("username") && !cfgOuter.containsKey("password")) {
            return Boolean.FALSE; // 无明文凭据可迁移（如仅 http 类型占位行）
        }

        ITransactionTemplate txnTemplate = orm().getSessionFactory().txn();
        return txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRES_NEW, (ITransaction txn) -> {
            NopMetaDataSource fresh = dao().getEntityById(row.getDataSourceId());
            if (fresh == null) {
                return Boolean.FALSE; // 行在迁移过程中被删除（外层循环快照陈旧）
            }
            Map<String, Object> cfg = parseConnectionConfigMap(fresh);
            if (credentialIdOf(cfg) != null) {
                return Boolean.FALSE; // 双检：并发迁移已处理
            }
            boolean hasUsername = cfg.containsKey("username");
            boolean hasPassword = cfg.containsKey("password");
            if (!hasUsername && !hasPassword) {
                return Boolean.FALSE;
            }
            String username = hasUsername && cfg.get("username") != null ? cfg.get("username").toString() : null;
            if (StringHelper.isBlank(username)) {
                // username 必填（对齐 buildDataSource requireNonBlank 现状语义——该行本就运行时失败）
                throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID)
                        .param("datasourceType", String.valueOf(fresh.getDatasourceType()))
                        .param("reason", "username missing/blank: row violates jdbc-datasource required field");
            }
            String password = hasPassword && cfg.get("password") != null ? cfg.get("password").toString() : "";

            String consumerRef = credentialConsumerRef(fresh.getDataSourceId());

            // (1) 幂等反查——主源 consumerRef（唯一约束承载身份）
            io.nop.credential.api.CredentialLookup byRef =
                    credentialMigrationSupport.findCredentialIdByConsumerRef(consumerRef);
            String credentialId;
            if (byRef != null) {
                if (byRef.isDeleted()) {
                    // 软删命中 → 该行失败供人工处置（不跳过不重建，避免同 consumerRef 双凭证歧义）
                    throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_RESOLVE_FAILED)
                            .param("credentialId", byRef.getCredentialId())
                            .param("reason", "referenced credential is soft-deleted; manual handling required");
                }
                credentialId = byRef.getCredentialId();
            } else {
                // 名称辅助反查（活跃行；name 无唯一约束，身份由 consumerRef 承载）
                String deterministicName = deterministicCredentialName(fresh.getQuerySpace(), fresh.getName());
                io.nop.credential.api.CredentialLookup byName =
                        credentialMigrationSupport.findCredentialByName(MIGRATION_CREDENTIAL_TYPE, deterministicName);
                if (byName != null) {
                    credentialId = byName.getCredentialId();
                } else {
                    // (2) 创建凭证（saveCredential 语义：加密 + scope=system）
                    Map<String, Object> fields = new LinkedHashMap<>();
                    fields.put("username", username);
                    fields.put("password", password);
                    credentialId = credentialMigrationSupport.createCredential(
                            MIGRATION_CREDENTIAL_TYPE, deterministicName, fields);
                }
            }

            // (3) registerUsage（幂等；前置校验凭证存在/未软删）
            credentialProvider.registerUsage(credentialId, consumerRef);

            // (4) JSON 置键 + 清除明文——与前三步同一行级事务（无中间落盘态）
            String beforeSnapshot = eventPublisher.buildSnapshot(fresh, EVENT_ENTITY_TYPE, fresh.getDataSourceId());
            cfg.put("credentialId", credentialId);
            cfg.remove("username");
            cfg.remove("password");
            fresh.setConnectionConfig(JsonTool.stringify(cfg));
            dao().updateEntity(fresh);
            publishCredentialEvent(fresh, beforeSnapshot, context);
            return Boolean.TRUE;
        });
    }

    /**
     * 覆盖标准 {@code delete}：删除前读取行内 credentialId，删除成功后注销引用计数
     * （{@code metadata:NopMetaDataSource:<dataSourceId>}）——防止 usage 行残留导致凭证删除被
     * 引用计数拦截永久拒绝（NopAiModelBizModel live 先例）。
     */
    @Description("@i18n:biz.delete|根据主键删除指定对象")
    @BizMutation
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaDataSource existing = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (deleted && existing != null) {
            unregisterCredentialUsageQuietly(existing);
        }
        return deleted;
    }

    /**
     * 覆盖 {@code deleteByQuery}：基类路径经 {@code doDeleteByQuery → doDeleteMulti → doDelete}
     * <b>不经过</b>本类覆盖的 {@code delete}（虚分派不发生）——先收集命中行，删除后逐行注销
     * （NopAiModelBizModel 先例钉定的批量路径坑位：漏覆写即 usage 引用残留）。
     */
    @Description("根据查询条件获取一批实体数据，然后删除这些实体")
    @BizMutation
    @Override
    public int deleteByQuery(@Name("query") QueryBean query, IServiceContext context) {
        List<NopMetaDataSource> hits = findList(query, null, context);
        int deleted = super.deleteByQuery(query, context);
        for (NopMetaDataSource hit : hits) {
            unregisterCredentialUsageQuietly(hit);
        }
        return deleted;
    }

    /**
     * 注销单行凭证引用（包私有以便测试覆盖）：provider 未部署或行内无 credentialId 时静默跳过
     * （无凭证可注销，非缺陷路径）；unregisterUsage 按 (credentialId, consumerRef) 删行、天然幂等。
     */
    void unregisterCredentialUsageQuietly(NopMetaDataSource dataSource) {
        if (credentialProvider == null) {
            return;
        }
        String credentialId = credentialIdOf(parseConnectionConfigMap(dataSource));
        if (StringHelper.isBlank(credentialId)) {
            return;
        }
        credentialProvider.unregisterUsage(credentialId, credentialConsumerRef(dataSource.getDataSourceId()));
    }

    /**
     * 外部表元数据同步：从已注册数据源扫描物理表结构 → 写入元数据目录。
     *
     * <p>设计决策 D2（架构基线 §2.5.1）：
     * <ul>
     *   <li>实体不存在抛 {@code metadata.datasource-not-found}（不 NPE）</li>
     *   <li>DISABLED 抛 {@code metadata.datasource-disabled}（不静默通过）</li>
     *   <li>非 jdbc 类型由连接服务抛 {@link NopException}（不静默成功）</li>
     *   <li>不支持的方言由读取器抛 {@link NopException}（不静默跳过）</li>
     *   <li>复用 P2-1 callback 式连接服务 {@code withConnection}：callback 内运行时取方言 + 扫描，
     *       callback 结束自动释放外部连接（本方法不自建连接）</li>
     *   <li>按 D1 方案 A 写入：tableType=external，metaModuleId 指向系统模块 nop/meta-external，
     *       列结构序列化为 JSON 存入 buildSql（子方案 A2）</li>
     *   <li>幂等 upsert：按 (metaModuleId, schema, tableName) 复合键去重（plan 0852-3 收敛自
     *       {@code (metaModuleId, tableName)}），同名不同 schema 不再互相覆盖；重复同步更新而非追加</li>
     *   <li>NULL-schema 并发防护（AR-07，plan-2026-08-05-2157-3）：每表 upsert 在 per-key 锁 +
     *       {@code REQUIRES_NEW} 独立事务内执行（锁跨 find→insert→flush→commit），并发同键双插
     *       收敛为 update，不产生静默重复行（详见 {@link #upsertExternalTableGuarded}）</li>
     *   <li>单表失败收集到 errors 不中断整批（flushSession 隔离 + clearSession 清理失败态）</li>
     *   <li><b>部分持久化契约（AR-17，R8.4b）</b>：每表 upsert 在 per-key 锁 + REQUIRES_NEW 独立事务内
     *       独立提交——scan 中途失败/单表失败时<b>已同步表保持持久化</b>（非全量原子，显式契约而非漂移）；
     *       scan 级失败（structureReader.read 抛 / 连接中断）异常向上传播（fail-loud）且<b>失败路径仍发布
     *       事件</b>——事件发布经 REQUIRES_NEW 独立事务存活（不随外层回滚消失），下游可追踪
     *       "sync 尝试发生 + 已部分持久化"</li>
     * </ul>
     *
     * @param dataSourceId  目标数据源 ID
     * @param schemaPattern 可选，限定扫描的 schema（null/空串表示全部）
     * @return {@code {syncedTableCount: int, errors: [{tableName, error}, ...]}}
     */
    @BizMutation
    public SyncExternalTablesResultDTO syncExternalTables(@Name("dataSourceId") String dataSourceId,
                                                           @Optional @Name("schemaPattern") String schemaPattern,
                                                           IServiceContext context) {
        NopMetaDataSource dataSource = requireEntity(dataSourceId, "syncExternalTables", context);

        String status = dataSource.getStatus();
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(status)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_DISABLED).param("dataSourceId", dataSourceId);
        }

        String externalModuleId = ensureExternalSystemModule();

        String beforeSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSourceId);

        AtomicInteger syncedCount = new AtomicInteger(0);
        List<ErrorDTO> errors = new ArrayList<>();

        try {
            connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                    (Connection conn, DatabaseMetaData metaData) -> {
                        List<ExternalTableInfo> tables = structureReader.read(conn, metaData, schemaPattern);
                        for (ExternalTableInfo table : tables) {
                            try {
                                upsertExternalTableGuarded(externalModuleId, dataSource, table);
                                syncedCount.incrementAndGet();
                            } catch (Exception e) {
                                LOG.error("syncExternalTables failed for table: {}", table.getTableName(), e);
                                ErrorDTO errDTO = new ErrorDTO();
                                errDTO.setCode(table.getTableName());
                                errDTO.setMessage(NopMetadataHelper.toErrorMessage(e));
                                errors.add(errDTO);
                                orm().clearSession();
                            }
                        }
                    });

            orm().flushSession();
        } catch (RuntimeException e) {
            // AR-17（R8.4b）：scan 级失败（structureReader.read 抛 / 连接中断）——try/catch 包住整个
            // withConnection 块（界定范围 = 连接中断与扫描失败都走此面）。已同步表已经 per-table
            // REQUIRES_NEW 独立提交持久化（部分持久化契约），事件行不能随之消失：在 REQUIRES_NEW
            // 独立事务中发布事件（沿 upsertExternalTableGuarded 先例），否则重抛使外层事务回滚、
            // 事件行随之消失（空壳修复）。事件发布自身失败不掩盖原始异常（LOG.error 留证后仍重抛）。
            publishSyncScanFailureEvent(dataSource, dataSourceId, beforeSnapshot, context, e);
            throw e;
        }

        String afterSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSourceId);
        eventPublisher.publishEventWithSnapshots(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED,
                EVENT_ENTITY_TYPE, dataSourceId, dataSource.getName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_SYNC,
                beforeSnapshot, afterSnapshot,
                MetaModelChangedEventPublisher.newTransactionId(), context);

        SyncExternalTablesResultDTO result = new SyncExternalTablesResultDTO();
        result.setSyncedTableCount(syncedCount.get());
        result.setErrors(errors);
        return result;
    }

    /**
     * AR-17（R8.4b）：scan 级失败路径的事件发布——REQUIRES_NEW 独立事务（沿
     * {@link #upsertExternalTableGuarded} :512-517 先例），使事件行在重抛导致的外层事务回滚后仍存活
     * （下游可追踪"sync 尝试发生 + 已部分持久化"）。快照语义：dataSource 实体在 sync 期间不变，
     * before/after 等同——事件价值是 sync 尝试的下游通知，不是实体 diff。
     *
     * <p>事件发布自身失败（罕见）不掩盖原始异常：LOG.error 留证后仍由调用方重抛原异常（fail-loud）。
     */
    private void publishSyncScanFailureEvent(NopMetaDataSource dataSource, String dataSourceId,
                                             String beforeSnapshot, IServiceContext context,
                                             RuntimeException cause) {
        try {
            ITransactionTemplate txnTemplate = orm().getSessionFactory().txn();
            txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRES_NEW, (ITransaction txn) -> {
                eventPublisher.publishEventWithSnapshots(
                        _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED,
                        EVENT_ENTITY_TYPE, dataSourceId, dataSource.getName(),
                        MetaModelChangedEventPublisher.CHANGE_SOURCE_SYNC,
                        beforeSnapshot, beforeSnapshot,
                        MetaModelChangedEventPublisher.newTransactionId(), context);
                return null;
            });
        } catch (Exception e) {
            LOG.error("publish sync scan failure event failed: dataSourceId={}", dataSourceId, e);
        }
    }

    /**
     * Catalog 运行时统计收集：从已注册数据源收集该 querySpace 下 external 表的物理运行时统计
     * （行数/索引/…），每次收集追加为新的时序快照行写入 NopMetaCatalog。
     *
     * <p>设计决策 D2（架构基线 §2.3.2 / 设计 05 §4.6）：
     * <ul>
     *   <li>实体不存在抛 {@code metadata.datasource-not-found}（不 NPE）</li>
     *   <li>DISABLED 抛 {@code metadata.datasource-disabled}（不静默通过）</li>
     *   <li>非 jdbc 类型由连接服务抛 {@link NopException}（不静默成功）</li>
     *   <li>复用 P2-1 callback 式连接服务 {@code withConnection}：callback 内运行时取方言 + 逐表收集，
     *       callback 结束自动释放外部连接（本方法不自建连接）</li>
     *   <li>统计不可用（sizeBytes/partitionCount/lastModified 等方言特定）记 null +
     *       {@code details.unavailable} 显式标记（不静默跳过整行、不伪造 0）</li>
     *   <li>时序语义：每表每收集一次追加新行（collectedAt=now），不覆盖旧行</li>
     *   <li>单表失败（SQL 异常）收集到 errors 不中断整批（flushSession 隔离 + clearSession 清理失败态）</li>
     * </ul>
     *
     * @param dataSourceId  目标数据源 ID
     * @param schemaPattern 可选，限定 COUNT/索引查询的物理 schema（null/空串表示**逐表默认解析**：
     *                      各表取自身 {@code NopMetaTable.schema}，plan 0852-3 Phase 3）。
     *                      显式入参对该批次所有表覆盖默认；null=各表用持久化 schema（仍 null 则不过滤）。
     * @return {@code {collectedCount: int, errors: [{tableName, error}, ...]}}
     */
    @BizMutation
    public CollectCatalogResultDTO collectCatalog(@Name("dataSourceId") String dataSourceId,
                                                   @Optional @Name("schemaPattern") String schemaPattern,
                                                   IServiceContext context) {
        NopMetaDataSource dataSource = requireEntity(dataSourceId, "collectCatalog", context);

        String status = dataSource.getStatus();
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(status)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_DISABLED).param("dataSourceId", dataSourceId);
        }

        List<NopMetaTable> externalTables = findExternalTables(dataSource.getQuerySpace());

        AtomicInteger collectedCount = new AtomicInteger(0);
        List<CollectCatalogTableDTO> tables = new ArrayList<>();
        List<ErrorDTO> errors = new ArrayList<>();

        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String productName = safeProductName(metaData);
                    for (NopMetaTable table : externalTables) {
                        try {
                            TableReference ref = new TableReference(TableReference.Kind.EXTERNAL,
                                    table.getMetaTableId(), table.getTableName(), null,
                                    dataSource, null, null, null);
                            String effectiveSchema = resolveDefaultSchema(schemaPattern, table);
                            CatalogTableStats stats = catalogCollector.collectForTable(
                                    conn, metaData, ref, effectiveSchema, productName);
                            appendCatalogRow(table.getMetaTableId(), stats);
                            CollectCatalogTableDTO tableDTO = new CollectCatalogTableDTO();
                            tableDTO.setTableName(table.getTableName());
                            tableDTO.setMetaSchema(table.getMetaSchema());
                            tableDTO.setTableType(table.getTableType());
                            tableDTO.setRowCount(stats.getRowCount());
                            tableDTO.setSizeBytes(stats.getSizeBytes());
                            tables.add(tableDTO);
                            orm().flushSession();
                            collectedCount.incrementAndGet();
                        } catch (Exception e) {
                            LOG.error("collectCatalog failed for table: {}", table.getTableName(), e);
                            ErrorDTO errDTO = new ErrorDTO();
                            errDTO.setCode(table.getTableName());
                            errDTO.setMessage(NopMetadataHelper.toErrorMessage(e));
                            errors.add(errDTO);
                            orm().clearSession();
                        }
                    }
                });

        CollectCatalogResultDTO result = new CollectCatalogResultDTO();
        result.setTableCount(collectedCount.get());
        result.setTables(tables);
        result.setErrors(errors);
        return result;
    }

    /** 查找该 querySpace 下所有 external 类型逻辑表（按 tableType=external 限定）。 */
    private List<NopMetaTable> findExternalTables(String querySpace) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_querySpace, querySpace));
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableType,
                _NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL));
        return tableDao.findAllByQuery(query);
    }

    /**
     * 单表 Catalog 收集入口（架构基线 §4.4.3 D1-D5）：对任意 tableType（external/entity/sql）的逻辑表
     * 收集运行时统计（行数/索引），追加为新的时序快照行写入 NopMetaCatalog。
     *
     * <p>解析路径（D3）：metaTableId → NopMetaTable → {@link MetaTableReferenceResolver} → {@link TableReference}
     * → {@link TableReferenceExecutor} 按 ref 形态分派 Connection → 收集器收集 → 追加 NopMetaCatalog 行。
     *
     * @param metaTableId   目标逻辑表 ID（任意 tableType）
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema；sql 子查询忽略）
     * @param context       服务上下文
     * @return {@code {metaTableId, rowCount, indexCount, unavailable:[...]}}
     */
    @BizMutation
    public CollectCatalogResultDTO collectCatalogForTable(@Name("metaTableId") String metaTableId,
                                                           @Optional @Name("schemaPattern") String schemaPattern,
                                                           IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        TableReference ref = tableRefResolver.resolve(table,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm());

        String effectiveSchema = resolveDefaultSchema(schemaPattern, table);

        CatalogTableStats stats = ensureTableRefExecutor().execute(ref,
                (conn, metaData, productName) -> catalogCollector.collectForTable(
                        conn, metaData, ref, effectiveSchema, productName));

        appendCatalogRow(table.getMetaTableId(), stats);

        CollectCatalogResultDTO result = new CollectCatalogResultDTO();
        result.setTableCount(1);
        CollectCatalogTableDTO tableDTO = new CollectCatalogTableDTO();
        tableDTO.setTableName(table.getTableName());
        tableDTO.setMetaSchema(table.getMetaSchema());
        tableDTO.setTableType(table.getTableType());
        tableDTO.setRowCount(stats.getRowCount());
        tableDTO.setSizeBytes(stats.getSizeBytes());
        List<CollectCatalogTableDTO> tables = new ArrayList<>();
        tables.add(tableDTO);
        result.setTables(tables);
        return result;
    }

    /**
     * 默认 schema 解析（plan 0852-3 Phase 3）：未显式传 schemaPattern（null/空/纯空白）且
     * {@code table.schema} 非空 → 默认取 {@code table.schema}；否则维持入参（可能为 null=不过滤）。
     *
     * <p>解析点在 BizModel 层（持有 NopMetaTable），执行器仅收最终 schemaPattern——
     * 使「sync 持久化一次 schema → 后续 Catalog/Quality/Profiling 执行无需重传」成立。
     * 显式入参优先覆盖持久化 schema。
     */
    private static String resolveDefaultSchema(String schemaPattern, NopMetaTable table) {
        if (schemaPattern != null && !schemaPattern.trim().isEmpty()) {
            return schemaPattern;
        }
        return table.getMetaSchema();
    }

    /** 延迟初始化 TableReferenceExecutor（需 orm()，构造时 orm 不可用）。 */
    private TableReferenceExecutor ensureTableRefExecutor() {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
        }
        return tableRefExecutor;
    }

    /**
     * 将单表收集结果追加为一行新的 NopMetaCatalog 快照（时序语义：collectedAt=now，不覆盖旧行）。
     * details JSON 承载 unavailable 标记 + 方言特定字段。
     */
    private void appendCatalogRow(String metaTableId, CatalogTableStats stats) {
        IEntityDao<NopMetaCatalog> catalogDao = daoFor(NopMetaCatalog.class);
        NopMetaCatalog row = catalogDao.newEntity();
        row.setMetaTableId(metaTableId);
        row.setRowCount(stats.getRowCount());
        row.setSizeBytes(stats.getSizeBytes());
        row.setIndexCount(stats.getIndexCount());
        row.setPartitionCount(stats.getPartitionCount());
        row.setLastModified(stats.getLastModified());
        row.setCollectedAt(CoreMetrics.currentTimestamp());
        row.setDetails(buildDetailsJson(stats));
        catalogDao.saveEntity(row);
    }

    /** details JSON：{unavailable: [...], databaseProductName: ...}，承载不可用标记 + 方言特定字段。 */
    private String buildDetailsJson(CatalogTableStats stats) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (!stats.getUnavailable().isEmpty()) {
            details.put("unavailable", stats.getUnavailable());
        }
        if (!stats.getExtras().isEmpty()) {
            details.putAll(stats.getExtras());
        }
        return JsonTool.stringify(details);
    }

    private static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            LOG.warn("getDatabaseProductName failed, product name will be absent from details", e);
            return null;
        }
    }

    /**
     * 幂等 upsert：按 (metaModuleId, schema, tableName) 复合键去重（plan 2026-07-17-0852-3 收敛自
     * {@code (metaModuleId, tableName)}，使同一数据源下不同 schema 的同名表可区分、互不覆盖）。
     * 存在则更新（querySpace/description/buildSql/schema 列快照），不存在则新建。
     *
     * <p><b>并发防护（AR-07）</b>：本方法不自行加锁/提交——调用方必须经
     * {@link #upsertExternalTableGuarded}（per-key 锁 + REQUIRES_NEW 独立事务提交）进入，
     * 否则 NULL-schema 并发双插会绕过 4 列 UK（NULL≠NULL）产生静默重复行。
     *
     * <p><b>跨数据源行为（Decision，plan 0852-3 Phase 2）</b>：去重键仍**不含 querySpace**——
     * 跨数据源、同名同 schema 的表会互相覆盖（与 1905-1 收敛前语义一致）。仅 schema 维度被纳入，
     * 使「同数据源不同 schema 同名表」可区分；「跨数据源同名同 schema」的 querySpace 维度纳入属
     * follow-up（非阻塞理由：应用层 upsert 仍幂等，迁移需评估跨数据源覆盖语义破坏面）。
     *
     * <p><b>实现说明（EQL 关键字规避）</b>：{@code SCHEMA} 在 EQL 文法中是 reserved keyword，
     * {@code o.schema=?} 解析失败。故先按 EQL-safe 的 {@code (metaModuleId, tableName)} 拉候选集，
     * 再在 Java 层按 schema 精确匹配（{@code null==null}）。schema=null 用 {@link #normalizeSchemaForMatch}
     * 归一为 null，使「无 schema」与「无 schema」匹配。
     */
    private void upsertExternalTable(String metaModuleId, NopMetaDataSource dataSource, ExternalTableInfo info) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);

        // EQL-safe 查询：仅按 (metaModuleId, tableName) 拉候选集（schema 维度在 Java 层过滤）
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_metaModuleId, metaModuleId));
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, info.getTableName()));
        List<NopMetaTable> candidates = tableDao.findAllByQuery(query);

        // Java 层 schema 精确匹配（normalizeSchemaForMatch 将 null/空串归一为 null，使 null==null 成立）
        String infoSchema = normalizeSchemaForMatch(info.getSchema());
        NopMetaTable table = null;
        for (NopMetaTable candidate : candidates) {
            if (java.util.Objects.equals(normalizeSchemaForMatch(candidate.getMetaSchema()), infoSchema)) {
                table = candidate;
                break;
            }
        }

        String columnsJson = serializeColumns(info.getColumns());

        if (table == null) {
            table = tableDao.newEntity();
            table.setMetaModuleId(metaModuleId);
            table.setIsDelta((byte) 0);
            table.setTableName(info.getTableName());
            table.setMetaSchema(infoSchema);
            table.setDisplayName(info.getTableName());
            table.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL);
            table.setQuerySpace(dataSource.getQuerySpace());
            table.setDescription(info.getRemark());
            table.setBuildSql(columnsJson);
            tableDao.saveEntity(table);
        } else {
            table.setMetaSchema(infoSchema);
            table.setQuerySpace(dataSource.getQuerySpace());
            table.setDescription(info.getRemark());
            table.setBuildSql(columnsJson);
            tableDao.updateEntity(table);
        }
    }

    /** schema 归一化用于匹配：null/空串/纯空白 → null（使「无 schema」行互相匹配）。 */
    private static String normalizeSchemaForMatch(String schema) {
        if (schema == null || schema.trim().isEmpty()) {
            return null;
        }
        return schema;
    }

    /**
     * 单实例 per-key 锁（路径 C'，plan-2026-08-05-2157-3 Phase 1 裁定）：按
     * {@code (metaModuleId, tableName, normalizedSchema)} 键持锁，锁覆盖 find→insert/update→flush→commit。
     *
     * <p><b>为什么必须跨 commit</b>（执行期裁定，路径 C 调整记录）：{@code syncExternalTables} 的整体事务
     * 由框架在 BizModel 返回后提交（commit 在方法之外）；若锁只覆盖 find→insert→flush（路径 C 原样），
     * 后到线程在独立会话（READ_COMMITTED）的 find 无法看见先到线程未提交的行——竞态保留。故每表 upsert
     * 包在 {@link TransactionPropagation#REQUIRES_NEW} 独立事务中，锁内 flush + commit，后到线程的 find
     * 才能看见先到线程已提交的行并收敛为 update（并发失败方不报错、不追加、不静默跳过）。
     *
     * <p>key 集合有界（受元数据规模约束），长期持有可接受；单实例 baseline（R4.3 已裁定），
     * 多实例残余面见 plan Deferred But Adjudicated 段。
     */
    private static final Map<String, Object> EXTERNAL_TABLE_UPSERT_LOCKS = new ConcurrentHashMap<>();

    private static String tableLockKey(String metaModuleId, String tableName, String normalizedSchema) {
        return metaModuleId + '\u0000' + tableName + '\u0000' + normalizedSchema;
    }

    /** per-key 锁内执行单表 upsert + flush + 独立事务提交；失败向上抛（由调用方收集到 errors[]）。 */
    private void upsertExternalTableGuarded(String metaModuleId, NopMetaDataSource dataSource, ExternalTableInfo info) {
        String lockKey = tableLockKey(metaModuleId, info.getTableName(), normalizeSchemaForMatch(info.getSchema()));
        Object lock = EXTERNAL_TABLE_UPSERT_LOCKS.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            ITransactionTemplate txnTemplate = orm().getSessionFactory().txn();
            txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRES_NEW, (ITransaction txn) -> {
                upsertExternalTable(metaModuleId, dataSource, info);
                orm().flushSession();
                return null;
            });
        }
    }

    /**
     * 确保外部表归属的系统模块存在（moduleId=nop/meta-external，status=RELEASED）。
     * 已存在则复用其 metaModuleId，不存在则惰性创建。
     */
    private String ensureExternalSystemModule() {
        IEntityDao<NopMetaModule> moduleDao = daoFor(NopMetaModule.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, EXTERNAL_MODULE_ID));
        NopMetaModule module = moduleDao.findFirstByQuery(query);
        if (module != null) {
            return module.getMetaModuleId();
        }

        module = moduleDao.newEntity();
        module.setModuleId(EXTERNAL_MODULE_ID);
        module.setModuleName(EXTERNAL_MODULE_NAME);
        module.setDisplayName("外部表系统模块");
        module.setModuleVersion(1L);
        module.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        module.setImportedAt(CoreMetrics.currentTimestamp());
        moduleDao.saveEntity(module);
        orm().flushSession();
        return module.getMetaModuleId();
    }

    /** 将扫描到的列结构序列化为 JSON 数组存入 buildSql（子方案 A2）。 */
    private String serializeColumns(List<ExternalColumnInfo> columns) {
        List<Map<String, Object>> list = new ArrayList<>(columns.size());
        for (ExternalColumnInfo col : columns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("columnName", col.getColumnName());
            m.put("dataType", col.getDataType());
            m.put("precision", col.getPrecision());
            m.put("scale", col.getScale());
            m.put("nullable", col.isNullable());
            m.put("ordinal", col.getOrdinal());
            if (col.getRemark() != null) {
                m.put("comment", col.getRemark());
            }
            if (col.getDefaultValue() != null) {
                m.put("defaultValue", col.getDefaultValue());
            }
            list.add(m);
        }
        return JsonTool.stringify(list);
    }

}
