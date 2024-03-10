/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.socket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSocketServer {
    @Test
    public void testEcho() {
        SocketServer server = new SocketServer();
        server.setCommandHandler((addr, request) -> {
            return request;
        });
        server.start();

        SocketClient client = new SocketClient();
        ClientConfig config = new ClientConfig();
        config.setReadTimeout(0);
        config.setPort(server.getPort());
        client.setClientConfig(config);
        client.connect();

        BinaryCommand request = new BinaryCommand(config.getMasks(), (short) 0x1, (short) 1, (short) 0, "abc");
        client.send(request, true);
        BinaryCommand response = client.recv();
        assertEquals("abc", response.getDataAsString());
        assertEquals(1, response.getCmd());

        request = new BinaryCommand(config.getMasks(), (short) 0x1, (short) 1, (short) 0, "bcd");
        client.ping();
        response = client.call(request);
        assertEquals("bcd", response.getDataAsString());
        assertEquals(1, response.getCmd());

        for (int i = 0; i < 300; i++) {
            request = new BinaryCommand(config.getMasks(), (short) 0x1, (short) 1, (short) 0, "bcd" + i);
            client.send(request, false);
        }

        for (int i = 0; i < 300; i++) {
            response = client.recv();
            assertEquals("bcd" + i, response.getDataAsString());
            assertEquals(1, response.getCmd());
        }

        client.close();

        server.stop();
    }
}