/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.demo.quarkus;

import io.nop.boot.NopApplication;
import io.nop.core.initialize.CoreInitialization;
import io.nop.quarkus.core.QuarkusIntegration;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.QuarkusMain;

import jakarta.enterprise.event.Observes;

@QuarkusMain
public class QuarkusDemoMain {
    // QuarkusApplication为命令行接口，执行完run方法之后就应该停止
    // @Override
    // public int run(String... args) throws Exception {
    // return new NopApplication().run(args);
    // }
    static String[] globalArgs;

    public void start(@Observes StartupEvent event) {
        QuarkusIntegration.start();
        new NopApplication().run(globalArgs);
    }

    public void stop(@Observes ShutdownEvent event) {
        CoreInitialization.destroy();
    }

    public static void main(String... args) {
        globalArgs = args;
        Quarkus.run(args);
    }
}
