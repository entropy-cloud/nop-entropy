/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.unittest.BaseTestCase;
import io.nop.core.unittest.MarkdownTestFile;
import io.nop.core.unittest.MarkdownTestSection;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXpl extends BaseTestCase {
    @BeforeAll
    public static void beforeAll() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    @ParameterizedTest
    @MethodSource
    public void runTest(MarkdownTestSection block) {
        MarkdownTestFile.runSection(block, this::runTestSection);
    }

    // 与参数化测试方法同名的静态方法作为参数工程
    static Stream<MarkdownTestSection> runTest() {
        TestXpl xs = new TestXpl();
        return xs.attachmentResources("xpls", true).stream().flatMap(file -> {
            MarkdownTestFile mf = xs.markdownTestFile(file);
            return mf.getSections().stream();
        });
    }

    Object runTestSection(MarkdownTestSection section) {
        ExprEvalAction action = XLang.newCompileTool().compileXpl(section.getLocation(), section.getSource());
        XLangOutputMode outputMode = XLangOutputMode.fromText(section.getStringAttribute("outputMode"));
        if (outputMode == null)
            outputMode = XLangOutputMode.none;

        IEvalContext context = new ServiceContextImpl();

        String expected = section.getStringAttribute("output");

        Object result = null;
        Object output = null;
        switch (outputMode) {
            case none: {
                result = action.invoke(context);
                break;
            }
            case text:
            case html:
            case xml: {
                output = action.generateText(context);
                break;
            }
            case node: {
                output = action.generateNode(context);
                break;
            }
            default: {
                result = action.invoke(context);
            }
        }
        if (outputMode != XLangOutputMode.none)
            checkOutput(expected, output);
        return result;
    }

    private void checkOutput(String checkExpected, Object result) {
        if (checkExpected == null || result == null) {
            assertEquals(checkExpected, result);
        } else if (result instanceof String) {
            assertEquals(checkExpected, result);
        } else if (result instanceof XNode) {
            XNode node = XNodeParser.instance().parseFromText(null, checkExpected);
            XNode resultNode = (XNode) result;
            if (!node.isXmlEquals(resultNode)) {
                assertEquals(node.xml(), resultNode.xml());
            }
        } else {
            assertEquals(checkExpected, String.valueOf(result));
        }
    }
}