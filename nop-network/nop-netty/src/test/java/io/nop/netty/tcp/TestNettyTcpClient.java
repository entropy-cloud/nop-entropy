package io.nop.netty.tcp;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.NetHelper;
import io.nop.netty.NopNettyErrors;
import org.junit.jupiter.api.Test;

import static io.nop.netty.tcp.TcpTestHelper.createClient;
import static io.nop.netty.tcp.TcpTestHelper.createMockServer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


public class TestNettyTcpClient {

    @Test
    public void testConnect() {
        int port = NetHelper.findAvailableTcpPort();
        NettyTcpClient client = createClient(port);

        try {
            FutureHelper.syncGet(client.sendAsync("123", 1000));
            fail();
        } catch (NopConnectException e) {
            assertEquals(NopNettyErrors.ERR_TCP_CONNECT_FAIL.getErrorCode(), e.getErrorCode());
        } catch (Exception e) {
            fail();
        } finally {
            client.stop();
        }
    }

    @Test
    public void testTimeout() {
        int port = NetHelper.findAvailableTcpPort();
        NettyTcpServer server = createMockServer(port);


        NettyTcpClient client = createClient(port);

        try {
            FutureHelper.syncGet(client.sendAsync("123", 1000));
            fail();
        } catch (NopConnectException e) {
            fail();
        } catch (NopTimeoutException e) {
            assertEquals(ApiErrors.ERR_TIMEOUT.getErrorCode(), e.getErrorCode());
        } finally {
            client.stop();
            server.stop();
        }
    }

}
