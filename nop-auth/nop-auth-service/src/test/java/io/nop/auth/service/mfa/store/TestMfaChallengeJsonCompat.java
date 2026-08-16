/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.commons.util.ClassHelper;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.PrefixEncodeHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W12-impl Phase 2：Redis 滚动升级兼容（设计 §3.5 migration note 验证）。
 * <p>
 * Redis 实现的存储契约是 {@code PrefixEncodeHelper} 的 {@code $d:类名\n + json}：
 * <ul>
 *   <li><b>新进程读老 JSON</b>（老进程未写 scene/payload/verifiedAt）：新字段反序列化为
 *       null——天然兼容，测试钉定。</li>
 *   <li><b>老进程读新 JSON</b>（新字段作为 unknown prop 出现）：行为由
 *       {@code nop.core.json.parse-ignore-unknown-prop} 控制（平台缺省 false）。
 *       测试钉定"开启该配置后容忍"这一迁移缓解措施；缺省行为同时被记录
 *       （challenge TTL 300s，滚动升级预留 5 分钟排空窗口即可完全规避）。</li>
 * </ul>
 */
public class TestMfaChallengeJsonCompat {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /** 模拟滚动升级中"无新字段知识"的老进程内存形态（一期 8 字段）。 */
    public static class OldMfaChallengeShape {
        private String challengeToken;
        private String userId;
        private String mfaType;
        private int loginType;
        private String tenantId;
        private String phone;
        private long createdAt;
        private long expireAt;

        public String getChallengeToken() {
            return challengeToken;
        }

        public void setChallengeToken(String challengeToken) {
            this.challengeToken = challengeToken;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getMfaType() {
            return mfaType;
        }

        public void setMfaType(String mfaType) {
            this.mfaType = mfaType;
        }

        public int getLoginType() {
            return loginType;
        }

        public void setLoginType(int loginType) {
            this.loginType = loginType;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }

        public long getExpireAt() {
            return expireAt;
        }

        public void setExpireAt(long expireAt) {
            this.expireAt = expireAt;
        }
    }

    private static MfaChallenge newShapeChallenge() {
        MfaChallenge c = new MfaChallenge("tok-1", "user-1", "totp", 1, "t0", "13800000000",
                1000L, 2000L);
        c.setScene(MfaChallenge.SCENE_OPERATION);
        c.setPayload("{\"operation\":\"NopAuthUser__resetUserMfa\",\"sessionId\":\"sess-1\"}");
        c.setVerifiedAt(1500L);
        return c;
    }

    @Test
    public void testPrefixEncodingRoundTripKeepsNewFields() {
        MfaChallenge c = newShapeChallenge();
        String encoded = PrefixEncodeHelper.encode(c);
        assertTrue(encoded.startsWith("$d:" + MfaChallenge.class.getName()),
                "encoded value must use $d:className header");

        Object decoded = PrefixEncodeHelper.decode(encoded, ClassHelper.getSafeClassLoader());
        assertTrue(decoded instanceof MfaChallenge);
        MfaChallenge back = (MfaChallenge) decoded;
        assertEquals(MfaChallenge.SCENE_OPERATION, back.getScene());
        assertEquals(c.getPayload(), back.getPayload());
        assertEquals(1500L, back.getVerifiedAt());
        assertEquals("user-1", back.getUserId());
    }

    @Test
    public void testNewProcessReadsOldJsonWithoutNewFields() {
        // 老进程写入的 JSON（无 scene/payload/verifiedAt 键）——手工构造同形 JSON
        String oldJson = "{\"challengeToken\":\"tok-2\",\"userId\":\"user-2\",\"mfaType\":\"totp\","
                + "\"loginType\":1,\"tenantId\":\"t0\",\"phone\":null,\"createdAt\":100,\"expireAt\":200}";
        MfaChallenge parsed = JsonTool.parseBeanFromText(oldJson, MfaChallenge.class);
        assertNotNull(parsed);
        assertEquals("user-2", parsed.getUserId());
        assertNull(parsed.getScene(), "absent scene deserializes to null (legacy = login scene)");
        assertNull(parsed.getPayload());
        assertNull(parsed.getVerifiedAt());
    }

    @Test
    public void testOldProcessReadsNewJsonTolerantWhenIgnoreUnknownPropEnabled() {
        String newJson = PrefixEncodeHelper.encode(newShapeChallenge());
        // 去掉 $d: 头，直接以老形态类解析 JSON 体（模拟老进程按类名解码到自己的旧类）
        String body = newJson.substring(newJson.indexOf('\n') + 1);

        // 平台缺省配置钉定（migration note 事实基础）
        IConfigProvider provider = AppConfig.getConfigProvider();
        Boolean original = provider.getConfigValue("nop.core.json.parse-ignore-unknown-prop", Boolean.FALSE);

        // 缺省（false）下的实际行为：可能拒绝（Jackson FAIL_ON_UNKNOWN_PROPERTIES）——记录事实，不作断言分支
        boolean rejectedByDefault;
        try {
            JsonTool.parseBeanFromText(body, OldMfaChallengeShape.class);
            rejectedByDefault = false;
        } catch (Exception e) {
            rejectedByDefault = true;
        }

        // 迁移缓解措施必须可行：开启 ignore-unknown-prop 后老形态类可读新 JSON
        provider.assignConfigValue("nop.core.json.parse-ignore-unknown-prop", true);
        try {
            OldMfaChallengeShape parsed = JsonTool.parseBeanFromText(body, OldMfaChallengeShape.class);
            assertNotNull(parsed);
            assertEquals("user-1", parsed.getUserId(), "known fields must survive unknown-prop tolerance");
            assertEquals(2000L, parsed.getExpireAt());

            // 恢复缺省后再验证行为一致性（若缺省拒绝，则迁移 note 生效；若缺省容忍则天然兼容）
            provider.assignConfigValue("nop.core.json.parse-ignore-unknown-prop",
                    original != null ? original : Boolean.FALSE);
            boolean rejectedAfterRestore;
            try {
                JsonTool.parseBeanFromText(body, OldMfaChallengeShape.class);
                rejectedAfterRestore = false;
            } catch (Exception e) {
                rejectedAfterRestore = true;
            }
            assertEquals(rejectedByDefault, rejectedAfterRestore,
                    "restoring default config must restore the original behavior");
        } finally {
            provider.assignConfigValue("nop.core.json.parse-ignore-unknown-prop",
                    original != null ? original : Boolean.FALSE);
        }
    }
}
