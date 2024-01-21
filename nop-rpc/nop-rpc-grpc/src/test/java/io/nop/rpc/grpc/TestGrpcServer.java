package io.nop.rpc.grpc;

import graphql.api.GreeterGrpc;
import graphql.api.Hello;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.rpc.grpc.server.GrpcServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGrpcServer extends JunitBaseTestCase {
    static final int PORT = 8090;

    @Inject
    GrpcServer grpcServer;

    @Test
    public void testService() {
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
        assertEquals("John-result", response.getMessage());
        // 关闭通道
        channel.shutdown();
    }
}
