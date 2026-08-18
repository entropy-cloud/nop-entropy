package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.CredentialLookup;
import io.nop.credential.api.ICredentialMigrationSupport;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.service.entity.NopMetaDataSourceBizModel;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W16-impl：NopMetaDataSourceBizModel 凭证管理动作对（bind/unbind/批量迁移/delete 双路径钩子）
 * 的容器级测试（H2 localDb）。测试装配声明（Phase 2 前置）：经手写 fake 双 SPI
 * （provider + migration support，共享凭证/引用状态——api-only compile 门不受影响）setter 注入
 * 容器 BizModel 实例，模拟 nop-credential-service 运行时语义（registerUsage 前置校验、
 * consumerRef 幂等、软删 fail-closed）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataSourceBizModelCredential extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopMetaDataSourceBizModel dataSourceBizModel;

    @Inject
    IOrmTemplate ormTemplate;

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutationData(GraphQLResponseBean response, String field) {
        assertFalse(response.hasError(), "mutation must succeed, errors=" + response.getErrors());
        Object root = response.getData();
        assertNotNull(root, "mutation data must be present");
        Object data = ((Map<String, Object>) root).get(field);
        assertNotNull(data, "mutation data must be present for " + field);
        return (Map<String, Object>) data;
    }

    /**
     * fake 凭证库（双 SPI 共享状态）：凭证表 + usage 表的内存模型，语义对齐生产实现——
     * registerUsage 前置校验（不存在/软删抛错）+ 幂等；createCredential 生成递增 id；
     * findCredentialIdByConsumerRef 命中软删返回 deleted=true。
     */
    static class FakeCredentialVault implements ICredentialProvider, ICredentialMigrationSupport {
        static class FakeCredential {
            final String credentialId;
            final String typeName;
            final String name;
            boolean deleted;

            FakeCredential(String credentialId, String typeName, String name) {
                this.credentialId = credentialId;
                this.typeName = typeName;
                this.name = name;
            }
        }

        final Map<String, FakeCredential> credentials = new LinkedHashMap<>();
        final Map<String, String> usages = new LinkedHashMap<>(); // consumerRef -> credentialId
        final List<String> createdCredentialIds = new ArrayList<>();
        final AtomicInteger idSeq = new AtomicInteger();
        final List<String> registerFailOnConsumerRef = new ArrayList<>(); // 注入失败（模拟中断，按 consumerRef）

        String createCredential(String typeName, String name, boolean deleted) {
            String id = "fake-cred-" + idSeq.incrementAndGet();
            credentials.put(id, new FakeCredential(id, typeName, name));
            credentials.get(id).deleted = deleted;
            return id;
        }

        @Override
        public CredentialData getCredential(String credentialId) {
            return null; // 本测试不经 buildDataSource 消费路径
        }

        @Override
        public Object getCredentialData(String credentialId, String field) {
            return null;
        }

        @Override
        public TestResult testCredential(String credentialId) {
            throw new UnsupportedOperationException("not used in test");
        }

        @Override
        public MaskedCredential mask(String credentialId) {
            throw new UnsupportedOperationException("not used in test");
        }

        @Override
        public void registerUsage(String credentialId, String consumerRef) {
            FakeCredential credential = credentials.get(credentialId);
            if (credential == null) {
                throw new NopException(NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND); // 模拟 NOT_FOUND
            }
            if (credential.deleted) {
                throw new NopException(NopMetadataErrors.ERR_DATASOURCE_DISABLED); // 模拟 DELETED
            }
            if (registerFailOnConsumerRef.contains(consumerRef)) {
                throw new NopException(NopMetadataErrors.ERR_DATASOURCE_CONNECT_FAILED); // 注入中断
            }
            usages.put(consumerRef, credentialId); // (credentialId, consumerRef) 唯一 → 幂等覆盖
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
            usages.remove(consumerRef, credentialId);
        }

        @Override
        public CredentialLookup findCredentialByName(String typeName, String name) {
            for (FakeCredential credential : credentials.values()) {
                if (!credential.deleted && credential.typeName.equals(typeName)
                        && credential.name.equals(name)) {
                    return new CredentialLookup(credential.credentialId, credential.typeName, false);
                }
            }
            return null;
        }

        @Override
        public CredentialLookup findCredentialIdByConsumerRef(String consumerRef) {
            String credentialId = usages.get(consumerRef);
            if (credentialId == null) {
                return null;
            }
            FakeCredential credential = credentials.get(credentialId);
            return new CredentialLookup(credentialId, credential.typeName, credential.deleted);
        }

        @Override
        public String createCredential(String typeName, String name, Map<String, Object> fields) {
            String id = createCredential(typeName, name, false);
            createdCredentialIds.add(id);
            return id;
        }
    }

    private FakeCredentialVault vault;

    // 简化用户上下文（admin 判定负例用；对齐 nop-credential 测试 RoleUserContext 形态）
    static final class RoleUserContext implements IUserContext {
        private final Set<String> roles;

        RoleUserContext(Set<String> roles) {
            this.roles = roles;
        }

        @Override
        public String getUserId() {
            return "user-1";
        }

        @Override
        public String getUserName() {
            return "user-1";
        }

        @Override
        public boolean isUserInRole(String roleId) {
            return roles.contains(roleId);
        }

        @Override
        public boolean isUserInAnyRole(Collection<String> roleIds) {
            for (String role : roleIds) {
                if (roles.contains(role)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Set<String> getRoles() {
            return roles;
        }

        @Override
        public void addRole(String roleId) {
        }

        @Override
        public void removeRole(String roleId) {
        }

        @Override
        public String getAccessToken() {
            return null;
        }

        @Override
        public String getRefreshToken() {
            return null;
        }

        @Override
        public void setAccessToken(String accessToken) {
        }

        @Override
        public void setRefreshToken(String refreshToken) {
        }

        @Override
        public long getLastAccessTime() {
            return 0;
        }

        @Override
        public void setLastAccessTime(long lastAccessTime) {
        }

        @Override
        public boolean dirty() {
            return false;
        }

        @Override
        public void markDirty() {
        }

        @Override
        public void clearDirty() {
        }

        @Override
        public String getSessionId() {
            return null;
        }

        @Override
        public String getTenantId() {
            return null;
        }

        @Override
        public String getLocale() {
            return null;
        }

        @Override
        public String getPrimaryRole() {
            return null;
        }

        @Override
        public String getTimeZone() {
            return null;
        }

        @Override
        public String getDeptId() {
            return null;
        }

        @Override
        public String getDeptName() {
            return null;
        }

        @Override
        public String getOpenId() {
            return null;
        }

        @Override
        public String getNickName() {
            return null;
        }

        @Override
        public Map<String, Object> getAttrs() {
            return Collections.emptyMap();
        }

        @Override
        public Object getAttr(String name) {
            return null;
        }

        @Override
        public void setAttr(String name, Object value) {
        }
    }

    private void installVault() {
        vault = new FakeCredentialVault();
        dataSourceBizModel.setCredentialProvider(vault);
        dataSourceBizModel.setCredentialMigrationSupport(vault);
    }

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private void saveDataSource(String id, String querySpace, String connectionConfig) {
        saveDataSource(id, querySpace, connectionConfig, id);
    }

    private void saveDataSource(String id, String querySpace, String connectionConfig, String name) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(name);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig(connectionConfig);
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    private NopMetaDataSource loadRow(String id) {
        return daoProvider.daoFor(NopMetaDataSource.class).getEntityById(id);
    }

    private static Map<String, Object> parseCfg(String json) {
        return json == null ? new LinkedHashMap<>() : JsonTool.parseMap(json);
    }

    private static final String PLAINTEXT_CFG(String id) {
        return "{\"jdbcUrl\":\"jdbc:h2:mem:" + id + "\",\"username\":\"sa\",\"password\":\"secret\"}";
    }

    // ==================== bindCredential ====================

    @Test
    public void bindCredentialSetsKeyClearsPlaintextAndRegistersUsage() {
        installVault();
        saveDataSource("ds-bind-1", "qs_bind1", PLAINTEXT_CFG("ds_bind_1"));
        String credentialId = vault.createCredential("jdbc-datasource", "manual-cred", false);

        GraphQLResponseBean response = execute("mutation { NopMetaDataSource__bindCredential("
                + "dataSourceId: \"ds-bind-1\", credentialId: \"" + credentialId + "\") }");
        assertEquals(credentialId, mutationData(response, "NopMetaDataSource__bindCredential").get("credentialId"));

        Map<String, Object> cfg = parseCfg(loadRow("ds-bind-1").getConnectionConfig());
        assertEquals(credentialId, cfg.get("credentialId"));
        assertFalse(cfg.containsKey("username"), "plaintext username must be cleared");
        assertFalse(cfg.containsKey("password"), "plaintext password must be cleared");
        assertEquals(credentialId, vault.usages.get("metadata:NopMetaDataSource:ds-bind-1"),
                "registerUsage must be recorded with consumerRef convention");
    }

    @Test
    public void bindCredentialIdempotentOnRepeat() {
        installVault();
        saveDataSource("ds-bind-2", "qs_bind2", PLAINTEXT_CFG("ds_bind_2"));
        String credentialId = vault.createCredential("jdbc-datasource", "manual-cred-2", false);

        execute("mutation { NopMetaDataSource__bindCredential(dataSourceId: \"ds-bind-2\", "
                + "credentialId: \"" + credentialId + "\") }");
        GraphQLResponseBean second = execute("mutation { NopMetaDataSource__bindCredential("
                + "dataSourceId: \"ds-bind-2\", credentialId: \"" + credentialId + "\") }");
        mutationData(second, "NopMetaDataSource__bindCredential"); // 幂等成功即断言
        assertEquals(credentialId, vault.usages.get("metadata:NopMetaDataSource:ds-bind-2"));
    }

    @Test
    public void bindCredentialRebindUnregistersOldAndRegistersNew() {
        installVault();
        saveDataSource("ds-bind-3", "qs_bind3", PLAINTEXT_CFG("ds_bind_3"));
        String credA = vault.createCredential("jdbc-datasource", "cred-a", false);
        String credB = vault.createCredential("jdbc-datasource", "cred-b", false);

        execute("mutation { NopMetaDataSource__bindCredential(dataSourceId: \"ds-bind-3\", "
                + "credentialId: \"" + credA + "\") }");
        execute("mutation { NopMetaDataSource__bindCredential(dataSourceId: \"ds-bind-3\", "
                + "credentialId: \"" + credB + "\") }");

        Map<String, Object> cfg = parseCfg(loadRow("ds-bind-3").getConnectionConfig());
        assertEquals(credB, cfg.get("credentialId"), "rebind A->B must end with B");
        assertEquals(credB, vault.usages.get("metadata:NopMetaDataSource:ds-bind-3"),
                "usage must point to B after rebind");
    }

    @Test
    public void bindCredentialFailureLeavesRowUntouched() {
        installVault();
        saveDataSource("ds-bind-4", "qs_bind4", PLAINTEXT_CFG("ds_bind_4"));
        String credDead = vault.createCredential("jdbc-datasource", "cred-dead", true); // 软删

        GraphQLResponseBean response = execute("mutation { NopMetaDataSource__bindCredential("
                + "dataSourceId: \"ds-bind-4\", credentialId: \"" + credDead + "\") }");
        assertTrue(response.hasError(), "bind to soft-deleted credential must fail (D6-03 前置校验)");

        // 无中间落盘态：行未被触碰（明文仍在、无 credentialId 键）
        Map<String, Object> cfg = parseCfg(loadRow("ds-bind-4").getConnectionConfig());
        assertEquals("sa", cfg.get("username"), "plaintext must survive failed bind (atomicity)");
        assertFalse(cfg.containsKey("credentialId"));
        assertNull(vault.usages.get("metadata:NopMetaDataSource:ds-bind-4"));
    }

    @Test
    public void bindCredentialRejectedForNonAdminLoginUser() {
        installVault();
        IUserContext.set(new RoleUserContext(Collections.emptySet())); // 登录但无 admin 角色
        try {
            NopException ex = assertThrows(NopException.class,
                    () -> dataSourceBizModel.bindCredential("ds-x", "cred-x", null));
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_ADMIN_REQUIRED.getErrorCode(),
                    ex.getErrorCode());
        } finally {
            IUserContext.set(null);
        }
    }

    @Test
    public void bindCredentialAllowedForConfiguredAdminRole() {
        installVault();
        saveDataSource("ds-bind-5", "qs_bind5", PLAINTEXT_CFG("ds_bind_5"));
        String credentialId = vault.createCredential("jdbc-datasource", "cred-admin", false);
        IUserContext.set(new RoleUserContext(Set.of("nop-admin"))); // 缺省角色集 admin,nop-admin
        try {
            ormTemplate.runInSession(session -> // 直调（无 GraphQL 层）；session 等价 GraphQL 引擎事务环境
                    dataSourceBizModel.bindCredential("ds-bind-5", credentialId, null));
            Map<String, Object> cfg = parseCfg(loadRow("ds-bind-5").getConnectionConfig());
            assertEquals(credentialId, cfg.get("credentialId"));
        } finally {
            IUserContext.set(null);
        }
    }

    // ==================== unbindCredential ====================

    @Test
    public void unbindCredentialClearsKeyAndUsageWithoutRestoringPlaintext() {
        installVault();
        saveDataSource("ds-unbind-1", "qs_unbind1", PLAINTEXT_CFG("ds_unbind_1"));
        String credentialId = vault.createCredential("jdbc-datasource", "cred-u", false);
        execute("mutation { NopMetaDataSource__bindCredential(dataSourceId: \"ds-unbind-1\", "
                + "credentialId: \"" + credentialId + "\") }");

        GraphQLResponseBean response = execute("mutation { NopMetaDataSource__unbindCredential("
                + "dataSourceId: \"ds-unbind-1\") }");
        assertFalse(response.hasError(), "unbind should succeed: " + response);

        Map<String, Object> cfg = parseCfg(loadRow("ds-unbind-1").getConnectionConfig());
        assertFalse(cfg.containsKey("credentialId"), "credentialId key must be removed");
        assertFalse(cfg.containsKey("username"), "unbind must NOT resurrect dead plaintext values");
        assertFalse(cfg.containsKey("password"));
        assertNull(vault.usages.get("metadata:NopMetaDataSource:ds-unbind-1"), "usage must be unregistered");
    }

    @Test
    public void unbindCredentialIdempotentWhenNoKey() {
        installVault();
        saveDataSource("ds-unbind-2", "qs_unbind2", PLAINTEXT_CFG("ds_unbind_2"));

        GraphQLResponseBean response = execute("mutation { NopMetaDataSource__unbindCredential("
                + "dataSourceId: \"ds-unbind-2\") }");
        assertFalse(response.hasError(), "unbind without credentialId must be a no-op: " + response);
        assertEquals("sa", parseCfg(loadRow("ds-unbind-2").getConnectionConfig()).get("username"));
    }

    // ==================== delete / deleteByQuery 双路径 unregister ====================

    @Test
    public void deleteUnregistersCredentialUsage() {
        installVault();
        saveDataSource("ds-del-1", "qs_del1", PLAINTEXT_CFG("ds_del_1"));
        String credentialId = vault.createCredential("jdbc-datasource", "cred-d1", false);
        execute("mutation { NopMetaDataSource__bindCredential(dataSourceId: \"ds-del-1\", "
                + "credentialId: \"" + credentialId + "\") }");
        assertNotNull(vault.usages.get("metadata:NopMetaDataSource:ds-del-1"));

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__delete(id: \"ds-del-1\") }");
        assertFalse(response.hasError(), "delete should succeed: " + response);
        assertNull(vault.usages.get("metadata:NopMetaDataSource:ds-del-1"),
                "delete must unregister credential usage (single-path)");
    }

    @Test
    public void deleteByQueryUnregistersCredentialUsageForAllHits() {
        installVault();
        saveDataSource("ds-dq-1", "qs_dq_1", PLAINTEXT_CFG("ds_dq_1"));
        saveDataSource("ds-dq-2", "qs_dq_2", PLAINTEXT_CFG("ds_dq_2"));
        String credentialId = vault.createCredential("jdbc-datasource", "cred-dq", false);
        execute("mutation { NopMetaDataSource__bindCredential(dataSourceId: \"ds-dq-1\", "
                + "credentialId: \"" + credentialId + "\") }");
        execute("mutation { NopMetaDataSource__bindCredential(dataSourceId: \"ds-dq-2\", "
                + "credentialId: \"" + credentialId + "\") }");
        assertNotNull(vault.usages.get("metadata:NopMetaDataSource:ds-dq-1"));

        // 批量路径：基类 doDeleteByQuery 不经虚分派覆盖的 delete——覆写必须先收集后注销（直调覆写面；
        // runInSession 等价 GraphQL 引擎会话环境——findList 与 deleteByQuery 共用同一 session）
        QueryBean deleteQuery = new QueryBean();
        deleteQuery.addFilter(io.nop.api.core.beans.FilterBeans.in("querySpace",
                java.util.Arrays.asList("qs_dq_1", "qs_dq_2")));
        int deleted = ormTemplate.runInSession(session ->
                dataSourceBizModel.deleteByQuery(deleteQuery, new io.nop.core.context.ServiceContextImpl()));
        assertEquals(2, deleted, "both rows deleted by query");
        assertNull(vault.usages.get("metadata:NopMetaDataSource:ds-dq-1"),
                "deleteByQuery must unregister usage (batch path, no virtual dispatch)");
        assertNull(vault.usages.get("metadata:NopMetaDataSource:ds-dq-2"));
    }

    // ==================== 批量迁移 ====================

    @Test
    public void migrateAllDataSourcesCreatesCredentialsAndClearsPlaintext() {
        installVault();
        saveDataSource("ds-mig-1", "qs_mig1", PLAINTEXT_CFG("ds_mig_1"));
        saveDataSource("ds-mig-2", "qs_mig2", PLAINTEXT_CFG("ds_mig_2"));
        saveDataSource("ds-mig-none", "qs_mig_none",
                "{\"jdbcUrl\":\"jdbc:h2:mem:ds_mig_none\"}"); // 无凭据行 → 跳过

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__migrateDataSourcesCredential }");
        Map<String, Object> summary = mutationData(response, "NopMetaDataSource__migrateDataSourcesCredential");
        assertEquals(2, ((Number) summary.get("migratedCount")).intValue(), "two plaintext rows migrated");
        assertEquals(1, ((Number) summary.get("skippedCount")).intValue(), "no-credential row skipped");
        assertEquals(0, ((Number) summary.get("failedCount")).intValue(), "no failures");

        for (String id : new String[]{"ds-mig-1", "ds-mig-2"}) {
            Map<String, Object> cfg = parseCfg(loadRow(id).getConnectionConfig());
            assertFalse(cfg.containsKey("username"), "plaintext cleared for " + id);
            assertFalse(cfg.containsKey("password"));
            assertNotNull(cfg.get("credentialId"), "credentialId key present for " + id);
            assertEquals(cfg.get("credentialId"),
                    vault.usages.get("metadata:NopMetaDataSource:" + id), "usage registered for " + id);
        }
        assertEquals(2, vault.createdCredentialIds.size(), "one credential per migrated row");
    }

    @Test
    public void migrateRerunIdempotentReusesCredentialsViaConsumerRef() {
        installVault();
        saveDataSource("ds-mig-r1", "qs_mig_r1", PLAINTEXT_CFG("ds_mig_r1"));

        execute("mutation { NopMetaDataSource__migrateDataSourcesCredential }");
        int createdAfterFirst = vault.createdCredentialIds.size();

        GraphQLResponseBean second = execute(
                "mutation { NopMetaDataSource__migrateDataSourcesCredential }");
        Map<String, Object> rerun = mutationData(second, "NopMetaDataSource__migrateDataSourcesCredential");
        assertEquals(0, ((Number) rerun.get("migratedCount")).intValue(), "already-migrated row skipped on rerun");
        assertEquals(1, ((Number) rerun.get("skippedCount")).intValue());
        assertEquals(createdAfterFirst, vault.createdCredentialIds.size(),
                "rerun must not create duplicate credentials (consumerRef 反查复用)");
    }

    @Test
    public void migrateInterruptedRerunConvergesWithoutDuplicates() {
        installVault();
        saveDataSource("ds-mig-i1", "qs_mig_i1", PLAINTEXT_CFG("ds_mig_i1"));
        saveDataSource("ds-mig-i2", "qs_mig_i2", PLAINTEXT_CFG("ds_mig_i2"));
        saveDataSource("ds-mig-i3", "qs_mig_i3", PLAINTEXT_CFG("ds_mig_i3"));

        // 第 2 行注入失败（registerUsage 抛错 → 该行 REQUIRES_NEW 事务回滚：credentialId 写入与
        // 明文清除同事务原子——回滚后无孤儿凭证、无半迁移行）
        vault.registerFailOnConsumerRef.add("metadata:NopMetaDataSource:ds-mig-i2");

        GraphQLResponseBean first = execute(
                "mutation { NopMetaDataSource__migrateDataSourcesCredential }");
        Map<String, Object> firstSummary = mutationData(first, "NopMetaDataSource__migrateDataSourcesCredential");
        assertEquals(1, ((Number) firstSummary.get("failedCount")).intValue(),
                "injected row failure collected, batch not aborted");

        // 解除注入（模拟运维修复后重跑）
        vault.registerFailOnConsumerRef.clear();

        // 中断重跑收敛
        GraphQLResponseBean rerun = execute(
                "mutation { NopMetaDataSource__migrateDataSourcesCredential }");
        Map<String, Object> rerunSummary = mutationData(rerun, "NopMetaDataSource__migrateDataSourcesCredential");
        assertEquals(0, ((Number) rerunSummary.get("failedCount")).intValue(), "rerun converges with no failures");

        for (String id : new String[]{"ds-mig-i1", "ds-mig-i2", "ds-mig-i3"}) {
            Map<String, Object> cfg = parseCfg(loadRow(id).getConnectionConfig());
            assertFalse(cfg.containsKey("username"), "all rows migrated after rerun: " + id);
            assertNotNull(cfg.get("credentialId"));
            assertEquals(cfg.get("credentialId"), vault.usages.get("metadata:NopMetaDataSource:" + id));
        }
        // 无重复凭证：每行恰一个凭证（consumerRef 反查复用 + 回滚不留孤儿）
        assertEquals(3, vault.createdCredentialIds.size(),
                "exactly one credential per row across interrupted runs");
    }

    @Test
    public void migrateSoftDeletedReferenceCountedAsFailure() {
        installVault();
        saveDataSource("ds-mig-sd", "qs_mig_sd", PLAINTEXT_CFG("ds_mig_sd"));
        // 预置软删凭证 + usage 引用（模拟历史遗留：consumerRef 指向墓碑）
        String deadId = vault.createCredential("jdbc-datasource", "dead-cred", true);
        vault.usages.put("metadata:NopMetaDataSource:ds-mig-sd", deadId);

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__migrateDataSourcesCredential }");
        Map<String, Object> summary = mutationData(response, "NopMetaDataSource__migrateDataSourcesCredential");
        assertEquals(1, ((Number) summary.get("failedCount")).intValue(),
                "soft-deleted hit must be counted as failure");
        assertEquals(0, ((Number) summary.get("migratedCount")).intValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) summary.get("failures");
        assertEquals(1, failures.size());
        assertEquals("ds-mig-sd", failures.get(0).get("dataSourceId"),
                "failure list names the dataSourceId");

        // 行未被跳过也未被重建：明文仍在（供人工处置），未新建凭证
        Map<String, Object> cfg = parseCfg(loadRow("ds-mig-sd").getConnectionConfig());
        assertEquals("sa", cfg.get("username"), "row left for manual handling (no skip, no re-create)");
        assertFalse(cfg.containsKey("credentialId"));
        assertEquals(0, vault.createdCredentialIds.size(), "no re-created credential for ambiguous ref");
    }

    @Test
    public void migrateLongNameUsesTruncatedHashedCredentialName() {
        installVault();
        // 行内列（NAME/QUERY_SPACE 各 VARCHAR(100)）放得下、拼接凭证名超 100：querySpace 50 + name 50
        String longName = "n".repeat(50);
        String longQuerySpace = "q".repeat(50);
        saveDataSource("ds-mig-long", longQuerySpace, PLAINTEXT_CFG("ds_mig_long"), longName);

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__migrateDataSourcesCredential }");
        Map<String, Object> summary = mutationData(response, "NopMetaDataSource__migrateDataSourcesCredential");
        assertEquals(1, ((Number) summary.get("migratedCount")).intValue(), "long name must migrate successfully");

        // 确定性名截断 + 哈希后缀 ≤ 100（name 列宽）
        String expectedName = NopMetaDataSourceBizModel.deterministicCredentialName(longQuerySpace, longName);
        assertTrue(expectedName.length() > 90, "composite name exceeds 100 before truncation: " + expectedName.length());
        assertTrue(expectedName.length() <= 100);
        assertNotNull(vault.findCredentialByName("jdbc-datasource", expectedName));
    }

    @Test
    public void deterministicCredentialNameTruncatesWithHashSuffix() {
        String longName = NopMetaDataSourceBizModel.deterministicCredentialName(
                "qs".repeat(40), "name".repeat(40));
        assertTrue(longName.length() <= 100, "must fit VARCHAR(100)");
        assertTrue(longName.contains("-"), "hash suffix separator present");
        // 短名不截断（确定性原样）
        String shortName = NopMetaDataSourceBizModel.deterministicCredentialName("qs", "n");
        assertEquals("jdbc-datasource:qs/n", shortName);
        // 不同长输入 → 不同截断名（哈希后缀区分）
        String other = NopMetaDataSourceBizModel.deterministicCredentialName(
                "qs".repeat(40), "name".repeat(40) + "!");
        assertTrue(!longName.equals(other), "distinct long names must not collide into identical truncated name");
    }
}
