package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.service.connection.MetaDataSourceConnectionProcessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-08（plan 2026-08-16-0226-1）回归测试：驱动/底层异常消息进入 error/reason param 前经
 * jdbc: URL 脱敏过滤（{@link MetaDataSourceConnectionProcessor#redactJdbcUrlsInText}）。
 *
 * <p>背景：驱动异常消息可回显完整 JDBC URL（userinfo 形态含口令，如 MySQL Connector/J
 * 建连失败消息），原样入 param 会击穿 {@code redactJdbcUrl} 对 jdbcUrl 参数的脱敏。
 *
 * <p>覆盖三个接入点中的两个连接侧接入点：
 * <ul>
 *   <li>{@code newNopConnectException}（建连点，URL 凭据回显风险最高）——经注册 stub 驱动
 *       实测 {@code withConnection} 建连失败链路（真实 DriverManager 分发）。</li>
 *   <li>{@code parseConnectionConfig}（JSON 解析异常消息可引用含 jdbcUrl 的原始输入）。</li>
 *   <li>过滤 helper 本身的对抗/零误伤断言（多形态、尾标点、边界）。</li>
 * </ul>
 * quality 侧 {@code MetaQualityRuleExecutor.messageOf} 收敛点见
 * {@code TestMetaQualityRuleExecutorMessageRedaction}。
 */
public class TestMetaDataSourceConnectionMessageRedaction {

    private final MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();

    private static final String BASE_CFG = "\"username\":\"sa\",\"password\":\"\"";

    // ===== 过滤 helper：对抗与零误伤 =====

    /** userinfo 口令形态（计划对抗向量）：口令与 userinfo 整段剥除，脱敏后形态保留可诊断。 */
    @Test
    public void testRedactJdbcUrlsInTextUserinfoForm() {
        String raw = "Failed to connect to jdbc:mysql://root:secret@10.0.0.1/db";
        String out = MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(raw);
        assertFalse(out.contains("secret"), "password must not survive: " + out);
        assertFalse(out.contains("root:secret@"), "userinfo segment must not survive: " + out);
        assertTrue(out.contains("jdbc:mysql://10.0.0.1/db"),
                "redacted URL form must be retained for diagnostics: " + out);
        assertTrue(out.startsWith("Failed to connect to"), "message prefix must be preserved: " + out);
    }

    /** 尾部收尾标点剥离：句号/右括号/引号不随 URL 匹配段被吞（脱敏后原位回填）。 */
    @Test
    public void testRedactJdbcUrlsInTextTrailingPunctuationNotSwallowed() {
        assertEquals("Cannot connect to jdbc:mysql://host/db.",
                MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(
                        "Cannot connect to jdbc:mysql://user:pass@host/db."),
                "trailing period must be preserved, not swallowed into the URL");
        assertEquals("(url=jdbc:mysql://host:3306/db)",
                MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(
                        "(url=jdbc:mysql://user:pass@host:3306/db)"),
                "closing paren must be preserved");
        assertEquals("see \"jdbc:postgresql://h/db\"",
                MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(
                        "see \"jdbc:postgresql://u:p@h/db\""),
                "closing quote must be preserved");
    }

    /** 无 jdbc: 命中的普通消息原样返回（诊断信息不丢）；null/空串原样。 */
    @Test
    public void testRedactJdbcUrlsInTextNoUrlUnchanged() {
        assertEquals("Connection refused: host unreachable",
                MetaDataSourceConnectionProcessor.redactJdbcUrlsInText("Connection refused: host unreachable"));
        assertEquals("Table 'db.t' doesn't exist",
                MetaDataSourceConnectionProcessor.redactJdbcUrlsInText("Table 'db.t' doesn't exist"));
        assertNull(MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(null));
        assertEquals("", MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(""));
        // 无 userinfo 的 URL 形态：redactJdbcUrl 不改写（无凭据可脱），消息保持原样
        assertEquals("linked to jdbc:mysql://10.0.0.1:3306/db (no credentials)",
                MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(
                        "linked to jdbc:mysql://10.0.0.1:3306/db (no credentials)"));
    }

    /** 多 URL 消息：每个匹配段独立脱敏。 */
    @Test
    public void testRedactJdbcUrlsInTextMultipleUrls() {
        String raw = "failover from jdbc:mysql://u1:p1@h1/db to jdbc:postgresql://u2:p2@h2/db";
        String out = MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(raw);
        assertFalse(out.contains("p1") || out.contains("p2"), "both passwords must be redacted: " + out);
        assertTrue(out.contains("jdbc:mysql://h1/db") && out.contains("jdbc:postgresql://h2/db"),
                "both redacted forms retained: " + out);
    }

    /**
     * 已知边界钉死（F6 既有裁定语义）：query 形态口令（{@code ?user=x&password=y}）与无
     * {@code ://} 的 Oracle thin 形态不在脱敏范围——本断言钉死该边界不被误认为已覆盖，防
     * 第三轮审计复发误报。
     */
    @Test
    public void testRedactJdbcUrlsInTextQueryFormBoundaryDocumented() {
        String queryForm = "connect jdbc:postgresql://example.com/db?user=x&password=hunter2 failed";
        assertEquals(queryForm, MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(queryForm),
                "query-form password is out of redactJdbcUrl scope (F6 adjudicated boundary)");
        String oracleThin = "oracle jdbc:oracle:thin:@//no-authority-form refused";
        assertEquals(oracleThin, MetaDataSourceConnectionProcessor.redactJdbcUrlsInText(oracleThin),
                "no :// Oracle thin form is out of redactJdbcUrl scope (F6 adjudicated boundary)");
    }

    // ===== 接入点 1：newNopConnectException（建连点，经真实 DriverManager 分发）=====

    /**
     * <b>P2-08 接线验证</b>：注册 stub 驱动接管 {@code jdbc:postgresql:} URL（test classpath 无
     * PostgreSQL 驱动，无分发冲突），抛出携带 userinfo 口令 URL 的模拟驱动消息——
     * {@code withConnection} 建连失败链路产出的 error param 必须脱敏。
     */
    @Test
    public void testNewNopConnectExceptionFiltersDriverMessage() throws Exception {
        ThrowingStubDriver stub = new ThrowingStubDriver(
                "Failed to connect to jdbc:postgresql://root:secret@example.com:5432/db (connection refused)");
        DriverManager.registerDriver(stub);
        try {
            BiConsumer<Connection, java.sql.DatabaseMetaData> noop = (c, m) -> {
            };
            NopException ex = assertThrows(NopException.class, () -> service.withConnection("jdbc",
                    "{\"jdbcUrl\":\"jdbc:postgresql://root:secret@example.com:5432/db\"," + BASE_CFG + "}",
                    noop),
                    "stub driver failure must surface as ERR_DATASOURCE_CONNECT_FAILED");
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_CONNECT_FAILED.getErrorCode(), ex.getErrorCode());
            String error = String.valueOf(ex.getParam("error"));
            assertFalse(error.contains("secret"), "error param must not carry the password: " + error);
            assertFalse(error.contains("root:secret@"), "error param must not carry userinfo: " + error);
            assertTrue(error.contains("jdbc:postgresql://example.com:5432/db"),
                    "redacted URL form retained for diagnostics: " + error);
            assertTrue(error.contains("Failed to connect to"), "driver message context retained: " + error);
            assertFalse(String.valueOf(ex.getMessage()).contains("secret"),
                    "rendered exception message must not carry the password: " + ex.getMessage());
            assertNotNull(ex.getCause(), "original SQLException preserved in cause chain");
            assertSame(SQLException.class, ex.getCause().getClass(),
                    "cause remains the original driver SQLException");
        } finally {
            DriverManager.deregisterDriver(stub);
        }
    }

    /** 建连点零误伤：不含 jdbc: 形态的驱动消息原样保留（诊断信息不丢）。 */
    @Test
    public void testNewNopConnectExceptionPlainMessageUnchanged() throws Exception {
        ThrowingStubDriver stub = new ThrowingStubDriver("Socket timeout while establishing connection");
        DriverManager.registerDriver(stub);
        try {
            BiConsumer<Connection, java.sql.DatabaseMetaData> noop = (c, m) -> {
            };
            NopException ex = assertThrows(NopException.class, () -> service.withConnection("jdbc",
                    "{\"jdbcUrl\":\"jdbc:postgresql://example.com:5432/db\"," + BASE_CFG + "}", noop));
            assertEquals("Socket timeout while establishing connection", String.valueOf(ex.getParam("error")),
                    "plain driver message must pass through verbatim (no diagnostics loss)");
        } finally {
            DriverManager.deregisterDriver(stub);
        }
    }

    // ===== 接入点 2：parseConnectionConfig（JSON 解析异常消息引用原始输入）=====

    /**
     * <b>P2-08 接线验证</b>：非法 connectionConfig 的 JSON 解析异常消息引用原始输入片段
     * （Nop 解析器 readerState 上下文窗回显输入文本——先用前置断言钉死该泄漏面成立，实测含
     * 凭据中段片段且无 {@code jdbc:} 前缀锚定）。connectionConfig 是凭据载体：reason param
     * 不回显解析器消息（抑制处理），原始异常经 cause 链保留供日志诊断。
     */
    @Test
    public void testParseConnectionConfigInvalidJsonReasonRedacted() {
        String badConfig = "{\"username\":\"sa\",\"jdbcUrl\":\"jdbc:postgresql://root:secret@example.com:5432/db\"";
        // 前置断言：原始解析异常消息确实引用了含口令的输入（泄漏面成立，抑制非空转）。
        // 消息断言放在 catch 块外（java-lint-getmessage-only：catch 内裸 getMessage 形态违约）
        Exception rawParseError = null;
        try {
            JsonTool.parseBeanFromText(badConfig, Object.class);
        } catch (Exception raw) {
            rawParseError = raw;
        }
        assertNotNull(rawParseError, "expected JsonTool parse failure for malformed config");
        assertTrue(String.valueOf(rawParseError.getMessage()).contains("secret"),
                "precondition: raw JSON parse error message echoes the credential-bearing input");
        NopException ex = assertThrows(NopException.class,
                () -> service.withConnection("jdbc", badConfig, (c, m) -> { }),
                "malformed connectionConfig must fail fast");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID.getErrorCode(), ex.getErrorCode());
        String reason = String.valueOf(ex.getParam("reason"));
        assertFalse(reason.contains("secret"), "reason must not carry the password: " + reason);
        assertFalse(reason.contains("jdbc:postgresql"), "reason must not echo raw input: " + reason);
        assertTrue(reason.contains("not valid JSON"),
                "reason must still identify the failure class: " + reason);
        assertFalse(String.valueOf(ex.getMessage()).contains("secret"),
                "rendered exception message must not echo the credential: " + ex.getMessage());
        assertNotNull(ex.getCause(), "original parse exception preserved in cause chain for log diagnostics");
    }

    /** 测试桩：接管 jdbc:postgresql: URL 的建连并抛出受控驱动消息（注册/注销由调用方管理）。 */
    private static final class ThrowingStubDriver implements Driver {
        private final String failMessage;

        ThrowingStubDriver(String failMessage) {
            this.failMessage = failMessage;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            throw new SQLException(failMessage);
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:postgresql:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }
    }
}
