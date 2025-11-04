/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.unittest.BaseTestCase;
import io.nop.core.unittest.MarkdownTestFile;
import io.nop.core.unittest.MarkdownTestSection;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class TestXScript extends BaseTestCase {

    // @Test
    // public void singleTest(){
    // runMarkdownTest(attachmentResource("exprs/for.test.md"),"3. for(var of items)语句",this::runTestBlock);
    // }

    public TestXScript() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
    }

    @BeforeAll
    public static void beforeAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    @ParameterizedTest
    @MethodSource
    public void runTest(MarkdownTestSection block) {
        MarkdownTestFile.runSection(block, this::runTestBlock);
    }

    // 与参数化测试方法同名的静态方法作为参数工程
    static Stream<MarkdownTestSection> runTest() {
        TestXScript xs = new TestXScript();
        return xs.attachmentResources("exprs", true).stream().flatMap(file -> {
            MarkdownTestFile mf = xs.markdownTestFile(file);
            return mf.getSections().stream();
        });
    }

    Object runTestBlock(MarkdownTestSection block) {
        IEvalAction action = XLang.newCompileTool().compileFullExpr(block.getLocation(), block.getSource());
        if (action == null)
            return null;
        return action.invoke(new ServiceContextImpl());
    }
}
