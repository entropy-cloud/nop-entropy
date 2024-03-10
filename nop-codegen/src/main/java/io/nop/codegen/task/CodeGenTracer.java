/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.task;

import io.nop.codegen.graalvm.ReflectConfigGenerator;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_DIR;
import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED;

public class CodeGenTracer {
    public static void trace(Runnable task) {
        if (CFG_CODEGEN_TRACE_ENABLED.get()) {
            ReflectionManager.instance().setRecordForNativeImage(true);
            try {
                task.run();
            } finally {
                dumpReflectionData();
                ReflectionManager.instance().setRecordForNativeImage(false);
            }
        } else {
            task.run();
        }
    }

    static void dumpReflectionData() {
        String dir = CFG_CODEGEN_TRACE_DIR.get();
        String reflectPath = StringHelper.appendPath(dir, "reflect-config.json");
        IResource reflectResource = ResourceHelper.resolveRelativePathResource(reflectPath);
        ReflectConfigGenerator.instance().generateDeltaToResource(reflectResource);
    }
}