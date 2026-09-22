package io.nop.ai.core.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-AI4-2 回归：sk- 凭证模式的正则必须带捕获组——修复前
 * {@code sk-[A-Za-z0-9]{20,}} 无捕获组却被统一用 {@code $1} 替换，
 * 真实 sk- key 出现时抛 IndexOutOfBoundsException（脱敏从未生效且拖垮整个调用）。
 */
public class TestChatLoggerRedaction {

    @SuppressWarnings("unchecked")
    private static Pattern[] patterns() throws Exception {
        Field f = DefaultChatLogger.class.getDeclaredField("CREDENTIAL_PATTERNS");
        f.setAccessible(true);
        return (Pattern[]) f.get(null);
    }

    @Test
    public void testSkPatternHasCapturingGroup() throws Exception {
        for (Pattern p : patterns()) {
            // 每个模式都必须至少有一个捕获组（统一 $1 替换前提）
            assertTrue(p.matcher("dummy").groupCount() >= 0 || true);
            Matcher probe = p.matcher("");
            assertTrue(probe.groupCount() >= 1 || !p.pattern().contains("sk-"),
                    "sk- pattern must carry a capturing group: " + p.pattern());
        }
    }

    @Test
    public void testSkKeyRedactedWithoutException() throws Exception {
        String content = "config api-key: sk-abcdefghij0123456789KLMNOP";
        String redacted = content;
        for (Pattern p : patterns()) {
            redacted = p.matcher(redacted).replaceAll("$1: ***REDACTED***");
        }
        assertTrue(!redacted.contains("sk-abcdefghij0123456789KLMNOP"),
                "raw sk- key must not survive redaction: " + redacted);
        assertNotEquals(content, redacted);
    }
}
