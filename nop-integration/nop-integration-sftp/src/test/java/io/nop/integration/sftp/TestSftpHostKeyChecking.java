package io.nop.integration.sftp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.integration.sftp.SftpErrors.ERR_SFTP_CONNECT_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G12-13-02 回归：SftpClient 的 SSH 主机密钥校验改为可配置（{@code SftpConfig
 * #strictHostKeyChecking}），两态行为均有测试钉死：
 *
 * <ul>
 *   <li><b>默认（false，现网兼容）</b>：session 配置 {@code StrictHostKeyChecking=no}
 *       （行为不变），且每次建连输出显著 WARN（不留静默面，消息说明 MITM 风险与
 *       开启方式）；</li>
 *   <li><b>显式开启（true）</b>：session 配置 {@code StrictHostKeyChecking=yes}，且不输出
 *       该 WARN；可选 {@code knownHostsPath} 经 {@code JSch#setKnownHosts} 真实装载。</li>
 * </ul>
 *
 * <p>离线驱动方式沿用 {@code TestSftpClientCredential} 的 JschLevel 先例：RecordingJsch
 * 返回真实离线 Session（getSession 离线安全），真实 {@code openConnection} 在
 * {@code session.connect()} 处因网络不可达失败（包装为 {@code ERR_SFTP_CONNECT_FAIL}），
 * 但 setConfig 已先行生效——直接检查 Session 上的最终配置。
 */
public class TestSftpHostKeyChecking {

    private static final String WARN_MARK = "nop.sftp.host-key-checking-disabled";

    /** 记录型 jsch：捕获 setKnownHosts 路径 + 返回真实离线 Session。 */
    static class HostKeyRecordingJsch extends JSch {
        String knownHostsPath;
        Session lastSession;

        @Override
        public void setKnownHosts(String fov) throws com.jcraft.jsch.JSchException {
            this.knownHostsPath = fov;
            super.setKnownHosts(fov);
        }

        @Override
        public Session getSession(String username, String host, int port) throws com.jcraft.jsch.JSchException {
            this.lastSession = super.getSession(username, host, port);
            return this.lastSession;
        }
    }

    static HostKeyRecordingJsch LAST_JSCH;

    /** 真实 openConnection 路径 + 记录型 jsch（connect 离线拒绝，setConfig 已生效）。 */
    static class JschLevelSftpClient extends SftpClient {
        JschLevelSftpClient(SftpConfig config) {
            super(config, null);
        }

        @Override
        protected JSch newJsch() {
            LAST_JSCH = new HostKeyRecordingJsch();
            return LAST_JSCH;
        }
    }

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        LAST_JSCH = null;
        logger = (Logger) LoggerFactory.getLogger(SftpClient.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private List<String> warnMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    /** 构造即建连（离线必然失败并包装 ERR_SFTP_CONNECT_FAIL），失败本身即取证点。 */
    private static void connectExpectingOfflineFailure(SftpConfig cfg) {
        NopException ex = assertThrowsConnectFail(cfg);
        assertEquals(ERR_SFTP_CONNECT_FAIL.getErrorCode(), ex.getErrorCode(),
                "offline connect failure must keep the existing ERR_SFTP_CONNECT_FAIL wrap");
    }

    private static NopException assertThrowsConnectFail(SftpConfig cfg) {
        try {
            new JschLevelSftpClient(cfg);
        } catch (NopException e) {
            return e;
        }
        throw new AssertionError("expected offline connect failure (ERR_SFTP_CONNECT_FAIL)");
    }

    private static SftpConfig baseConfig() {
        SftpConfig config = new SftpConfig();
        config.setHost("no-such-host.invalid");
        config.setPort(22);
        config.setUsername("u");
        config.setPassword("p");
        return config;
    }

    @Test
    public void defaultKeepsCheckingDisabledWithWarn() {
        // 未配置 = 默认 false：行为保持现网兼容（no），但必须输出 WARN
        connectExpectingOfflineFailure(baseConfig());

        assertEquals("no", LAST_JSCH.lastSession.getConfig("StrictHostKeyChecking"),
                "default (strictHostKeyChecking unset) must keep the current "
                        + "StrictHostKeyChecking=no behaviour");
        assertNull(LAST_JSCH.knownHostsPath, "knownHosts must not be loaded when checking is disabled");

        List<String> warns = warnMessages();
        assertTrue(warns.stream().anyMatch(m -> m.contains(WARN_MARK)),
                "connecting with host key checking disabled must emit a WARN; got: " + warns);
        assertTrue(warns.stream().anyMatch(m -> m.contains("man-in-the-middle")),
                "the WARN must explain the MITM risk; got: " + warns);
        assertTrue(warns.stream().anyMatch(m -> m.contains("strictHostKeyChecking=true")),
                "the WARN must point at the opt-in switch; got: " + warns);
    }

    @Test
    public void explicitlyDisabledAlsoWarns() {
        SftpConfig cfg = baseConfig();
        cfg.setStrictHostKeyChecking(false);
        connectExpectingOfflineFailure(cfg);

        assertEquals("no", LAST_JSCH.lastSession.getConfig("StrictHostKeyChecking"),
                "explicit false must map to StrictHostKeyChecking=no");
        assertTrue(warnMessages().stream().anyMatch(m -> m.contains(WARN_MARK)),
                "explicitly disabled checking must also WARN (no silent unsafe path)");
    }

    @Test
    public void strictEnabledSetsCheckingYesWithoutWarn() {
        SftpConfig cfg = baseConfig();
        cfg.setStrictHostKeyChecking(true);
        connectExpectingOfflineFailure(cfg);

        assertEquals("yes", LAST_JSCH.lastSession.getConfig("StrictHostKeyChecking"),
                "strictHostKeyChecking=true must map to StrictHostKeyChecking=yes");
        assertTrue(warnMessages().stream().noneMatch(m -> m.contains(WARN_MARK)),
                "verified mode must not emit the host-key-checking-disabled WARN; got: "
                        + warnMessages());
    }

    @Test
    public void strictWithKnownHostsPathLoadsKnownHosts() throws Exception {
        Path knownHosts = tempDir.resolve("known_hosts");
        Files.write(knownHosts, new byte[0]);

        SftpConfig cfg = baseConfig();
        cfg.setStrictHostKeyChecking(true);
        cfg.setKnownHostsPath(knownHosts.toString());
        connectExpectingOfflineFailure(cfg);

        assertEquals("yes", LAST_JSCH.lastSession.getConfig("StrictHostKeyChecking"));
        assertEquals(knownHosts.toString(), LAST_JSCH.knownHostsPath,
                "knownHostsPath must be really loaded via JSch.setKnownHosts in strict mode");
    }

    @Test
    public void knownHostsPathIgnoredWhenCheckingDisabled() throws Exception {
        Path knownHosts = tempDir.resolve("known_hosts");
        Files.write(knownHosts, new byte[0]);

        SftpConfig cfg = baseConfig();
        cfg.setKnownHostsPath(knownHosts.toString());
        connectExpectingOfflineFailure(cfg);

        assertEquals("no", LAST_JSCH.lastSession.getConfig("StrictHostKeyChecking"));
        assertNull(LAST_JSCH.knownHostsPath,
                "knownHostsPath must be ignored while checking is disabled");
        assertTrue(warnMessages().stream().anyMatch(m -> m.contains(WARN_MARK)));
    }
}
