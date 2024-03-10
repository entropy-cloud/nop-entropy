/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import org.junit.Test;

public class TestXLangCompletionContributor extends LightPlatformCodeInsightFixture4TestCase {
    @Test
    public void noEmptyPrefix() {
        // empty file
        myFixture.configureByText("test.xgen", "");
        assertEquals("expected no completions for an empty file",
                0, myFixture.completeBasic().length);

        // whitespace suffix
        myFixture.configureByText("test.xgen", "<foo> a");
        myFixture.type(" ");
        assertEquals("expected no completions in content",
                0, myFixture.completeBasic().length);
    }

    @Test
    public void testCompletion() {
        myFixture.configureByText("test.xgen", "<root>");

        myFixture.type("foo");
        LookupElement[] items = myFixture.completeBasic();
        // fixme: test the completion items
    }
}
