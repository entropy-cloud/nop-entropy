package io.nop.ai.core.service;

import io.nop.ai.core.model.ModelClassCandidateModel;
import io.nop.ai.core.model.ModelClassModel;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_NO_DEFAULT_LLMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-15-0849-2 Phase 2 (ROUTE-01): {@code LlmConfigHelper} 模型类路由组解析——
 * model → 模型类归属 + 候选集展开（Minimum Rules #24 无静默跳过 + #25 新功能必有测试）。
 *
 * <p>归属机制：显式成员声明（members 清单，model 名全局匹配，首个声明命中胜出）；
 * 未归属 = null = 无路由组（零回归）；无配置文件 = null（零回归守卫）。
 * 候选展开：accountRef 缺省 = 主账号 + 有序账号链（主账号在前）；accountRef = 单账号（未知 id
 * 解析期 fail-fast）；model 缺省 = provider defaultModel（缺失解析期 fail-fast）。
 */
public class TestLlmConfigHelperModelClass extends JunitBaseTestCase {

    private static final String CONFIG_REL_PATH =
            "src/test/resources/_vfs/nop/ai/llm/_default.model-class.xml";
    private static final String CONFIG_TARGET_PATH =
            "target/test-classes/_vfs/nop/ai/llm/_default.model-class.xml";

    @Test
    void resolveModelClassReturnsFirstDeclaredHit() {
        // 多类声明 + 首个声明命中（成员清单包含即命中，声明顺序决定归属）。
        ModelClassModel v4 = LlmConfigHelper.resolveModelClass("deepseek-v4");
        assertNotNull(v4);
        assertEquals("tier-v4", v4.getId());

        assertEquals("tier-v4", LlmConfigHelper.resolveModelClass("deepseek-v4-max").getId(),
                "members csv-set 多值成员均命中同一类");
        assertEquals("tier-misc", LlmConfigHelper.resolveModelClass("misc-model-a").getId(),
                "第二个声明类同样可达");
    }

    @Test
    void resolveModelClassUnknownModelIsNull() {
        // 未归属任何类 = 无路由组（零回归语义——与无配置文件行为一致）。
        assertNull(LlmConfigHelper.resolveModelClass("not-a-member-model"));
    }

    @Test
    void resolveModelClassEmptyModelIsNull() {
        assertNull(LlmConfigHelper.resolveModelClass(null));
        assertNull(LlmConfigHelper.resolveModelClass(""));
    }

    @Test
    void resolveModelClassAbsentFileIsNull() throws Exception {
        // 无 model-class 配置文件 = 无路由组 = 零回归（参照 resolveFailoverChain 零回归守卫模式）。
        File src = new File(CONFIG_REL_PATH);
        File target = new File(CONFIG_TARGET_PATH);
        File srcBak = new File(CONFIG_REL_PATH + ".bak");
        File targetBak = new File(CONFIG_TARGET_PATH + ".bak");
        boolean srcMoved = false;
        boolean targetMoved = false;
        try {
            if (src.exists()) {
                assertTrue(src.renameTo(srcBak), "move src config aside");
                srcMoved = true;
            }
            if (target.exists()) {
                assertTrue(target.renameTo(targetBak), "move target/test-classes config aside");
                targetMoved = true;
            }
            assertNull(LlmConfigHelper.resolveModelClass("deepseek-v4"),
                    "absent model-class file must resolve to null (zero-regression)");
        } finally {
            if (srcMoved) {
                assertTrue(srcBak.renameTo(src), "restore src config");
            }
            if (targetMoved) {
                assertTrue(targetBak.renameTo(target), "restore target config");
            }
        }
    }

    @Test
    void resolveModelClassCandidatesExpandsProviderChain() {
        // 候选展开全链：accountRef 缺省 = 主账号 + 有序账号链（主账号在前）；
        // accountRef = 单账号；model 缺省 = provider defaultModel；声明顺序保留。
        ModelClassModel v4 = LlmConfigHelper.resolveModelClass("deepseek-v4");
        List<ModelClassCandidate> candidates = LlmConfigHelper.resolveModelClassCandidates(v4);

        assertNotNull(candidates);
        assertEquals(6, candidates.size(),
                "1 (test-accounts main+3 backups) + 1 (backup-2 ref) + 1 (test-modelclass main) = 6");

        // 候选 1（纯 provider）：主账号在前。
        assertCandidate(candidates.get(0), "test-accounts", "deepseek-v4", null, null, 10);
        // 账号链按声明顺序（backup-1/2/3）。
        assertCandidate(candidates.get(1), "test-accounts", "deepseek-v4", "key-backup-1",
                "https://backup1.example.com", 10);
        assertCandidate(candidates.get(2), "test-accounts", "deepseek-v4", "key-backup-2", null, 3);
        assertCandidate(candidates.get(3), "test-accounts", "deepseek-v4", "key-backup-3",
                "https://backup3.example.com", 0);
        // 候选 2（accountRef=backup-2）：只展开为 backup-2。
        assertCandidate(candidates.get(4), "test-accounts", "deepseek-v4-max", "key-backup-2", null, 3);
        // 候选 3（model 缺省）：取 test-modelclass 的 defaultModel；无并发配置 = 不限制。
        assertCandidate(candidates.get(5), "test-modelclass", "test-model-1", null, null, null);
    }

