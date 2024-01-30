package io.nop.quarkus.grpc;

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class SimpleServer {
    public static void main(String[] args) {
        try {
            // 生成 gRPC 服务器的启动代码
            Server server = ServerBuilder.forPort(50051)
                    //.addService(new HelloServiceImpl())
                    .build();
            server.start();

            server.awaitTermination();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
