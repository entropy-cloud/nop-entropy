/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.EmailCodeStoreConfig;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W15-impl：{@link EmailCodeEntry} 真实 PrefixTextCodec 序列化路径核对（W12 教训——
 * FakeNosql 绕过序列化，@DataBean 缺失只经真实编码路径暴露）。同型先例
 * {@code TestMfaChallengeJsonCompat#testPrefixEncodingRoundTripKeepsNewFields}。
 */
public class TestEmailCodeEntrySerialization {

    @Test
    public void testEmailCodeEntryIsDataBeanSerializable() {
        EmailCodeEntry entry = new EmailCodeEntry("123456", 987654321L);

        // 真实编码路径：平台 JSON 序列化缺省仅允许 @DataBean（serialize-only-data-bean=true）
        String json = JsonTool.stringify(entry);
        assertTrue(json.contains("123456"));
        assertTrue(json.contains("987654321"));

        EmailCodeEntry back = JsonTool.parseBeanFromText(json, EmailCodeEntry.class);
        assertEquals(entry, back, "round-trip must preserve code and expireAtMillis");
    }
}