    @Test
    void resolveModelClassCandidatesEmptyWhenNullClass() {
        // 无路由组（null 类）= 空候选集（零回归，非异常）。
        List<ModelClassCandidate> candidates = LlmConfigHelper.resolveModelClassCandidates(null);
        assertNotNull(candidates);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void resolveModelClassCandidatesReturnsUnmodifiableView() {
        ModelClassModel v4 = LlmConfigHelper.resolveModelClass("deepseek-v4");
        List<ModelClassCandidate> candidates = LlmConfigHelper.resolveModelClassCandidates(v4);
        assertThrows(UnsupportedOperationException.class, () -> candidates.clear(),
                "resolved candidates must be an unmodifiable snapshot");
    }

    @Test
    void resolveModelClassCandidatesUnknownAccountRefFailsFast() {
        // 未知 accountRef：解析期 fail-fast（不静默吞——Minimum Rules #24）。
        ModelClassModel bad = new ModelClassModel();
        bad.setId("bad-class");
        ModelClassCandidateModel declared = new ModelClassCandidateModel();
        declared.setProvider("test-accounts");
        declared.setModel("deepseek-v4");
        declared.setAccountRef("no-such-account");
        bad.setCandidates(List.of(declared));

        NopException e = assertThrows(NopException.class,
                () -> LlmConfigHelper.resolveModelClassCandidates(bad));
        assertEquals(ERR_AI_AGENT_INVALID_ARG.getErrorCode(), e.getErrorCode(),
                "unknown accountRef must fail fast with the invalid-arg error code");
    }

    @Test
    void resolveModelClassCandidatesUnknownProviderFailsFast() {
        // 未知 provider：解析期 fail-fast（不静默吞）。
        ModelClassModel bad = new ModelClassModel();
        bad.setId("bad-class");
        ModelClassCandidateModel declared = new ModelClassCandidateModel();
        declared.setProvider("no-such-provider");
        bad.setCandidates(List.of(declared));

        NopException e = assertThrows(NopException.class,
                () -> LlmConfigHelper.resolveModelClassCandidates(bad));
        assertEquals(ERR_AI_AGENT_INVALID_ARG.getErrorCode(), e.getErrorCode(),
                "unknown provider must fail fast with the invalid-arg error code");
    }

    @Test
    void resolveModelClassCandidatesMissingDefaultModelFailsFast() {
        // model 缺省 + provider 无 defaultModel：解析期 fail-fast（不静默吞）。
        ModelClassModel bad = new ModelClassModel();
        bad.setId("bad-class");
        ModelClassCandidateModel declared = new ModelClassCandidateModel();
        declared.setProvider("test-accounts"); // test-accounts.llm.xml 无 defaultModel
        bad.setCandidates(List.of(declared));

        NopException e = assertThrows(NopException.class,
                () -> LlmConfigHelper.resolveModelClassCandidates(bad));
        assertEquals(ERR_AI_SERVICE_NO_DEFAULT_LLMS.getErrorCode(), e.getErrorCode(),
                "missing default model must fail fast with the no-default-llms error code");
    }

    private static void assertCandidate(ModelClassCandidate c, String provider, String model,
                                        String accountKey, String accountBaseUrl, Integer concurrencyLimit) {
        assertEquals(provider, c.getProvider());
        assertEquals(model, c.getModel());
        assertEquals(accountKey, c.getAccountKey());
        assertEquals(accountBaseUrl, c.getAccountBaseUrl());
        assertEquals(concurrencyLimit, c.getConcurrencyLimit(),
                "concurrency limit must be resolved at expansion time (W3 semantics)");
        assertEquals(provider + ":" + model, c.getModelKey(),
                "breaker key must be provider:model");
    }
}
