/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import graphql.api.GreeterGrpc;
import graphql.api.Hello;
import org.junit.jupiter.api.Test;

public class TestGrpcClient {
    static final int PORT = 50031;

    @Test
    public void testRequest() throws Exception {

        Server server = ServerBuilder.forPort(PORT)
                .addService(new HelloServiceImpl())
                .build();
        server.start();

        testHello();

        server.shutdown();
    }

    void testHello() {
        // 创建一个 gRPC 通道
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", PORT)
                .usePlaintext()
                .build();
        // 创建一个 HelloService 的客户端存根
        GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        // 创建一个 HelloRequest 对象
        Hello.HelloRequest request = Hello.HelloRequest.newBuilder()
                .setName("John")
                .build();
        // 调用 gRPC 服务器的 sayHello 方法
        Hello.HelloReply response = stub.sayHello(request);
        // 打印响应结果
        System.out.println(response.getMessage());
        // 关闭通道
        channel.shutdown();
    }

    private static class HelloServiceImpl extends GreeterGrpc.GreeterImplBase {
        @Override
        public void sayHello(Hello.HelloRequest request, StreamObserver<Hello.HelloReply> responseObserver) {
            String name = request.getName();
            String message = "Hello, " + name + "!";
            Hello.HelloReply response = Hello.HelloReply.newBuilder()
                    .setMessage(message)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
