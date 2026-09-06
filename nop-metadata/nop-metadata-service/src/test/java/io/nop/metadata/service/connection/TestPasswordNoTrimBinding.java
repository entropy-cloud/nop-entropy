package io.nop.metadata.service.connection;

import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * check2 P3-11（2026-08-23 审计）回归：buildDataSource 对 password 做 trim，含首尾空白的密码被静默改写。
 *
 * <p>缺陷机制：{@code requireField} 统一 {@code value.toString().trim()}——jdbcUrl/username trim 合理，
 * 但合法密码可含首尾空格（Token 型口令），trim 后与真实凭据不一致 → 建连 401 且错误信息不提示
 * "密码被改写"。mergeCredentialConfig 侧不 trim，两路径行为也不一致。
 *
 * <p>修复：password 走不 trim 的存在性检查（cfg.containsKey + 原样 toString）。
 *
 * <p>反射调用 private buildDataSource（沿 TestMetaTableProfilerProbeNumeric 先例），断言
 * SimpleDataSource 承载的 password 保留首尾空白、username 仍 trim。
 * mutate-fail：回退为 trim 路径时 getPassword() == "pad"（空白丢失）→ 断言失败。
 */
public class TestPasswordNoTrimBinding {

    /** password 保留首尾空白（不 trim）；username 维持 trim 语义。 */
    @Test
    public void testPasswordPreservesLeadingTrailingSpaces() throws Exception {
        SimpleDataSource ds = buildDataSource(
                "{\"jdbcUrl\":\"jdbc:h2:mem:pw_trim\",\"username\":\"  sa  \",\"password\":\" pad \"}");
        assertEquals(" pad ", ds.getPassword(),
                "password must be passed through verbatim (leading/trailing spaces are part of the credential)");
        assertEquals("sa", ds.getUsername(),
                "username keeps trim semantics (non-credential identifier)");
    }

    /** password 缺 key → 显式失败；null 值 → 空串（对齐 H2 空密码现状，不 NPE）。 */
    @Test
    public void testPasswordMissingKeyFailsAndNullBecomesEmpty() throws Exception {
        io.nop.api.core.exceptions.NopException ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> buildDataSource("{\"jdbcUrl\":\"jdbc:h2:mem:pw_trim2\",\"username\":\"sa\"}"),
                "missing password key must fail fast");
        assertEquals(io.nop.metadata.service.NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID.getErrorCode(),
                ex.getErrorCode());

        SimpleDataSource ds = buildDataSource(
                "{\"jdbcUrl\":\"jdbc:h2:mem:pw_trim3\",\"username\":\"sa\",\"password\":null}");
        assertEquals("", ds.getPassword(), "null password must map to empty string (H2 empty-password parity)");
    }

    private static SimpleDataSource buildDataSource(String connectionConfig) throws Exception {
        MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();
        Method m = MetaDataSourceConnectionProcessor.class.getDeclaredMethod(
                "buildDataSource", String.class, String.class);
        m.setAccessible(true);
        try {
            return (SimpleDataSource) m.invoke(service, "jdbc", connectionConfig);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        }
    }
}
