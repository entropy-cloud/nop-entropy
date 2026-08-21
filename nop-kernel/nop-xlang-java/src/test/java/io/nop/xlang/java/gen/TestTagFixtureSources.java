/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.java.translator.ExecToJavaTranslator;
import io.nop.xlang.java.translator.GeneratedJavaSource;
import io.nop.xlang.xpl.xlib.XplTagLib;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标签夹具反漂移护栏（I11，{@code TestGeneratedFixtureSources} 同款机制）：提交的
 * {@code Gen__test_xlang_java_gen_tags_xlib_Sum} 夹具源码 == 转译器当前输出（逐串相等——
 * 转译器演进致夹具漂移即红灯）；再生成载体 = {@code TagFixtureMain}（手动运行写盘）。
 */
public class TestTagFixtureSources {

    private static final String LIB_PATH = "/test/xlang-java-gen/tags.xlib";

    private static final String SUM_KEY = LIB_PATH + "#Sum";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void reset() {
        CoreInitialization.destroy();
    }

    @Test
    public void testSumFixtureMatchesTranslatorOutput() {
        XplTagLib lib = (XplTagLib) ResourceComponentManager.instance().loadComponentModel(LIB_PATH);
        ExecutableFunction fn = (ExecutableFunction) lib.getTag("Sum").getFunctionModel().getInvoker();
        GeneratedJavaSource src = new ExecToJavaTranslator().translateTagUnit(SUM_KEY, fn);
        File fixture = new File("src/test/java",
                src.getClassName().replace('.', '/') + ".java");
        assertTrue(fixture.isFile(), "tag fixture source must be committed: " + fixture.getPath());
        assertEquals(src.getCode(), FileHelper.readText(fixture, null),
                "tag fixture drifted from translator output; rerun TagFixtureMain and commit");
    }
}
