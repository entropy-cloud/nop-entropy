/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.socket;

import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    public void testDisconnectRemovesConnection() throws Exception {
        SocketServer server = new SocketServer();
        server.setCommandHandler((addr, request) -> request);
        server.start();
        try {
            Socket client = new Socket("127.0.0.1", server.getPort());
            try {
                client.setSoTimeout(5000);

                // 完成一次请求-响应，确保连接已被 accept 线程注册
                BinaryCommand request = BinaryCommand.newCommand(server.getServerConfig(), (short) 0x1, (short) 0,
                        "abc");
                BinaryCommand.writePacketToStream(request, client.getOutputStream());
                client.getOutputStream().flush();

                ServerConfig config = server.getServerConfig();
                BinaryCommand response = BinaryCommand.readPacketFromStream(client.getInputStream(),
                        config.getMasks(), config.getMinDataLen(), config.getMaxDataLen(), ByteBuffer.allocate(8));
                assertEquals("abc", response.getDataAsString());

                // 注册使用的 key 与 getConnectionKey(Socket) 公开方法必须一致
                String key = server.getConnectionKey(client.getLocalAddress().getHostAddress(), client.getLocalPort());
                assertNotNull(server.getConnection(key));

                // getConnectionKey(Socket) 必须使用 getHostAddress() 形式，不允许 InetAddress.toString() 的 '/' 前缀
                String publicKey = server.getConnectionKey(client);
                String expectedPublicKey = client.getInetAddress().getHostAddress() + ':' + client.getPort();

                client.close();

                // 客户端断开后服务端应清理 connections 表，不允许残留已关闭的 socket
                long deadline = System.currentTimeMillis() + 10_000;
                while (server.getConnection(key) != null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100);
                }
                assertNull(server.getConnection(key), "client disconnect should remove connection entry");

                assertEquals(expectedPublicKey, publicKey);
            } finally {
                client.close();
            }
        } finally {
            server.stop();
        }
    }
}