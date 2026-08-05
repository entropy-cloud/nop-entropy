package io.nop.metadata.service.connection;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-02/AR-03 残余变体（plan-2026-08-06-0553-1 Phase 2）：JDBC host 提取层的反例（放行）与
 * allowed-hosts 放行语义在包级可见 seam（{@link MetaDataSourceConnectionProcessor#validateJdbcUrl}）上断言，
 * 不断开真实连接（避免对 TEST-NET/外部地址发起慢速真实建连）。
 *
 * <p>拒绝向量（内部 host → ERR_DATASOURCE_JDBC_URL_BLOCKED）走既有 {@code TestMetaDataSourceConnectionSecurity}
 * 的 testConnect 真实入口断言；本类只覆盖"放行"断言（host 校验层不抛异常）。
 */
public class TestMetaDataSourceConnectionProcessorExtract {

    private final MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();

    /** 反例：带括号外网 IPv6（[2001:db8::1]:3306）必须通过 host 校验（外网放行，不触发建连）。 */
    @Test
    public void testBracketedExternalIpv6PassesHostCheck() {
        assertDoesNotThrow(() -> service.validateJdbcUrl("jdbc:mysql://[2001:db8::1]:3306/db"),
                "bracketed documentation IPv6 is external, must pass host check");
    }

    /** allowlist 语义核对：归一化后的 host（::1）与 allowed-hosts 配置项精确匹配时放行
     * （jdbc:mysql://::1:3306/db → extractHost 归一化出 "::1" → isInternalHost 命中 → allowlist 精确匹配 → 放行）。 */
    @Test
    public void testAllowlistMatchesNormalizedHost() {
        service.allowedInternalHostsCsv = "::1";
        assertDoesNotThrow(() -> service.validateJdbcUrl("jdbc:mysql://::1:3306/db"),
                "normalized host ::1 in allowed-hosts must pass host check");
    }

    /** allowlist 语义核对（互补面）：归一化后的 host 与配置项不一致时仍拒绝
     * （allowed-hosts 配 "::1:3306" 整体串不匹配归一化后的 "::1" → 拒绝，比较发生在归一化之后）。 */
    @Test
    public void testAllowlistMismatchStillRejected() {
        service.allowedInternalHostsCsv = "::1:3306";
        NopException ex = assertThrows(NopException.class,
                () -> service.validateJdbcUrl("jdbc:mysql://::1:3306/db"),
                "allowed-hosts entry must match the normalized host, not the raw host:port string");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("host"),
                "reason must mention host: " + ex.getParam("reason"));
    }
}
