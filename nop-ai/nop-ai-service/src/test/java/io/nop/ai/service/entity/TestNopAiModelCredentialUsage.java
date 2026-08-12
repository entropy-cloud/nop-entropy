package io.nop.ai.service.entity;

import io.nop.ai.dao.entity.NopAiModel;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W7-successor（plan 2026-08-13-1118-3 Phase 2）：{@link NopAiModelBizModel} credential 引用计数接线测试。
 *
 * <p>覆盖 bind / 换绑 / 解绑 / noop 的 {@link ICredentialProvider#registerUsage} /
 * {@link ICredentialProvider#unregisterUsage} 调用语义 + consumerRef 约定（{@code ai:NopAiModel:<modelId>}）。
 * 用 recording fake 捕获调用（消费方接线验证），凭证库自身的 register/unregister DB 落行已在
 * nop-credential-service 的 TestCredentialProviderImpl 覆盖（消费方不重测 SPI 实现）。
 */
public class TestNopAiModelCredentialUsage {

    @Test
    void bindRegistersUsage() {
        RecordingProvider provider = new RecordingProvider();
        NopAiModelBizModel biz = newBiz(provider);

        NopAiModel saved = model("m-1", "cred-A");
        biz.reconcileCredentialUsage(saved, null); // old=null, new=cred-A => bind

        assertEquals(1, provider.registered.size());
        assertEquals(call("cred-A", "ai:NopAiModel:m-1"), provider.registered.get(0));
        assertTrue(provider.unregistered.isEmpty(), "bind must not unregister");
    }

    @Test
    void switchCredentialUnregistersOldAndRegistersNew() {
        RecordingProvider provider = new RecordingProvider();
        NopAiModelBizModel biz = newBiz(provider);

        NopAiModel saved = model("m-2", "cred-B");
        biz.reconcileCredentialUsage(saved, "cred-A"); // old=cred-A, new=cred-B => switch

        assertEquals(1, provider.registered.size());
        assertEquals(call("cred-B", "ai:NopAiModel:m-2"), provider.registered.get(0));
        assertEquals(1, provider.unregistered.size());
        assertEquals(call("cred-A", "ai:NopAiModel:m-2"), provider.unregistered.get(0));
    }

    @Test
    void unbindUnregistersUsage() {
        RecordingProvider provider = new RecordingProvider();
        NopAiModelBizModel biz = newBiz(provider);

        NopAiModel saved = model("m-3", null);
        biz.reconcileCredentialUsage(saved, "cred-A"); // old=cred-A, new=null => unbind

        assertTrue(provider.registered.isEmpty(), "unbind must not register");
        assertEquals(1, provider.unregistered.size());
        assertEquals(call("cred-A", "ai:NopAiModel:m-3"), provider.unregistered.get(0));
    }

    @Test
    void unchangedCredentialIdIsNoop() {
        RecordingProvider provider = new RecordingProvider();
        NopAiModelBizModel biz = newBiz(provider);

        NopAiModel saved = model("m-4", "cred-A");
        biz.reconcileCredentialUsage(saved, "cred-A"); // old=new=cred-A => noop

        assertTrue(provider.registered.isEmpty());
        assertTrue(provider.unregistered.isEmpty());
    }

    @Test
    void bothEmptyIsNoop() {
        RecordingProvider provider = new RecordingProvider();
        NopAiModelBizModel biz = newBiz(provider);

        NopAiModel saved = model("m-5", null);
        biz.reconcileCredentialUsage(saved, null); // old=new=null => noop

        assertTrue(provider.registered.isEmpty());
        assertTrue(provider.unregistered.isEmpty());
    }

    @Test
    void providerNotDeployedSkipsReconciliationSafely() {
        NopAiModelBizModel biz = new NopAiModelBizModel();
        biz.setCredentialProvider(null); // nop-credential not deployed

        // must not throw — graceful skip (deployment without credential lib)
        biz.reconcileCredentialUsage(model("m-6", "cred-A"), null);
    }

    @Test
    void consumerRefFollowsConvention() {
        assertEquals("ai:NopAiModel:abc-123", NopAiModelBizModel.consumerRef("abc-123"));
    }

    private static NopAiModelBizModel newBiz(RecordingProvider provider) {
        NopAiModelBizModel biz = new NopAiModelBizModel();
        biz.setCredentialProvider(provider);
        return biz;
    }

    private static NopAiModel model(String id, String credentialId) {
        NopAiModel m = new NopAiModel();
        m.setId(id);
        m.setCredentialId(credentialId);
        return m;
    }

    private static String call(String credentialId, String consumerRef) {
        return credentialId + "|" + consumerRef;
    }

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
