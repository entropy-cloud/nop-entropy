package io.nop.integration.sftp;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-018 (plan 2523)：构造期「session 已连、channel 未开」失败必须回收 SSH 会话——
 * 源码级伴随钉（2522 R2 Major-3 先例：jsch 不可子类化使注入不可达）。
 * 当前即红：缺陷态无回收块。
 */
@Timeout(30)
public class TestSftpClientConstructionRecovery_C6_018 {

    @Test
    public void channelConnectFailureMustDisconnectSession() throws Exception {
        Path source = Path.of("src/main/java/io/nop/integration/sftp/SftpClient.java");
        String code = Files.readString(source);
        // 源码级结构钉（作用域收窄版）：channel.connect 与 openConnection 方法结束之间必须
        // 存在 session.disconnect() 回收块——全文件匹配会误命中 close() 方法的同名调用
        //（缺陷态假绿实证 @ red-r4-sftp）。
        int channelConnect = code.indexOf("channel.connect(config.getTimeoutMillis())");
        assertTrue(channelConnect > 0, "sanity: timeout-wired channel.connect must exist (C3-024)");
        int nextMethod = code.indexOf("public List<FileStatusBean> listFiles", channelConnect);
        if (nextMethod < 0)
            nextMethod = code.length();
        String block = code.substring(channelConnect, nextMethod);
        assertTrue(block.contains("session.disconnect()"),
                "C6-018: channel.connect failure path must disconnect the session"
                        + " (defect: orphaned SSH session on construction failure — caller has no reference)");
    }
}
