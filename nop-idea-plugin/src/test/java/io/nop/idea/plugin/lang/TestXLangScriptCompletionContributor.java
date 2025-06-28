/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;
import org.junit.Test;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class TestXLangScriptCompletionContributor extends LightPlatformCodeInsightFixture4TestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    @Test
    public void testImportCompletion() {
        myFixture.configureByText("sample." + ext, "import io.nop");
        myFixture.type(".");

        LookupElement[] items = myFixture.completeBasic();
        assertTrue(items.length > 0);
    }
}
