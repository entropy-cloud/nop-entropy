package io.nop.socket;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSocketProtocolFix {

    static ByteArrayInputStream packetFor(int len, short masks, short version) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putInt(len);
        buf.putShort(masks);
        buf.putShort(version);
        buf.rewind();
        return new ByteArrayInputStream(buf.array());
    }

    @Test
    public void testZeroLengthPacketThrowsIoException() {
        // 协议只允许 len==4（心跳）或 len>=8；len=0 是结构性非法值
        IOException e = assertThrows(IOException.class,
                () -> BinaryCommand.readPacketFromStream(packetFor(0, (short) 1, (short) 1),
                        (short) 1, 0, 1024, ByteBuffer.allocate(8)));
        assertTrue(e.getMessage().contains("too-small"), "should be the designed diagnostic, got: " + e.getMessage());
    }

    @Test
    public void testShortLengthPacketThrowsIoException() {
        // len=6 同样结构性非法（不足 masks+version+cmd+flags 8 字节头）
        assertThrows(IOException.class, () -> BinaryCommand.readPacketFromStream(
                packetFor(6, (short) 1, (short) 1), (short) 1, 0, 1024, ByteBuffer.allocate(8)));
    }

    @Test
    public void testHeartbeatPacketRoundTrip() throws IOException {
        // len=4 心跳包应正常解析
        BinaryCommand cmd = BinaryCommand.readPacketFromStream(packetFor(4, (short) 1, (short) 1),
                (short) 1, 0, 1024, ByteBuffer.allocate(8));
        assertEquals(0, cmd.getCmd());
        assertTrue(cmd.isEmptyCommand());
    }

    @Test
    public void testConnectFailureIsRetryable() {
        // 连接失败（服务器不存在）后应可以再次 connect，而不是 Guard.checkState 失败
        SocketClient client = new SocketClient();
        ClientConfig config = new ClientConfig();
        config.setHost("localhost");
        config.setPort(1); // 保留端口，几乎必然连接拒绝
        config.setConnectTimeout(500);
        client.setClientConfig(config);

        assertThrows(NopException.class, client::connect);

        // 第二次 connect 失败仍应是 NopException（连接失败），
        // 修复前这里是 IllegalStateException（socket 字段未清理）
        assertThrows(NopException.class, client::connect);
    }
}
