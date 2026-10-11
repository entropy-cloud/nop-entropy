/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.app;

import io.nop.boot.NopApplication;
import io.nop.core.initialize.CoreInitialization;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.event.Observes;

@QuarkusMain
public class NopFileApplication {
    static String[] globalArgs;

    public void start(@Observes StartupEvent event) {
        // 休眠裁决（R1.2 / plan 2365，2026-10-09）：本 app 不是可部署 HTTP 服务——Quarkus 集成有意不启动，
        // 依赖树亦无 quarkus web 扩展。复活路径：①恢复 io.nop.quarkus.core.QuarkusIntegration import；
        // ②取消下方 start() 调用注释；③恢复 nop-quarkus-web-orm-starter 依赖并重验证 quarkus-web 栈。
//        QuarkusIntegration.start();

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
