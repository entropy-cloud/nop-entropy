package io.nop.netty.ext;

import io.nop.commons.util.NetHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.netty.ext.server.ModelBasedTcpClient;
import io.nop.netty.ext.server.ModelBasedTcpClientConfig;
import io.nop.netty.ext.server.ModelBasedTcpServer;
import io.nop.netty.ext.server.ModelBasedTcpServerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

@Disabled
public class TestModelBasedTcpServer extends BaseTestCase {
    ModelBasedTcpServer server;

    ModelBasedTcpClient client;

    @BeforeAll
    public static void initAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroyAll() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void init() {
        createServer();
        createClient();
    }

    void createServer() {
        ModelBasedTcpServerConfig config = new ModelBasedTcpServerConfig();
        config.setHost("localhost");
        config.setPort(NetHelper.findAvailableTcpPort());
        config.setPacketModelPath("/test/test-rpc.packet-codec.xml");
        config.setStateMachinePath("/test/test-client.fsm.xml");
        server = new ModelBasedTcpServer();
        server.setConfig(config);
        server.start();
    }

    void createClient() {
        ModelBasedTcpClientConfig config = new ModelBasedTcpClientConfig();
        config.setRemoteHost("localhost");
        config.setRemotePort(server.getConfig().getPort());
        config.setPacketModelPath("/test/test-rpc.packet-codec.xml");
        config.setStateMachinePath("/test/test-server.fsm.xml");
        client = new ModelBasedTcpClient();
        client.setConfig(config);
        client.start();
    }

    @AfterEach
    public void destroy() {
        server.stop();
        client.stop();
    }

    @Test
    public void testServer() {
        Map<String, Object> msg = new HashMap<>();
        msg.put("id", "1");
        msg.put("name", "test");

        client.send(msg, 1000);
    }
}
