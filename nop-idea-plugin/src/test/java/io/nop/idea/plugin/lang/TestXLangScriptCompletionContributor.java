/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import java.util.List;

import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class TestXLangScriptCompletionContributor extends BaseXLangPluginTestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    public void testImportCompletion() {
        String[] samples = new String[] {
                //"io.nop.xlang.", //
                "io.nop.xlang.x", //
                "io.nop.xu", //
        };

        for (String sample : samples) {
            myFixture.configureByText("sample." + ext, "import " + sample + "<caret>;");
            myFixture.completeBasic();

            List<String> items = myFixture.getLookupElementStrings();
            assertNotNull(items);
            assertFalse(items.isEmpty());

            items.forEach((item) -> assertTrue(item.startsWith(sample)));

            doTestCompletion("import " + items.get(0) + ";");
        }
    }
}
