/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.grpc;

import io.nop.boot.NopApplication;
import io.nop.quarkus.core.QuarkusIntegration;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.event.Observes;

@QuarkusMain
public class GrpcDemoMain {
    static String[] globalArgs;

    public void start(@Observes StartupEvent event) {
        QuarkusIntegration.start();
        new NopApplication().run(globalArgs);
    }
    public static void main(String[] args) {
        globalArgs = args;
        Quarkus.run(args);
    }
}
