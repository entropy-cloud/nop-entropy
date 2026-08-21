/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.java.translator.ExecToJavaTranslator;
import io.nop.xlang.java.translator.GeneratedJavaSource;
import io.nop.xlang.xpl.xlib.XplTagLib;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 标签夹具再生成载体（I11，{@code GeneratedFixtureMain} 同款先例）：从测试 VFS 的
 * {@code /test/xlang-java-gen/tags.xlib} 生成 Sum 标签夹具源码到
 * {@code src/test/java/io/nop/xlang/gen/}（手动运行，写盘后提交）。
 */
public class TagFixtureMain {

    private static final String LIB_PATH = "/test/xlang-java-gen/tags.xlib";

    public static void main(String[] args) throws Exception {
        CoreInitialization.initialize();
        try {
            XplTagLib lib = (XplTagLib) ResourceComponentManager.instance().loadComponentModel(LIB_PATH);
            ExecutableFunction fn = (ExecutableFunction) lib.getTag("Sum").getFunctionModel().getInvoker();
            GeneratedJavaSource src = new ExecToJavaTranslator()
                    .translateTagUnit(LIB_PATH + "#Sum", fn);
            Path target = Path.of("src", "test", "java",
                    src.getClassName().replace('.', '/') + ".java");
            Files.createDirectories(target.getParent());
            Files.writeString(target, src.getCode());
            System.out.println("written: " + target.toAbsolutePath());
        } finally {
            CoreInitialization.destroy();
        }
    }
}
