/**
 * StartupInfoLogger 敏感值掩码回归测试：
 * 修复前掩码规则仅覆盖 password 类名称，TOKEN/SECRET/ACCESS_KEY/PRIVATE_KEY 等命名的
 * 凭据在 debug 模式启动日志中明文输出；修复后扩充凭据类关键词。
 */
package io.nop.boot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestStartupInfoLoggerEncodeValue {

    static class TestableStartupInfoLogger extends StartupInfoLogger {
        TestableStartupInfoLogger() {
            super(Object.class);
        }

        String encode(String name, String value) {
            return encodeValue(name, value);
        }
    }

    private final TestableStartupInfoLogger logger = new TestableStartupInfoLogger();

    @Test
    public void testCredentialLikeNamesAreMasked() {
        // 修复前：以下命名不含 "password"，值明文输出
        assertEquals("nop.auth.token=***", logger.encode("nop.auth.token", "jwt-secret-value"));
        assertEquals("aws.access_key=***", logger.encode("aws.access_key", "AKIA1234567890"));
        assertEquals("my.private_key=***", logger.encode("my.private_key", "-----BEGIN-----"));
        assertEquals("db.secret=***", logger.encode("db.secret", "s3cr3t"));
        assertEquals("service.credential=***", logger.encode("service.credential", "user:pass"));
        assertEquals("api-key=***", logger.encode("api-key", "abcd1234"));
        assertEquals("jdbc.password=***", logger.encode("jdbc.password", "unchanged-rule"));
        assertEquals("nop.userpass=***", logger.encode("nop.userpass", "unchanged-rule"));
        assertEquals("x.secret.y=***", logger.encode("x.secret.y", "unchanged-rule"));
    }

    @Test
    public void testNonSensitiveNamesStayReadable() {
        assertEquals("java.version=21", logger.encode("java.version", "21"));
        assertEquals("user.dir=/opt/app", logger.encode("user.dir", "/opt/app"));
        assertEquals("nop.application.name=demo", logger.encode("nop.application.name", "demo"));
        // "keywords" 含 "key" 子串但非凭据命名——本实现按 token/credential/access_key 等
        // 更精确的关键词匹配，普通单词不受影响
        assertEquals("search.keywords=a,b", logger.encode("search.keywords", "a,b"));
    }

    @Test
    public void testSecValuePrefixStillMasked() {
        assertEquals("v=@sec::***", logger.encode("v", "@sec:abc123"));
    }
}
