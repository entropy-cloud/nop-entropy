package io.nop.netty.ext;

import io.nop.core.unittest.BaseTestCase;
import io.nop.netty.ext.server.ModelBasedTcpClient;
import io.nop.netty.ext.server.ModelBasedTcpClientConfig;
import io.nop.netty.ext.server.ModelBasedTcpServer;
import io.nop.netty.ext.server.ModelBasedTcpServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

@Disabled
public class TestModelBasedTcpServer extends BaseTestCase {
    ModelBasedTcpServer server;

    ModelBasedTcpClient client;

    @BeforeEach
    public void init() {
        createServer();
        createClient();
    }

    void createServer() {
        ModelBasedTcpServerConfig config = new ModelBasedTcpServerConfig();
        server = new ModelBasedTcpServer();
        server.setConfig(config);
        server.start();
    }

    void createClient() {
        ModelBasedTcpClientConfig config = new ModelBasedTcpClientConfig();
        config.setRemotePort(server.getConfig().getPort());
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
        Map<String,Object> msg = new HashMap<>();
        msg.put("id","1");
        msg.put("name","test");

        client.send(msg, 1000);
    }
}
