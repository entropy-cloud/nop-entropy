/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
