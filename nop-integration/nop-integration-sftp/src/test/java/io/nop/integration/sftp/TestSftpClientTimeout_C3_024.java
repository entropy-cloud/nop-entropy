package io.nop.integration.sftp;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3-024 (plan 2522): the whole SFTP connect chain must be bounded — jsch defaults are
 * 0 (infinite), so a network black hole hangs the business thread forever.
 *
 * <p>Evidence form (per design §5.1/裁定⑦): a banner black-hole probe proves the
 * timeout envelope takes effect, plus a SOURCE-level companion pin asserting the three
 * timeout wirings exist (session.setTimeout / session.connect(ms) / channel.connect(ms)) —
 * jsch Session/Channel cannot be subclassed across packages, so call parameters are not
 * capturable by a recording fake (R2 Major-3 resolution).</p>
 */
@Timeout(60)
public class TestSftpClientTimeout_C3_024 {

    /**
     * Source-level companion pin: the three timeout wirings must be present in
     * SftpClient — session.setTimeout(...), session.connect(ms), channel.connect(ms).
     * Deterministic red on the defect state (all three absent / no-arg).
     */
    @Test
    public void timeoutWiringPresentInSource() throws Exception {
        Path source = Path.of("src/main/java/io/nop/integration/sftp/SftpClient.java");
        String code = Files.readString(source);
        assertTrue(code.contains("session.setTimeout(config.getTimeoutMillis())"),
                "C3-024: session.setTimeout must be wired from the config (socket read envelope)");
        assertTrue(code.contains("session.connect(config.getTimeoutMillis())"),
                "C3-024: session.connect must carry the connect timeout (jsch no-arg connect = infinite)");
        assertTrue(code.contains("channel.connect(config.getTimeoutMillis())"),
                "C3-024: channel.connect must carry the timeout (the only non-redundant wiring —"
                        + " no-arg Channel.connect passes literal 0)");
    }

    /**
     * Banner black-hole probe: a TCP server that accepts but never sends the SSH
     * banner. Pre-fix the jsch session connect hangs forever (business thread hung);
     * post-fix the configured connect timeout fails fast with ERR_SFTP_CONNECT_FAIL.
     */
    @Test
    public void bannerBlackHoleFailsWithinConfiguredTimeout() throws Exception {
        ServerSocket blackHole = new ServerSocket(0);
        Thread acceptor = new Thread(() -> {
            while (!blackHole.isClosed()) {
                try {
                    Socket socket = blackHole.accept();
                    // accept and then stay silent — never send the SSH banner
                } catch (Exception e) {
                    break;
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
        try {
            SftpConfig config = new SftpConfig();
            config.setHost("127.0.0.1");
            config.setPort(blackHole.getLocalPort());
            config.setUsername("probe");
            config.setPassword("probe");
            // short budget so the post-fix fail-fast is observable inside the window
            config.setTimeoutMillis(500);

            // construction IS the connect path (the constructor drives this.connect())
            CompletableFuture<SftpClient> connect = CompletableFuture.supplyAsync(() -> {
                SftpClient client = new SftpClient(config) {
                    @Override
                    protected JSch newJsch() {
                        return new JSch();
                    }
                };
                client.close();
                return client;
            });
            // 失败模式 = 超时（defect: connect hangs forever on the banner read）。
            // fail-fast 判别：connect future 必须在窗口内到达异常终态（500ms 预算 + 5s 窗口）——
            // get 超时（future 未完成）即挂死形态，不得视为通过。
            try {
                connect.get(5, TimeUnit.SECONDS);
                throw new AssertionError(
                        "C3-024: banner black hole must fail the connect (defect: jsch 0 = infinite hang)");
            } catch (java.util.concurrent.ExecutionException e) {
                assertTrue(connect.isCompletedExceptionally(),
                        "connect must reach an exceptional terminal state within the window");
            }
        } finally {
            blackHole.close();
        }
    }
}
