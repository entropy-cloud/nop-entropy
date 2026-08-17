package io.nop.ai.service.entity;

import io.nop.ai.dao.entity.NopAiModel;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.ServiceContextImpl;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1a D6-02（A1-audit successor，2026-08-17）：{@link NopAiModelBizModel} 删除动作注销
 * 引用计数的 <b>mock-verify 级</b>接线测试——参数捕获断言 {@code delete}/{@code batchDelete}/
 * {@code deleteByQuery} 真实调用 {@link ICredentialProvider#unregisterUsage} 且
 * (credentialId, consumerRef) 正确。
 *
 * <p>经 AutoTest 容器（{@code @NopTestConfig(localDb)}）注入容器装配的 BizModel bean
 * （真实 objMeta/crudToolProvider/bizObjectManager 链路），将可选依赖
 * {@code ICredentialProvider} 换为 recording fake 捕获注销调用（对齐
 * {@link TestNopAiModelCredentialUsage} 先例；"usage 行存在 → 凭证删除被拦截"链路由
 * nop-credential 侧既有引用计数拦截测试覆盖，消费方不重复实现）。
 *
 * <p>直接 BizModel 调用需 ORM 会话上下文（GraphQL 面由引擎包裹；此处对齐
 * {@code TestNopAiBizModelEntityCrud} 先例显式 {@code ormTemplate.runInSession}，
 * 使"读取待删行 + 删除"同处一个会话）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopAiModelDeleteUsage extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    NopAiModelBizModel bizModel;

    private RecordingProvider provider;

    // ==================== 测试基建 ====================

    private void wireRecordingProvider() {
        provider = new RecordingProvider();
        bizModel.setCredentialProvider(provider);
    }

    private void seedModel(String id, String providerCode, String modelName, String credentialId) {
        IEntityDao<NopAiModel> dao = daoProvider.daoFor(NopAiModel.class);
        NopAiModel m = dao.newEntity();
        m.setId(id);
        m.setProvider(providerCode);
        m.setModelName(modelName);
        m.setCredentialId(credentialId);
        m.setVersion(1);
        dao.saveEntity(m);
    }

    private static String call(String credentialId, String consumerRef) {
        return credentialId + "|" + consumerRef;
    }

    // ==================== delete：删除前读 credentialId、删除后注销 ====================

    @Test
    public void deleteUnregistersUsageWithCorrectRef() {
        seedModel("del-1", "oa", "g4", "cred-A");
        wireRecordingProvider();

        Boolean deleted = ormTemplate.runInSession(
                s -> bizModel.delete("del-1", new ServiceContextImpl()));

        assertTrue(deleted, "delete must succeed (no usage-reference interception on model face)");
        assertEquals(List.of(call("cred-A", "ai:NopAiModel:del-1")), provider.unregistered,
                "delete must really call unregisterUsage with (credentialId, consumerRef) — mock verify, not type existence");
        assertTrue(provider.registered.isEmpty(), "delete must not register");
    }

    @Test
    public void deleteWithoutCredentialIdSkipsUnregister() {
        seedModel("del-2", "oa", "g4", null);
        wireRecordingProvider();

        Boolean deleted = ormTemplate.runInSession(
                s -> bizModel.delete("del-2", new ServiceContextImpl()));

        assertTrue(deleted);
        assertTrue(provider.unregistered.isEmpty(), "model without credentialId has nothing to unregister");
    }

    @Test
    public void deleteNonExistentIdIsIdempotentWithoutUnregister() {
        wireRecordingProvider();

        Boolean deleted = ormTemplate.runInSession(
                s -> bizModel.delete("no-such-model", new ServiceContextImpl()));

        assertTrue(deleted, "base doDelete idempotent semantics for unknown id");
        assertTrue(provider.unregistered.isEmpty(), "unknown id must not produce unregister calls");
    }

    @Test
    public void providerNotDeployedSkipsUnregisterSafely() {
        seedModel("del-3", "oa", "g4", "cred-A");
        bizModel.setCredentialProvider(null); // deployment without credential lib

        Boolean deleted = ormTemplate.runInSession(
                s -> bizModel.delete("del-3", new ServiceContextImpl()));

        assertTrue(deleted, "delete must proceed when ICredentialProvider is not deployed (optional wiring)");
    }

    // ==================== batchDelete：基类逐 id 委托覆盖后的 delete（虚分派） ====================

    @Test
    public void batchDeleteUnregistersUsagePerId() {
        seedModel("bd-1", "oa", "g4", "cred-B1");
        seedModel("bd-2", "oa", "g4o", "cred-B2");
        seedModel("bd-3", "oa", "g41", null);
        wireRecordingProvider();

        ormTemplate.runInSession(s -> bizModel.batchDelete(
                new LinkedHashSet<>(List.of("bd-1", "bd-2", "bd-3")), new ServiceContextImpl()));

        assertEquals(Set.of(
                        call("cred-B1", "ai:NopAiModel:bd-1"),
                        call("cred-B2", "ai:NopAiModel:bd-2")),
                Set.copyOf(provider.unregistered),
                "batchDelete delegates per-id to the overridden delete => unregisterUsage per bound model");
        assertTrue(provider.registered.isEmpty());
    }

    // ==================== deleteByQuery：基类不经过 delete，覆盖内先收集后注销 ====================

    @Test
    public void deleteByQueryUnregistersUsageForAllHits() {
        seedModel("dq-1", "an", "c3", "cred-C1");
        seedModel("dq-2", "an", "c35", "cred-C2");
        seedModel("dq-other", "oa", "g4", "cred-C3");
        wireRecordingProvider();

        QueryBean query = new QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq(NopAiModel.PROP_NAME_provider, "an"));

        Integer deleted = ormTemplate.runInSession(
                s -> bizModel.deleteByQuery(query, new ServiceContextImpl()));

        assertEquals(2, deleted, "deleteByQuery must delete exactly the 2 provider=an rows");
        assertEquals(Set.of(
                        call("cred-C1", "ai:NopAiModel:dq-1"),
                        call("cred-C2", "ai:NopAiModel:dq-2")),
                Set.copyOf(provider.unregistered),
                "deleteByQuery must unregister usage for every deleted hit (base path bypasses delete)");

        // NopAiModel 无逻辑删除列 → 物理删除：非命中行存活、命中行消失
        IEntityDao<NopAiModel> dao = daoProvider.daoFor(NopAiModel.class);
        assertNotNull(dao.getEntityById("dq-other"), "non-matching row must survive");
        assertNull(dao.getEntityById("dq-1"), "matching row must be deleted");
        assertNull(dao.getEntityById("dq-2"), "matching row must be deleted");
        assertTrue(provider.registered.isEmpty());
    }

    // ==================== recording fake ====================

    private static final class RecordingProvider implements ICredentialProvider {
        final List<String> registered = new ArrayList<>();
        final List<String> unregistered = new ArrayList<>();

        @Override
        public void registerUsage(String credentialId, String consumerRef) {
            registered.add(call(credentialId, consumerRef));
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
            unregistered.add(call(credentialId, consumerRef));
        }

        @Override
        public CredentialData getCredential(String credentialId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getCredentialData(String credentialId, String field) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TestResult testCredential(String credentialId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaskedCredential mask(String credentialId) {
            throw new UnsupportedOperationException();
        }
    }
}
